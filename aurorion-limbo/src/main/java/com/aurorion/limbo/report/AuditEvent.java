package com.aurorion.limbo.report;

import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.UUID;

/**
 * Uma linha da auditoria do Limbo.
 *
 * <p>O formato e um objeto JSON por linha (JSONL) porque o consumidor nao e o jogo: e a staff, e o
 * bot. JSONL le com {@code tail -f}, cresce sem reescrever nada e aguenta um arquivo grande sem
 * precisar carregar o historico inteiro na memoria — coisas que um JSON unico com um array nao faz.
 *
 * <p>Os nomes dos campos sao <b>contrato</b>. O bot do Discord depende deles; renomear um campo aqui
 * quebra o painel de quem estiver lendo do outro lado, entao campo novo se adiciona, campo velho nao
 * se renomeia.
 */
public record AuditEvent(
        Type type,
        UUID player,
        String name,
        int lives,
        long remainingMillis,
        int walkedBlocks,
        int forgottenTotal,
        String detail,
        Instant occurredAt) {

    public AuditEvent(Type type, UUID player, String name, int lives, long remainingMillis,
                      int walkedBlocks, int forgottenTotal, String detail) {
        this(type, player, name, lives, remainingMillis, walkedBlocks, forgottenTotal, detail, Instant.now());
    }

    public enum Type {
        /** Zerou as vidas e caiu no Limbo. */
        QUEDA,
        /** Alguem devolveu vida: saiu resgatado. */
        RESGATE,
        /** Entrou na janela final e a Porta do Esquecido passou a ser possivel. */
        PORTA_ARMADA,
        /** Caminhou o bastante e a Porta apareceu. */
        PORTA_APARECEU,
        /**
         * Saiu sozinho pela Porta.
         *
         * <p><b>E este o evento que justifica a auditoria existir.</b> Sair por aqui significa que o
         * servidor inteiro teve horas para ir buscar e nao foi — e e esse fato, registrado e
         * consultavel, que a staff usa depois para construir o que vier em cima.
         */
        PORTA_ATRAVESSADA,
        /** O prazo venceu sem resgate e sem Porta. */
        PRAZO_VENCIDO,
        TENTATIVA_RESGATE,
        PRAZO_AJUSTADO
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("ts", occurredAt.toString());
        json.addProperty("evento", type.name());
        json.addProperty("uuid", player.toString());
        json.addProperty("nome", name);
        json.addProperty("vidas", lives);
        json.addProperty("prazo_s", remainingMillis / 1000L);
        json.addProperty("andou", walkedBlocks);
        json.addProperty("saidas_esquecido", forgottenTotal);

        if (!detail.isEmpty()) {
            json.addProperty("detalhe", detail);
        }
        return json;
    }

    /** Uma frase para humanos: vai para o log do servidor e para o corpo do webhook. */
    public String toLine() {
        String base = switch (type) {
            case QUEDA -> name + " zerou as vidas e caiu no Limbo.";
            case RESGATE -> name + " foi resgatado do Limbo.";
            case PORTA_ARMADA -> name + " entrou na janela final: a Porta do Esquecido pode aparecer.";
            case PORTA_APARECEU -> name + " caminhou " + walkedBlocks + " blocos e a Porta apareceu.";
            case PORTA_ATRAVESSADA -> name + " saiu sozinho pela Porta do Esquecido — ninguem foi buscar."
                    + (forgottenTotal > 1 ? " Ja e a " + forgottenTotal + "a vez." : "");
            case PRAZO_VENCIDO -> name + " ficou no Limbo ate o prazo vencer.";
            case TENTATIVA_RESGATE -> "Tentativa de resgate registrada para " + name + ".";
            case PRAZO_AJUSTADO -> "Prazo de " + name + " ajustado para " + remainingMillis / 1000L + "s.";
        };
        return detail.isEmpty() ? base : base + " (" + detail + ")";
    }
}
