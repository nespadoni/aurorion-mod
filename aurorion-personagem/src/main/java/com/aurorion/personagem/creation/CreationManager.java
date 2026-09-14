package com.aurorion.personagem.creation;

import com.aurorion.core.character.CharacterData;
import com.aurorion.core.character.CharacterGate;
import com.aurorion.core.character.CharacterName;
import com.aurorion.core.character.CharacterNamedEvent;
import com.aurorion.core.character.CharacterResetEvent;
import com.aurorion.core.data.SavedDataAccess;
import com.aurorion.personagem.AurorionPersonagem;
import com.aurorion.personagem.config.CreationConfig;
import com.aurorion.personagem.network.CreationFeedbackPayload;
import com.aurorion.personagem.network.OpenCreationPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Quem esta sem personagem nao joga, e quem responde comeca do zero.
 *
 * <h2>A ordem e a regra</h2>
 *
 * <p>Trocar de personagem tem um ponto de nao-retorno: entre "apagar a historia antiga" e "publicar
 * a identidade nova" existe um instante em que uma queda de energia deixaria a conta sem nenhuma das
 * duas — sem inventario e sem personagem. Por isso a troca e uma <b>transacao com diario</b>:
 *
 * <ol>
 *   <li>a identidade nova e <b>reservada</b> e gravada em disco antes de qualquer coisa ser apagada;</li>
 *   <li>o reset roda (vanilla aqui, mods no {@link CharacterResetEvent});</li>
 *   <li>so entao a identidade e publicada, e o diario gravado de novo.</li>
 * </ol>
 *
 * <p>Cair no meio deixa a reserva no disco e a conta ainda morta — que e exatamente o estado de onde
 * o proximo login retoma, repetindo um reset que foi escrito para ser idempotente. O que nao pode
 * acontecer, e nao acontece, e a pessoa voltar viva com metade das coisas da vida anterior.
 *
 * <h2>Por que o gravado forcado</h2>
 *
 * <p>{@code SavedData} normal so chega ao disco no autosave. Um diario que talvez esteja gravado nao
 * e um diario. Nos dois pontos da transacao o dado e forcado para o disco com fsync; se isso falhar,
 * a operacao e desfeita e a pessoa recebe uma recusa — em vez de um "deu certo" que o disco nunca viu.
 */
public final class CreationManager {
    /** Modo de jogo de antes da espera, para devolver quem so estava sem nome. */
    private static final Map<UUID, GameType> HELD = new HashMap<>();

    private CreationManager() {
    }

    // --- Quem e barrado ----------------------------------------------------------------------

    /**
     * A politica do servidor, em um lugar so. O {@code CharacterGate} do core pergunta isto.
     *
     * <p>Morte definitiva e reserva pendente barram sempre. Falta de nome so barra se a config
     * mandar — e um servidor que ja estava rodando pode nomear a populacao existente aos poucos, sem
     * travar ninguem no login.
     */
    public static boolean needsCreation(ServerPlayer player) {
        CharacterData data = CharacterData.get(player.server);
        UUID account = player.getUUID();

        if (!data.needsName(account)) return false;
        return data.isDead(account) || data.pending(account) != null || CreationConfig.ASK_EXISTING.get();
    }

    // --- Entrada -----------------------------------------------------------------------------

    /** @return true se a pessoa ficou retida; o chamador nao deve seguir com o login normal. */
    public static boolean onJoin(ServerPlayer player) {
        // Quem esta no meio do epilogo da morte definitiva ainda tem uma cena para assistir. A
        // pergunta do nome espera a proxima conexao, depois da desconexao que fecha aquela historia.
        if (!needsCreation(player) || CharacterGate.deferred(player)) {
            HELD.remove(player.getUUID());
            return false;
        }
        hold(player);
        prompt(player);
        return true;
    }

    /**
     * A rede de seguranca do portao, uma vez por segundo.
     *
     * <p>Cobre duas coisas que o login sozinho nao cobre: quem foi solto do espectador por outro mod
     * ou por comando de staff, e quem passou a dever um personagem <b>durante</b> a sessao — o caso
     * real e o epilogo da morte definitiva terminando com a pessoa ainda conectada.
     *
     * <p>Percorre a lista de jogadores por indice e so faz consultas de mapa. Nada aqui aloca, e nada
     * roda por tick: com 80 pessoas online sao 80 buscas por segundo, nao 1600.
     */
    public static void sweep(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();

        for (int i = 0; i < players.size(); i++) {
            ServerPlayer player = players.get(i);

            if (HELD.containsKey(player.getUUID())) {
                if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) hold(player);
            } else {
                onJoin(player);
            }
        }

