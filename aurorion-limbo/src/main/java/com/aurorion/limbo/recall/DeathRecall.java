package com.aurorion.limbo.recall;

import com.aurorion.core.character.CharacterData;
import com.aurorion.core.death.DeathClaims;
import com.aurorion.core.death.DeathId;
import com.aurorion.core.level.ProtectedDrops;
import com.aurorion.core.level.SafeSpot;
import com.aurorion.limbo.AurorionLimbo;
import com.aurorion.limbo.config.LimboConfig;
import com.aurorion.limbo.exile.LimboManager;
import com.aurorion.limbo.narrate.LimboText;
import com.aurorion.limbo.registry.LimboItems;
import com.aurorion.limbo.report.AuditEvent;
import com.aurorion.limbo.report.AuditLog;
import com.aurorion.vidas.lives.LivesManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * O espolio de uma morte: marcar o que ela deixou no chao, levar a pessoa de volta ao lugar
 * (Fio da Volta) e chamar de volta o que ainda existir (Relicario).
 *
 * <h2>Chamar de volta, nunca restaurar</h2>
 *
 * <p>O Relicario nao guarda copia do inventario. Ele procura, no mundo, os itens com a marca daquela
 * morte — o que outro jogador ja pegou ficou com ele, e o que a lava levou nao volta. Restaurar uma
 * copia duplicaria tudo que alguem tivesse saqueado, e numa economia de torneira fechada item criado
 * do nada e inflacao (ECONOMIA.md §3). Quando algo volta, o {@link DeathClaims} do core registra —
 * e e isso que avisa o {@code /deathhistory restore} de que restaurar agora duplicaria.
 *
 * <h2>Por personagem, nao por conta</h2>
 *
 * <p>A ultima morte e guardada pelo id do <b>personagem</b> ({@link CharacterData}). Uma conta com
 * varios personagens (a staff, com personagens jogaveis e NPCs) tem uma ultima morte por personagem,
 * e o Fio de um nunca leva ao corpo de outro.
 *
 * <h2>Por que o chamado espera</h2>
 *
 * <p>Quem usa o Relicario quase nunca esta perto de onde morreu, e o chunk da morte esta
 * descarregado. Carregar os blocos e sincrono, mas as <b>entidades</b> chegam por outra fila,
 * assincrona — procurar os itens no mesmo tick do clique acharia o chao vazio. Entao o clique abre um
 * chamado, um ticket temporario mantem a area carregada, e o chamado fecha quando as entidades
 * chegaram (ou o prazo curto venceu). Mesmo problema, e mesma solucao, da rotacao do Oraculo.
 *
 * <h2>Custo</h2>
 *
 * <p>Fora de uma morte ou de um Relicario em uso, o tick custa duas checagens de lista vazia. Os
 * itens que nao caem na morte sao do core ({@code KeptOnDeath}), nao daqui.
 */
public final class DeathRecall {
    /** Marca, nos dados persistentes do drop, de qual morte ele saiu. */
    public static final String KEY_DEATH = AurorionLimbo.MOD_ID + ":morte";

    /** O que o {@link DeathClaims} grava como origem; aparece na tela da staff. */
    private static final String CLAIM_SOURCE = "relicario";

    /** Quanto o chamado espera as entidades da area carregarem antes de fechar com o que tiver. */
    private static final int RECALL_TIMEOUT_TICKS = 20 * 10;

    /**
     * Ticket que segura a area da morte carregada durante o chamado. O tempo de vida e o que
     * garante que nada fica carregado para sempre: se o chamado for esquecido, o ticket expira sozinho.
     */
    private static final TicketType<ChunkPos> RECALL_TICKET = TicketType.create(
            AurorionLimbo.MOD_ID + "_relicario", Comparator.comparingLong(ChunkPos::toLong),
            RECALL_TIMEOUT_TICKS + 20 * 5);

    /**
     * Um chamado do Relicario em andamento.
     *
     * @param character quem pagou. Se a conta trocar de personagem no meio, nada e entregue ao outro.
     * @param paid      falso quando quem usou nao gasta item (criativo): a devolucao nao pode criar um.
     */
    private record Recall(UUID account, UUID character, UUID deathId, ResourceKey<Level> dimension,
                          ChunkPos center, int radius, long deadline, boolean paid) {
    }

    /** Drops de uma morte deste tick, esperando o fim dele para serem marcados. */
    private record Marking(UUID deathId, Collection<ItemEntity> drops) {
    }

