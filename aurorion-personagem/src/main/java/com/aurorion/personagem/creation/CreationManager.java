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
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Quem esta sem personagem nao joga, e quem recomeca recomeca do nada.
 *
 * <h2>A troca acontece com a pessoa offline</h2>
 *
 * <p>Apagar a historia anterior e apagar o arquivo do jogador ({@link PlayerFileWipe}), e isso so
 * funciona com a conta fora da lista de jogadores — online, o arquivo no disco e uma copia velha que
 * o logout reescreve. Entao a troca nao acontece no clique: o clique <b>reserva</b> a identidade e
 * desconecta; a varredura apaga e publica; o login seguinte apresenta o personagem novo.
 *
 * <p>Fica mais lento e mais cerimonioso que um reset instantaneo, e essa e a ideia: comecar outra
 * vida custa uma reconexao e uma autorizacao da staff, nao um clique.
 *
 * <h2>A ordem e a regra</h2>
 *
 * <ol>
 *   <li>a identidade nova e <b>reservada</b> e gravada em disco com {@code fsync};</li>
 *   <li>a conta e desconectada;</li>
 *   <li>fora do jogo, o arquivo do jogador e apagado e os mods zeram o que guardam;</li>
 *   <li>so entao a identidade e publicada, e o diario gravado de novo.</li>
 * </ol>
 *
 * <p>Cair em qualquer ponto deixa a reserva no disco e a conta ainda morta — o estado de onde a
 * varredura seguinte retoma, repetindo passos escritos para serem idempotentes. O que nao pode
 * acontecer, e nao acontece, e a pessoa voltar viva com metade das coisas da vida anterior.
 */
public final class CreationManager {
    /** Modo de jogo de antes da espera, para devolver quem so estava sem nome. */
    private static final Map<UUID, GameType> HELD = new HashMap<>();

    /** Trocas que falharam ao apagar. Existe so para nao repetir o mesmo erro no log a cada segundo. */
    private static final Set<UUID> REPORTED = new HashSet<>();

    private CreationManager() {
    }

    // --- Quem e barrado ----------------------------------------------------------------------

    /**
     * A politica do servidor, em um lugar so.
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

    /** @return true se a pessoa ficou retida ou foi desconectada; o login normal nao deve seguir. */
    public static boolean onJoin(ServerPlayer player) {
        CharacterData data = CharacterData.get(player.server);
        UUID account = player.getUUID();

        // Nasceu enquanto estava fora: a identidade ja existe, falta apresenta-la.
        if (data.takeNewborn(account)) welcome(player);

        // Quem esta no meio do epilogo da morte definitiva ainda tem uma cena para assistir. A
        // pergunta do nome espera a proxima conexao, depois da desconexao que fecha aquela historia.
        if (!needsCreation(player) || CharacterGate.deferred(player)) {
            HELD.remove(account);
            return false;
        }

        // Reserva em aberto: a troca so anda com esta conta fora. Entrar aqui e so atrasa-la.
        if (data.pending(account) != null) {
            player.connection.disconnect(wipingMessage(data.pending(account).next().fullName()));
            return true;
        }

        if (data.isDead(account) && !data.isAuthorized(account)) {
            player.connection.disconnect(Component.literal(CreationConfig.NEEDS_STAFF.get()));
            return true;
        }

        hold(player);
        prompt(player);
        return true;
    }