        if (!HELD.isEmpty()) {
            HELD.keySet().removeIf(account -> server.getPlayerList().getPlayer(account) == null);
        }
    }

    public static void onLogout(ServerPlayer player) {
        HELD.remove(player.getUUID());
    }

    public static void reset() {
        HELD.clear();
    }

    /**
     * Espectador enquanto espera.
     *
     * <p>Nao e estetica: e o unico estado do vanilla em que a pessoa nao sofre dano, nao empurra
     * ninguem, nao pega item e nao interage com bloco nenhum. A tela e modal, mas um cliente sem o
     * mod — ou com a tela fechada na forca — continua parado por causa disto, e nao por causa dela.
     */
    public static void hold(ServerPlayer player) {
        GameType before = player.gameMode.getGameModeForPlayer();
        HELD.putIfAbsent(player.getUUID(), before);

        player.stopRiding();
        if (player.containerMenu != player.inventoryMenu) player.closeContainer();
        if (before != GameType.SPECTATOR) player.setGameMode(GameType.SPECTATOR);
        player.setDeltaMovement(Vec3.ZERO);
        player.setCamera(player);
    }

    public static boolean isHeld(ServerPlayer player) {
        return HELD.containsKey(player.getUUID());
    }

    /** Reabre a pergunta. O cliente com o mod desenha a tela; o cliente sem o mod le no chat. */
    public static void prompt(ServerPlayer player) {
        CharacterData data = CharacterData.get(player.server);
        CharacterData.Pending pending = data.pending(player.getUUID());
        boolean replacement = pending != null || data.isDead(player.getUUID());

        String title = CreationConfig.TITLE.get();
        String intro = replacement ? CreationConfig.REBIRTH.get() : CreationConfig.WELCOME.get();
        String rules = CreationConfig.RULES.get();
        String reserved = pending == null ? "" : pending.next().fullName();

        if (player.connection.hasChannel(OpenCreationPayload.TYPE.id())) {
            PacketDistributor.sendToPlayer(player, new OpenCreationPayload(replacement, title, intro, rules, reserved));
            return;
        }

        player.sendSystemMessage(Component.literal(title).withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal(intro).withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal(rules).withStyle(ChatFormatting.DARK_GRAY));
        player.sendSystemMessage(reserved.isEmpty()
                ? Component.literal("Digite: /personagem criar <Nome> <Sobrenome>").withStyle(ChatFormatting.YELLOW)
                : Component.literal("Uma criação ficou pela metade. Digite /personagem continuar para terminar como "
                        + reserved + ".").withStyle(ChatFormatting.YELLOW));
    }

    // --- Resposta ----------------------------------------------------------------------------

    /** Chamado pelo pacote da tela e pelo comando. Tudo e reconferido aqui. */
    public static void submit(ServerPlayer player, String firstName, String lastName) {
        if (!needsCreation(player)) {
            accept(player, Component.literal("Você já tem um personagem."));
            return;
        }

        CharacterData data = CharacterData.get(player.server);
        UUID account = player.getUUID();

        // Uma reserva pendente ja tem nome escrito no diario. Aceitar outro nome agora deixaria o
        // primeiro reservado para sempre, sem dono — entao a reserva manda, e o que foi digitado
        // e ignorado de proposito.
        CharacterData.Pending pending = data.pending(account);
        if (pending != null) {
            activate(player, pending);
            return;
        }

        CharacterName name;
        try {
            name = new CharacterName(firstName, lastName);
        } catch (IllegalArgumentException invalid) {
            refuse(player, invalid.getMessage());
            return;
        }

        if (!data.nameAvailable(name)) {
            refuse(player, "Esse nome já pertence a outro personagem — inclusive a um que morreu.");
            return;
        }

        if (!data.isDead(account)) {
            // Primeiro nome de um personagem vivo: nada foi vivido por outra pessoa, nada a apagar.
            CharacterData.Character named = data.nameLiving(account, name);
            if (!journal(player.server)) {
                refuse(player, "O servidor não conseguiu gravar seu nome. Tente de novo.");
                return;
            }
            release(player, named, false);
            return;
        }

        CharacterData.Pending transaction;
        try {
            transaction = data.beginReplacement(account, player.getGameProfile().getName(), name);
        } catch (IllegalStateException refused) {
            refuse(player, "Não foi possível abrir a criação agora. Chame a staff.");
            AurorionPersonagem.LOGGER.warn("Reserva recusada para {}", player.getGameProfile().getName(), refused);
            return;
        }

        if (!journal(player.server)) {
            data.abandonReplacement(account);
            refuse(player, "O servidor não conseguiu gravar a reserva do nome. Tente de novo.");
            return;
        }
        activate(player, transaction);
    }

    /**
     * O ponto de nao-retorno: apaga a historia anterior e publica a identidade reservada.
     *
     * <p>Falha do reset nao publica nada. A reserva continua no disco e a conta continua morta, que
     * e o estado de retomada — a pessoa ve uma recusa e a proxima tentativa repete o reset inteiro.
     */
    private static void activate(ServerPlayer player, CharacterData.Pending transaction) {
        MinecraftServer server = player.server;
        CharacterData data = CharacterData.get(server);
        UUID account = player.getUUID();

        try {
            VanillaReset.apply(player);
            NeoForge.EVENT_BUS.post(new CharacterResetEvent(server, transaction));
        } catch (RuntimeException | LinkageError failure) {
            AurorionPersonagem.LOGGER.error("Reset de {} falhou; a identidade nova nao foi publicada.",
                    player.getGameProfile().getName(), failure);
            refuse(player, "Algo falhou ao apagar a história anterior. Tente de novo ou chame a staff.");
            return;
        }

        CharacterData.Character previous = data.find(account);
        CharacterData.Character next = data.finishReplacement(account, transaction.next().id());

        if (!journal(server)) {
            if (previous != null) data.restorePending(transaction, previous);
            refuse(player, "O servidor não conseguiu gravar o personagem novo. Tente de novo.");
            return;
        }
        release(player, next, true);
    }

    /** Devolve o jogo. Daqui para frente o {@code CharacterGate} nao barra mais esta conta. */
    private static void release(ServerPlayer player, CharacterData.Character character, boolean replacement) {
        MinecraftServer server = player.server;
        GameType before = HELD.remove(player.getUUID());

        if (replacement && CreationConfig.SPAWN_ON_BIRTH.get()) {
            ServerLevel overworld = server.getLevel(Level.OVERWORLD);
            if (overworld != null) {
                BlockPos birthplace = VanillaReset.birthplace(overworld);
                player.teleportTo(overworld, birthplace.getX() + 0.5D, birthplace.getY(), birthplace.getZ() + 0.5D,
                        overworld.getSharedSpawnAngle(), 0.0F);
            }
        }

        // Quem foi retido em espectador volta ao que era. Quem nasceu agora entra no padrao do
        // servidor — e um espectador guardado de antes de um reinicio nao vira uma prisao.
        GameType restored = replacement || before == null || before == GameType.SPECTATOR
                ? server.getDefaultGameType()
                : before;
        if (player.gameMode.getGameModeForPlayer() != restored) player.setGameMode(restored);

        NeoForge.EVENT_BUS.post(new CharacterNamedEvent(player, character, replacement));

        accept(player, Component.literal("Você é " + character.fullName() + "."));
        announce(player, character);
    }

    private static void announce(ServerPlayer player, CharacterData.Character character) {
        for (String line : CreationConfig.FIRST_WORDS.get()) {
            if (line.isBlank()) continue;
            player.sendSystemMessage(Component.literal(format(line, character.fullName()))
                    .withStyle(ChatFormatting.GRAY));
        }

        String greeting = CreationConfig.GREETING.get();
        if (greeting.isBlank()) return;

        Component broadcast = Component.literal(format(greeting, character.fullName()))
                .withStyle(ChatFormatting.GOLD);
        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            if (other != player) other.sendSystemMessage(broadcast);
        }
    }

    /** Um {@code %s} opcional. Texto de config nao pode derrubar o servidor por formato errado. */
    private static String format(String template, String fullName) {
        try {
            return template.formatted(fullName);
        } catch (RuntimeException badFormat) {
            return template;
        }
    }

    // --- Disco -------------------------------------------------------------------------------

    /** @return false quando o diario nao chegou ao disco; nesse caso nada pode ser dado por feito. */
    private static boolean journal(MinecraftServer server) {
        try {
            SavedDataAccess.flushAll(server);
            return true;
        } catch (IOException failure) {
            AurorionPersonagem.LOGGER.error("Nao consegui gravar o diario de personagens.", failure);
            return false;
        }
    }

    // --- Respostas ---------------------------------------------------------------------------

    private static void refuse(ServerPlayer player, @Nullable String reason) {
        feedback(player, false, Component.literal(reason == null ? "Nome recusado." : reason));
    }

    private static void accept(ServerPlayer player, Component message) {
        feedback(player, true, message);
    }

    private static void feedback(ServerPlayer player, boolean accepted, Component message) {
        if (player.connection.hasChannel(CreationFeedbackPayload.TYPE.id())) {
            PacketDistributor.sendToPlayer(player, new CreationFeedbackPayload(accepted, message));
        }
        player.sendSystemMessage(message.copy().withStyle(accepted ? ChatFormatting.GREEN : ChatFormatting.RED));
    }
}