    private static final List<Marking> MARKINGS = new ArrayList<>();
    private static final Map<UUID, Recall> RECALLS = new HashMap<>();

    private DeathRecall() {
    }

    // --- A morte ---------------------------------------------------------------------------------

    /**
     * Grava onde o personagem morreu.
     *
     * <p>Mortes <b>dentro do Limbo</b> nao contam. O Fio nao leva para la, e deixar uma morte no Limbo
     * sobrescrever a anterior apagaria justamente o espolio que o exilado vai querer buscar quando
     * for resgatado.
     */
    public static void recordDeath(ServerPlayer player) {
        if (!counts(player)) return;

        DeathData.get(player.server).record(characterOf(player), new DeathData.Death(DeathId.of(player),
                player.level().dimension().location(), player.position(), System.currentTimeMillis()));
    }

    /**
     * Guarda a lista de drops para marcar no fim do tick.
     *
     * <p>Marcar aqui, na hora do evento, perderia o que for acrescentado depois — mods de slot como o
     * Curios poem os drops deles neste mesmo evento, e a ordem entre mods da mesma prioridade nao e
     * garantida. A lista e a mesma que o vanilla vai espalhar pelo chao, entao guardar a referencia e
     * marcar no fim do tick pega tudo, em qualquer ordem.
     *
     * <p>O id vem do {@link DeathId}: e o mesmo que o {@link #recordDeath} gravou neste tick, e o mesmo
     * que o {@code /deathhistory} mostra.
     */
    public static void onDrops(ServerPlayer player, Collection<ItemEntity> drops) {
        if (!counts(player) || drops.isEmpty()) return;
        MARKINGS.add(new Marking(DeathId.of(player), drops));
    }

    private static void markPending() {
        int ticks = LimboConfig.DROP_PROTECTION_MINUTES.get() * 60 * 20;

        for (Marking marking : MARKINGS) {
            for (ItemEntity item : marking.drops()) {
                // Ja recolhido ou fundido com outro drop no mesmo tick. Se um mod de tumulo cancelou
                // o evento, os itens nunca nasceram: marca-los nao custa nada e nao muda nada.
                if (item.isRemoved()) continue;
                item.getPersistentData().putUUID(KEY_DEATH, marking.deathId());
                ProtectedDrops.protect(item, ticks);
            }
        }
        MARKINGS.clear();
    }

    private static boolean counts(ServerPlayer player) {
        return !(player instanceof FakePlayer) && player.level().dimension() != LimboManager.dimension();
    }

    private static UUID characterOf(ServerPlayer player) {
        return CharacterData.get(player.server).current(player.getUUID()).id();
    }

    /** O personagem aposentado leva a ultima morte junto. */
    public static void clear(MinecraftServer server, UUID character) {
        DeathData.get(server).clear(character);
    }

    // --- Fio da Volta ----------------------------------------------------------------------------

    /** Conferencia barata no inicio do uso, so para nao deixar alguem canalizar para nada. */
    public static boolean canReturn(ServerPlayer player) {
        return target(player) != null;
    }

    /**
     * Leva a pessoa ao lugar exato da ultima morte.
     *
     * <p>"Exato" ate o ponto em que ele mata de novo: quem morreu caindo, afogado ou na lava nao pode
     * ser devolvido ao mesmo ar, agua ou lava. Nesse caso o destino e o chao seguro mais proximo — e
     * se nao houver nenhum, o Fio recusa e <b>nao e gasto</b>.
     *
     * @return {@code true} se a viagem aconteceu e o item deve ser consumido.
     */
    public static boolean returnTo(ServerPlayer player) {
        Target target = target(player);
        if (target == null) return false;

        Vec3 landing = landing(target.level(), target.death().position());
        if (landing == null) {
            refuse(player, "aurorion_limbo.fio.sem_chao");
            return false;
        }

        ServerLevel from = player.serverLevel();
        Vec3 departure = player.position();
        player.stopRiding();

        if (target.level() == from) {
            player.teleportTo(landing.x, landing.y, landing.z);
        } else {
            // changeDimension devolve null quando alguem cancela a viagem — o aurorion_portais tranca
            // dimensoes fora do horario. O Fio nao fura tranca: recusa e continua no bolso.
            Entity moved = player.changeDimension(new DimensionTransition(target.level(), landing, Vec3.ZERO,
                    player.getYRot(), player.getXRot(), DimensionTransition.DO_NOTHING));
            if (moved == null) {
                refuse(player, "aurorion_limbo.fio.barrado");
                return false;
            }
        }
        player.resetFallDistance();

        from.sendParticles(ParticleTypes.REVERSE_PORTAL, departure.x, departure.y + 1.0D, departure.z,
                40, 0.4D, 0.8D, 0.4D, 0.05D);
        from.playSound(null, departure.x, departure.y, departure.z, SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.8F, 0.7F);
        target.level().playSound(null, landing.x, landing.y, landing.z, SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.8F, 0.7F);

        player.sendSystemMessage(Component.translatable("aurorion_limbo.fio.usado")
                .withStyle(style -> style.withColor(LimboText.COLD)));
        audit(player, AuditEvent.Type.FIO_USADO, target.death().id(), String.format("%s %.1f %.1f %.1f",
                target.death().dimension(), landing.x, landing.y, landing.z));
        return true;
    }