    /**
     * A rede de seguranca do portao, uma vez por segundo.
     *
     * <p>Cobre o que o login sozinho nao cobre: quem foi solto do espectador por outro mod ou por
     * comando de staff, quem passou a dever um personagem <b>durante</b> a sessao, e — a parte que so
     * existe aqui — as trocas esperando para serem apagadas.
     *
     * <p>Percorre a lista de jogadores por indice e so faz consultas de mapa. Nada aqui aloca no caso
     * comum, e nada roda por tick: com 80 pessoas online sao 80 buscas por segundo, nao 1600.
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
        advancePending(server);
    }

    public static void onLogout(ServerPlayer player) {
        HELD.remove(player.getUUID());
    }

    public static void reset() {
        HELD.clear();
        REPORTED.clear();
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
        boolean replacement = CharacterData.get(player.server).isDead(player.getUUID());

        String title = CreationConfig.TITLE.get();
        String intro = replacement ? CreationConfig.REBIRTH.get() : CreationConfig.WELCOME.get();
        String rules = CreationConfig.RULES.get();

        if (player.connection.hasChannel(OpenCreationPayload.TYPE.id())) {
            PacketDistributor.sendToPlayer(player, new OpenCreationPayload(replacement, title, intro, rules));
            return;
        }

        player.sendSystemMessage(Component.literal(title).withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal(intro).withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal(rules).withStyle(ChatFormatting.DARK_GRAY));
        player.sendSystemMessage(Component.literal("Digite: /personagem criar <Nome> <Sobrenome>")
                .withStyle(ChatFormatting.YELLOW));
    }

    // --- Resposta ----------------------------------------------------------------------------

    /** Chamado pelo pacote da tela e pelo comando. Tudo e reconferido aqui. */
    public static void submit(ServerPlayer player, String firstName, String lastName) {
        // O comando e aberto a qualquer um (ver PersonagemCommand): quem ja tem personagem apenas
        // ouve isso de volta, sem passar por nada do resto.
        if (!needsCreation(player)) {
            player.sendSystemMessage(Component.literal("Você já tem um personagem.")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        CharacterData data = CharacterData.get(player.server);
        UUID account = player.getUUID();

        CharacterData.Pending open = data.pending(account);
        if (open != null) {
            player.connection.disconnect(wipingMessage(open.next().fullName()));
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
            release(player, named);
            return;
        }

        if (!data.isAuthorized(account)) {
            refuse(player, "A staff ainda não liberou outra história para esta conta.");
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

        // Daqui para frente a troca e da varredura. A conta precisa sair para o arquivo poder morrer.
        player.connection.disconnect(wipingMessage(name.fullName()));
    }

    // --- A troca, com a conta offline ----------------------------------------------------------

    private static void advancePending(MinecraftServer server) {
        CharacterData data = CharacterData.get(server);
        Map<UUID, CharacterData.Pending> open = data.pendingResets();
        if (open.isEmpty()) return;

        for (UUID account : open.keySet().toArray(new UUID[0])) {
            CharacterData.Pending transaction = data.pending(account);
            if (transaction == null) continue;

            ServerPlayer online = server.getPlayerList().getPlayer(account);
            if (online != null) {
                online.connection.disconnect(wipingMessage(transaction.next().fullName()));
                continue;
            }
            publish(server, data, transaction);
        }
    }

    /**
     * O ponto de nao-retorno. Apaga, e so entao publica.
     *
     * <p>Falha em apagar nao publica nada: a reserva continua no disco, a conta continua morta, e a
     * varredura seguinte tenta de novo. Repetir e seguro — apagar um arquivo que ja nao existe e
     * zerar um dado que ja esta zerado dao o mesmo resultado.
     */
    private static void publish(MinecraftServer server, CharacterData data, CharacterData.Pending transaction) {
        UUID account = transaction.account();

        try {
            PlayerFileWipe.apply(server, account);
            NeoForge.EVENT_BUS.post(new CharacterResetEvent(server, transaction));
        } catch (IOException | RuntimeException | LinkageError failure) {
            if (REPORTED.add(account)) {
                AurorionPersonagem.LOGGER.error(
                        "Nao consegui apagar a historia de {}; a identidade nova segue sem ser publicada "
                                + "e a troca sera repetida.", transaction.accountName(), failure);
            }
            return;
        }
        REPORTED.remove(account);

        CharacterData.Character previous = data.find(account);
        CharacterData.Character next = data.finishReplacement(account, transaction.next().id());

        if (!journal(server)) {
            if (previous != null) data.restorePending(transaction, previous);
            return;
        }
        AurorionPersonagem.LOGGER.info("{} recomeca como {} ({}).",
                transaction.accountName(), next.fullName(), next.id());
    }

    // --- Apresentacao --------------------------------------------------------------------------

    /** Devolve o jogo a quem so precisava de um nome. Nada foi apagado neste caminho. */
    private static void release(ServerPlayer player, CharacterData.Character character) {
        MinecraftServer server = player.server;
        GameType before = HELD.remove(player.getUUID());

        // Um espectador guardado de antes de um reinicio nao pode virar uma prisao.
        GameType restored = before == null || before == GameType.SPECTATOR
                ? server.getDefaultGameType()
                : before;
        if (player.gameMode.getGameModeForPlayer() != restored) player.setGameMode(restored);

        NeoForge.EVENT_BUS.post(new CharacterNamedEvent(player, character, false));
        accept(player, Component.literal("Você é " + character.fullName() + "."));
        announce(player, character);
    }

    /**
     * O primeiro login do personagem novo.
     *
     * <p>A identidade foi publicada com a pessoa offline, entao nada dela foi mostrado ainda: o nome
     * exibido, a saudacao e o que os outros mods fazem no nascimento acontecem tudo aqui.
     */
    private static void welcome(ServerPlayer player) {
        CharacterData.Character character = CharacterData.get(player.server).find(player.getUUID());
        if (character == null || !character.named()) return;

        NeoForge.EVENT_BUS.post(new CharacterNamedEvent(player, character, true));
        player.sendSystemMessage(Component.literal("Você é " + character.fullName() + ".")
                .withStyle(ChatFormatting.GREEN));
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

    private static Component wipingMessage(String fullName) {
        return Component.literal(format(CreationConfig.WIPING.get(), fullName));
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
