package com.aurorion.core.integration;

import com.aurorion.core.AurorionCore;
import com.aurorion.core.character.CharacterData;
import com.aurorion.core.house.HouseGate;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Fatos do jogo para fora do servidor: o site e o bot ficam sabendo de uma morte, de uma queda no
 * Limbo ou de um portal aberto <b>no momento em que acontece</b>, sem ninguem consultar o servidor.
 *
 * <p>Mesmo desenho do {@link HouseGate}: o core guarda o <b>ponto de publicacao</b>, os mods de
 * conteudo publicam, e quem entrega (o {@code aurorion-integracao}) se registra com {@link #provide}.
 * Limbo, portais e casas publicam sem saber que a integracao existe, e a integracao nao importa
 * nenhum deles — a regra de independencia da SDD §3 continua valendo.
 *
 * <p><b>E se ninguem chamar {@link #provide}?</b> {@link #publish} vira no-op: sem a integracao no
 * pack, nenhum fato e montado alem da checagem de um campo, e nada muda no jogo. E e o comportamento
 * certo — "sem site para avisar" nao e erro.
 *
 * <h2>Custo</h2>
 *
 * <p>Publicar acontece na thread do servidor, uma vez por acontecimento (mortes, quedas, janelas de
 * portal: dezenas por dia, nao por tick). O {@link Sink} registrado tem que ser nao-bloqueante: so
 * enfileira. Rede, disco e reenvio moram na thread da integracao. Quem publica deve consultar
 * {@link #installed()} antes de montar o payload, para nao pagar nem a montagem quando nao ha destino.
 */
public final class GameFacts {

    // Tipos aceitos pelo contrato v1 do backend (internal/timeline/facts.go).
    public static final String PLAYER_DEATH = "player.death.confirmed";
    public static final String LIMBO_ENTERED = "limbo.entered";
    public static final String LIMBO_EXITED = "limbo.exited";
    public static final String FINAL_DEATH = "character.final_death";
    public static final String PORTAL_STATE = "portal.state.changed";
    public static final String HOUSE_CHANGED = "house.assignment.changed";

    /**
     * Um acontecimento, ja pronto para sair do servidor. Imutavel: e montado na thread do servidor
     * e lido na thread da integracao. O payload nao pode ser alterado depois de publicado.
     *
     * @param eventId    identidade estavel do fato; o backend deduplica por ela. Numa morte, o
     *                   {@code DeathId}, o mesmo que historico e espolio usam.
     * @param profile    o perfil Minecraft em uso (o alt tem UUID proprio). Nunca a conta real.
     * @param character  o personagem, quando ha um.
     */
    public record Fact(String eventId, String type, Instant occurredAt,
                       @Nullable UUID profile, @Nullable UUID character,
                       @Nullable String sourceKind, @Nullable String sourceId,
                       JsonObject payload) {
    }

    /** Quem entrega os fatos. Tem que devolver na hora: so enfileira. */
    @FunctionalInterface
    public interface Sink {
        void accept(Fact fact);
    }

    /** Quem e a pessoa por tras de um fato, nos termos que podem ser publicados. */
    public record Subject(UUID profile, @Nullable UUID character, String characterName, String house) {
        /** Copia nome do personagem e casa para o payload. */
        public void writeTo(JsonObject payload) {
            if (!characterName.isEmpty()) payload.addProperty("character_name", characterName);
            if (!house.isEmpty()) payload.addProperty("house", house);
        }
    }

    @Nullable
    private static volatile Sink sink;

    private GameFacts() {
    }

    /** Chamado uma vez pelo mod de integracao, na construcao dele. */
    public static void provide(Sink value) {
        sink = value;
    }

    /** Ha quem entregue? Consulte antes de montar o payload. */
    public static boolean installed() {
        return sink != null;
    }

    /** Publica o fato. Nunca lanca: avisar o site nao pode interromper a mecanica que o gerou. */
    public static void publish(Fact fact) {
        Sink current = sink;
        if (current == null) return;
        try {
            current.accept(fact);
        } catch (RuntimeException e) {
            AurorionCore.LOGGER.warn("Fato {} ({}) nao foi entregue a integracao", fact.type(), fact.eventId(), e);
        }
    }

    /** Atalho para fatos sem fonte propria: id aleatorio, agora. */
    public static void publish(String type, @Nullable Subject subject, JsonObject payload) {
        if (subject != null) subject.writeTo(payload);
        publish(new Fact(UUID.randomUUID().toString(), type, Instant.now(),
                subject == null ? null : subject.profile(),
                subject == null ? null : subject.character(),
                null, null, payload));
    }

    /**
     * O personagem e a casa de um jogador, como devem aparecer em publico.
     *
     * <p>Sem personagem nomeado, o nome fica vazio e o site mostra "um viajante": o nick da conta
     * nunca sai daqui, porque revelaria quem esta por tras de um alt. {@code find}, e nao
     * {@code current}: publicar um fato nao pode criar identidade como efeito colateral.
     */
    public static Subject subject(MinecraftServer server, UUID player) {
        CharacterData.Character character = CharacterData.get(server).find(player);
        String name = character != null && character.named() ? character.fullName() : "";
        ResourceLocation house = HouseGate.of(server, player);
        String houseName = house == null ? "" : HouseGate.nameOf(house).getString();
        return new Subject(player, character == null ? null : character.id(), name, houseName);
    }
}