    @Nullable
    private static Vec3 landing(ServerLevel level, Vec3 exact) {
        BlockPos feet = BlockPos.containing(exact);
        ChunkAccess chunk = level.getChunk(feet.getX() >> 4, feet.getZ() >> 4);

        if (level.getWorldBorder().isWithinBounds(feet)
                && feet.getY() > level.getMinBuildHeight()
                && feet.getY() < level.getMaxBuildHeight() - 1
                && SafeSpot.fits(level, chunk, feet.mutable())) {
            return exact;
        }

        BlockPos near = SafeSpot.nearestVertical(level, feet, 16);
        if (near == null) near = SafeSpot.aroundColumn(level, feet, 8, feet.getY() + 16);
        return near == null ? null : near.getBottomCenter();
    }

    // --- Relicario -------------------------------------------------------------------------------

    /**
     * Abre o chamado. O item ja pode ser consumido: se nada voltar, {@link #finish} o devolve.
     *
     * @param paid se quem usou realmente gasta o item.
     */
    public static boolean recall(ServerPlayer player, boolean paid) {
        if (RECALLS.containsKey(player.getUUID())) {
            refuse(player, "aurorion_limbo.relicario.ocupado");
            return false;
        }

        Target target = target(player);
        if (target == null) return false;

        ServerLevel level = target.level();
        ChunkPos center = new ChunkPos(BlockPos.containing(target.death().position()));
        int radius = LimboConfig.RELIC_CHUNK_RADIUS.get();

        // Um anel a mais que a busca: a borda da area procurada precisa estar inteira, nao na franja.
        level.getChunkSource().addRegionTicket(RECALL_TICKET, center, radius + 1, center);
        RECALLS.put(player.getUUID(), new Recall(player.getUUID(), target.character(), target.death().id(),
                level.dimension(), center, radius, level.getGameTime() + RECALL_TIMEOUT_TICKS, paid));

        player.displayClientMessage(Component.translatable("aurorion_limbo.relicario.chamando")
                .withStyle(style -> style.withColor(LimboText.AMBER)), true);
        return true;
    }

    /** Chamado a cada tick, no fim dele. Sem morte nem Relicario em uso, sao duas listas vazias. */
    public static void tick(MinecraftServer server) {
        if (!MARKINGS.isEmpty()) markPending();
        if (RECALLS.isEmpty()) return;

        for (Iterator<Recall> it = RECALLS.values().iterator(); it.hasNext(); ) {
            Recall recall = it.next();
            ServerLevel level = server.getLevel(recall.dimension());
            boolean ready = level != null && entitiesLoaded(level, recall);
            if (level != null && !ready && level.getGameTime() < recall.deadline()) continue;

            it.remove();
            finish(server, level, recall);
        }
    }

    private static boolean entitiesLoaded(ServerLevel level, Recall recall) {
        ChunkPos center = recall.center();
        int r = recall.radius();
        for (int x = center.x - r; x <= center.x + r; x++) {
            for (int z = center.z - r; z <= center.z + r; z++) {
                if (!level.areEntitiesLoaded(ChunkPos.asLong(x, z))) return false;
            }
        }
        return true;
    }

