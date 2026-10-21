package com.aurorion.talk.server;

import com.aurorion.talk.config.TalkServerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Quem ouve uma fala: so quem esta perto. E o servidor que decide, nao o cliente.
 *
 * <h2>Por que no servidor</h2>
 *
 * <p>Antes, o servidor entregava toda fala a todo mundo e o mod escondia no HUD de quem tinha o
 * mod e a opcao ligada. Esconder nao e nao receber: quem desliga a opcao, ou entra sem o mod (o
 * {@code displayTest} e {@code IGNORE_ALL_VERSION} e o canal e opcional), lia o chat do servidor
 * inteiro — a cena da taverna, a conspiracao no porao, tudo. A unica barreira que vale para cliente
 * modificado e nao mandar o pacote.
 *
 * <h2>Por que um mixin no {@code PlayerList}, e nao o {@code ServerChatEvent}</h2>
 *
 * <p>O evento entrega o texto, nao o {@link PlayerChatMessage}. A fala e <b>assinada</b> pelo
 * cliente de quem falou; para os outros clientes aceitarem, o servidor precisa reenviar a mesma
 * mensagem assinada — remontar uma a partir do texto quebraria a assinatura (fala marcada como
 * nao segura, ou cliente desconectado por cadeia invalida). Entao a fala passa pelo pipeline
 * inteiro do NeoForge e so a <b>entrega</b> e trocada, no ultimo passo
 * ({@code PlayerList#broadcastChatMessage}). Vantagens que caem de graca:
 *
 * <ul>
 *   <li>Evento cancelado por outro mod (silencio da magia, retencao da criacao de personagem,
 *       possessao) nunca chega aqui: o NeoForge para antes do broadcast.</li>
 *   <li>Quem escuta o {@code ServerChatEvent} — uma ponte com o Discord, por exemplo — continua
 *       recebendo toda fala, porque o evento nao e cancelado por nos.</li>
 *   <li>O log do console continua igual ao do vanilla.</li>
 * </ul>
 *
 * <p>Pular destinatarios nao fere a cadeia: cada cliente so exige que o indice de mensagens de um
 * mesmo autor cresca, nao que seja contiguo — e assim que o {@code /msg} ja funciona no vanilla.
 *
 * <h2>Quais falas</h2>
 *
 * <p>{@link ChatType#CHAT} (o chat comum) e {@link ChatType#EMOTE_COMMAND} ({@code /me}): o
 * {@code /me} e uma acao dentro da cena e, se ficasse global, seria o jeito trivial de contornar o
 * alcance. {@code /say} (nivel 2, anuncio da staff), {@code /msg} e {@code /teammsg} ficam como o
 * vanilla manda.
 *
 * <h2>Espiar</h2>
 *
 * <p>A staff (nivel {@link #STAFF_PERMISSION}) pode ligar {@code /aurorion fala espiar} para receber
 * as falas de longe, marcadas com {@code [longe]}. Comeca <b>desligado</b> para todo mundo e so vale
 * enquanto o servidor estiver no ar: ler a cena alheia e ferramenta de moderacao, e quem liga precisa
 * ter decidido ligar naquela sessao — nao herdar de um mes atras. A permissao e conferida a cada
 * fala, entao quem perde o OP para de receber na hora.
 *
 * <p><b>Custo</b>: O(jogadores online) por fala, duas leituras de config, nenhuma alocacao por
 * destinatario (a marca de longe e montada uma vez por fala, so se algum espiao estiver longe).
 */
public final class ProximityChat {
    public static final int STAFF_PERMISSION = 2;

    /** Mesma chave do {@code PlayerList.CHAT_FILTERED_FULL} do vanilla. */
    private static final Component CHAT_FILTERED_FULL = Component.translatable("chat.filtered_full");
    private static final Component FAR_TAG = Component.literal("[longe] ").withStyle(ChatFormatting.DARK_GRAY);

    /**
     * Quem esta espiando. Thread do servidor na pratica (chat e comando rodam nela), mas o conjunto
     * concorrente custa nada e tira a duvida. Cresce no maximo ate o tamanho da staff.
     */
    private static final Set<UUID> SPIES = ConcurrentHashMap.newKeySet();

    private ProximityChat() {
    }

    /**
     * Esta fala e nossa? Chamado antes de qualquer alocacao no mixin: o que nao for chat/emote de
     * jogador, ou com a proximidade desligada, segue o caminho do vanilla intocado.
     */
    public static boolean handles(@Nullable ServerPlayer sender, ChatType.Bound bound) {
        if (sender == null) return false;
        if (!TalkServerConfig.PROXIMITY_ENABLED.get()) return false;

        Holder<ChatType> type = bound.chatType();
        return type.is(ChatType.CHAT) || type.is(ChatType.EMOTE_COMMAND);
    }

    /**
     * Substitui o laco de {@code PlayerList#broadcastChatMessage}: mesmo log, mesma mensagem assinada,
     * mesmo filtro de texto — so que para quem esta perto.
     *
     * @param shouldFilterMessageTo o mesmo predicado que o vanilla usaria (filtro de palavrao do
     *                              Realms/servidor), repassado sem mudar
     */
    public static void deliver(PlayerChatMessage message, Predicate<ServerPlayer> shouldFilterMessageTo,
                               ServerPlayer sender, ChatType.Bound bound) {
        MinecraftServer server = sender.server;

        // Mesmo criterio do PlayerList#verifyChatTrusted, reescrito para nao depender de um metodo
        // que pode ser privado: o log do console tem de continuar dizendo quando a fala nao e segura.
        boolean trusted = message.hasSignature() && !message.hasExpiredServer(Instant.now());
        server.logChatMessage(message.decoratedContent(), bound, trusted ? null : "Not Secure");

        OutgoingChatMessage outgoing = OutgoingChatMessage.create(message);
        double radius = TalkServerConfig.PROXIMITY_RADIUS.get();
        double radiusSqr = radius * radius;
        boolean anySpy = !SPIES.isEmpty();

        ChatType.Bound farBound = null;
        boolean fullyFilteredSomewhere = false;

        for (ServerPlayer recipient : server.getPlayerList().getPlayers()) {
            ChatType.Bound used;
            if (hears(sender, recipient, radiusSqr)) {
                used = bound;
            } else if (anySpy && spyReceives(recipient)) {
                if (farBound == null) farBound = farBound(bound);
                used = farBound;
            } else {
                continue;
            }

            boolean filtered = shouldFilterMessageTo.test(recipient);
            recipient.sendChatMessage(outgoing, filtered, used);
            fullyFilteredSomewhere |= filtered && message.isFullyFiltered();
        }

        if (fullyFilteredSomewhere) sender.sendSystemMessage(CHAT_FILTERED_FULL);
    }

    /** Quem fala sempre se ouve; os demais, na mesma dimensao e dentro do raio. */
    private static boolean hears(ServerPlayer sender, ServerPlayer recipient, double radiusSqr) {
        if (recipient == sender) return true;
        return recipient.level() == sender.level() && recipient.distanceToSqr(sender) <= radiusSqr;
    }

    private static boolean spyReceives(ServerPlayer recipient) {
        return SPIES.contains(recipient.getUUID()) && recipient.hasPermissions(STAFF_PERMISSION);
    }

    /**
     * O nome do autor com {@code [longe]} na frente, so para o espiao. O {@link ChatType.Bound} nao
     * entra na assinatura (e decoracao do servidor), entao trocar o nome aqui nao invalida nada.
     */
    private static ChatType.Bound farBound(ChatType.Bound bound) {
        Component name = Component.empty().append(FAR_TAG).append(bound.name());
        return new ChatType.Bound(bound.chatType(), name, bound.targetName());
    }

    /** @return true se mudou. */
    public static boolean setSpying(UUID staff, boolean spying) {
        return spying ? SPIES.add(staff) : SPIES.remove(staff);
    }

    public static boolean isSpying(UUID staff) {
        return SPIES.contains(staff);
    }

    public static void clear() {
        SPIES.clear();
    }
}
