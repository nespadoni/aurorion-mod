package com.aurorion.vidas.lives;

import com.aurorion.core.config.DerivedConfig;
import com.aurorion.vidas.AurorionVidas;
import com.aurorion.vidas.config.LivesConfig;
import com.aurorion.vidas.network.SyncLivesPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** As regras de vida e exilio. Tudo roda no servidor; o cliente so recebe o numero para desenhar. */
public final class LivesManager {
    /**
     * A dimensao de exilio chega como texto na config e vira {@link ResourceKey} uma vez so. O
     * {@link DerivedConfig} refaz a conversao sozinho quando o arquivo muda — sem evento de config
     * para alguem esquecer de ligar.
     */
    private static final DerivedConfig<String, ResourceKey<Level>> EXILE_DIMENSION =
            new DerivedConfig<>(LivesConfig.EXILE_DIMENSION::get, LivesManager::parseDimension);

    private LivesManager() {
    }

    public static ResourceKey<Level> exileDimension() {
        return EXILE_DIMENSION.get();
    }

    private static ResourceKey<Level> parseDimension(String raw) {
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) {
            AurorionVidas.LOGGER.error("exileDimension '{}' nao e um id valido; usando o Nether.", raw);
            id = Level.NETHER.location();
        }
        return ResourceKey.create(Registries.DIMENSION, id);
    }

    // --- Consulta ------------------------------------------------------------------------------

    public static int livesOf(MinecraftServer server, UUID player) {
        return LivesData.get(server).livesOf(player);
    }

    public static boolean isExiled(MinecraftServer server, UUID player) {
        return LivesData.get(server).isExiled(player);
    }

    public static int maxLives() {
        return LivesConfig.MAX_LIVES.get();
    }

    // --- Morte ---------------------------------------------------------------------------------

    /**
     * Gasta uma vida. Chamado uma vez por morte de verdade — totem da imortalidade nunca chega aqui,
     * porque o vanilla intercepta antes de a entidade morrer.
     *
     * @return quantas vidas sobraram.
     */
    public static int loseLife(ServerPlayer player) {
        LivesData data = LivesData.get(player.server);
        UUID uuid = player.getUUID();

        int before = data.livesOf(uuid);
        if (before <= 0) {
            // Ja exilado: continua em zero, sem "dever" vidas. O respawn e que o devolve ao exilio.
            return 0;
        }

        int after = data.setLives(uuid, before - 1);
        sync(player, after);

        if (after > 0) {
            player.sendSystemMessage(Component.translatable("aurorion_vidas.perdeu", after, maxLives())
                    .withStyle(ChatFormatting.RED));
            if (LivesConfig.ANNOUNCE_LIFE_LOSS.get()) {
                player.server.getPlayerList().broadcastSystemMessage(
                        Component.translatable("aurorion_vidas.perdeu.anuncio",
                                player.getDisplayName(), after, maxLives()), false);
            }
            return after;
        }

        announceExile(player);
        return 0;
    }

    private static void announceExile(ServerPlayer player) {
        player.sendSystemMessage(Component.translatable("aurorion_vidas.exilado")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));

        if (!LivesConfig.ANNOUNCE_EXILE.get()) return;

        player.server.getPlayerList().broadcastSystemMessage(
                Component.translatable("aurorion_vidas.exilado.anuncio", player.getDisplayName())
                        .withStyle(ChatFormatting.DARK_RED), false);

        for (ServerPlayer online : player.server.getPlayerList().getPlayers()) {
            online.playNotifySound(SoundEvents.WITHER_SPAWN, SoundSource.MASTER, 0.35F, 1.4F);
        }
    }

    // --- Exilio --------------------------------------------------------------------------------

    /**
     * Para onde o exilado renasce.
     *
     * <p>Este e o caminho de ida do exilio, e ele passa por {@code PlayerRespawnPositionEvent} — que
     * <b>nao</b> chama {@code changeDimension}. E por isso que o portao do {@code aurorion_portais}
     * nao precisa saber deste mod nem abrir excecao: a ida simplesmente nao e uma "viagem".
     *
     * @return o destino, ou {@code null} se a dimensao de exilio nao existe neste servidor.
     */
    @Nullable
    public static DimensionTransition exileTransition(ServerPlayer player) {
        ServerLevel level = player.server.getLevel(exileDimension());
        if (level == null) {
            AurorionVidas.LOGGER.error("Dimensao de exilio '{}' nao existe neste servidor; respawn segue o vanilla.",
                    exileDimension().location());
            return null;
        }

        BlockPos pos = ExileSpot.resolve(level, LivesData.get(player.server));
        return new DimensionTransition(level, pos.getBottomCenter(), Vec3.ZERO,
                player.getYRot(), player.getXRot(), DimensionTransition.DO_NOTHING);
    }

    // --- Administracao -------------------------------------------------------------------------

    /** @return o valor gravado depois do ajuste. */
    public static int setLives(MinecraftServer server, UUID player, int value) {
        int result = LivesData.get(server).setLives(player, value);
        syncIfOnline(server, player, result);
        return result;
    }

    public static int addLives(MinecraftServer server, UUID player, int delta) {
        LivesData data = LivesData.get(server);
        int result = data.setLives(player, data.livesOf(player) + delta);
        syncIfOnline(server, player, result);
        return result;
    }

    public static void setExileSpot(ServerLevel level, BlockPos pos) {
        LivesData.get(level.getServer()).setExile(level.dimension(), pos);
    }

    @Nullable
    public static BlockPos exileSpot(MinecraftServer server) {
        return LivesData.get(server).exilePosIn(exileDimension());
    }

    // --- Rede ----------------------------------------------------------------------------------

    /** O jogador so recebe o proprio numero: o payload e O(1), nunca O(jogadores) (SDD §7.3). */
    public static void sync(ServerPlayer player) {
        sync(player, LivesData.get(player.server).livesOf(player.getUUID()));
    }

    private static void sync(ServerPlayer player, int lives) {
        PacketDistributor.sendToPlayer(player, new SyncLivesPayload(lives, maxLives()));
    }

    private static void syncIfOnline(MinecraftServer server, UUID player, int lives) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            sync(online, lives);
        }
    }
}