    private static void finish(MinecraftServer server, @Nullable ServerLevel level, Recall recall) {
        ServerPlayer player = server.getPlayerList().getPlayer(recall.account());
        if (player == null || !characterOf(player).equals(recall.character())) {
            // Deslogou (ou trocou de personagem) nos segundos do chamado. Os itens continuam no chao,
            // marcados; um Relicario novo ainda os encontra. O gasto deste fica no log para a staff.
            AurorionLimbo.LOGGER.warn("Relicario da conta {} (personagem {}) fechou sem o dono presente; "
                    + "nada foi movido. morte={}", recall.account(), recall.character(), recall.deathId());
            return;
        }

        List<ItemEntity> found = level == null ? List.of()
                : level.getEntitiesOfClass(ItemEntity.class, area(level, recall),
                        item -> isFrom(item, recall.deathId()));

        int stacks = 0;
        for (ItemEntity item : found) {
            if (item.isRemoved() || item.getItem().isEmpty()) continue;

            ItemStack stack = item.getItem().copy();
            item.discard();
            player.getInventory().placeItemBackInInventory(stack);
            stacks++;
        }

        if (stacks == 0) {
            if (recall.paid()) player.getInventory().placeItemBackInInventory(new ItemStack(LimboItems.RELICARIO.get()));
            refuse(player, "aurorion_limbo.relicario.vazio");
            audit(player, AuditEvent.Type.RELICARIO_USADO, recall.deathId(), "nada restou; relicario devolvido");
            return;
        }

        DeathClaims.get(server).claim(recall.deathId(), CLAIM_SOURCE, stacks);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.6F);
        player.sendSystemMessage(Component.translatable("aurorion_limbo.relicario.voltou", stacks)
                .withStyle(style -> style.withColor(LimboText.AMBER)));
        audit(player, AuditEvent.Type.RELICARIO_USADO, recall.deathId(), stacks + " pilha(s) recuperada(s)");
    }

    private static AABB area(ServerLevel level, Recall recall) {
        ChunkPos center = recall.center();
        int r = recall.radius();
        return new AABB(
                (center.x - r) << 4, level.getMinBuildHeight(), (center.z - r) << 4,
                (center.x + r + 1) << 4, level.getMaxBuildHeight(), (center.z + r + 1) << 4);
    }

    private static boolean isFrom(ItemEntity item, UUID deathId) {
        CompoundTag data = item.getPersistentData();
        return data.hasUUID(KEY_DEATH) && data.getUUID(KEY_DEATH).equals(deathId);
    }

    /**
     * Um chamado aberto ja cobrou o item. Fechar o servidor no meio dele devolve o Relicario a quem
     * ainda estiver online — os itens continuam marcados no chao para o proximo.
     */
    public static void abortAll(MinecraftServer server) {
        for (Recall recall : RECALLS.values()) {
            ServerPlayer player = server.getPlayerList().getPlayer(recall.account());
            if (player != null && recall.paid()) {
                player.getInventory().placeItemBackInInventory(new ItemStack(LimboItems.RELICARIO.get()));
            }
        }
        reset();
    }

    public static void reset() {
        RECALLS.clear();
        MARKINGS.clear();
    }

    // --- Comum -----------------------------------------------------------------------------------

    private record Target(UUID character, DeathData.Death death, ServerLevel level) {
    }

    /** A morte usavel do personagem de quem clicou, ou {@code null} com o motivo ja dito ao jogador. */
    @Nullable
    private static Target target(ServerPlayer player) {
        MinecraftServer server = player.server;

        if (player.level().dimension() == LimboManager.dimension()
                || LivesManager.isExiled(server, player.getUUID())) {
            refuse(player, "aurorion_limbo.espolio.no_limbo");
            return null;
        }

        UUID character = characterOf(player);
        DeathData.Death death = DeathData.get(server).last(character);
        if (death == null) {
            refuse(player, "aurorion_limbo.espolio.sem_morte");
            return null;
        }

        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, death.dimension()));
        if (level == null) {
            refuse(player, "aurorion_limbo.espolio.sem_mundo");
            return null;
        }
        return new Target(character, death, level);
    }

    private static void refuse(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key)
                .withStyle(style -> style.withColor(LimboText.RUST)), true);
    }

    /** O id da morte vai no detalhe: e o mesmo do {@code /deathhistory view}, para a staff cruzar. */
    private static void audit(ServerPlayer player, AuditEvent.Type type, UUID deathId, String detail) {
        AuditLog.record(player.server, new AuditEvent(type, player.getUUID(), player.getGameProfile().getName(),
                LivesManager.livesOf(player.server, player.getUUID()), 0L, 0, 0,
                "morte=" + deathId + " " + detail));
    }
}
