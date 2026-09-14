package com.aurorion.limbo.narrate;

import com.aurorion.core.text.TimeFormat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * As falas do Limbo, num lugar so.
 *
 * <p>Existe para os dois narradores dizerem exatamente a mesma coisa: se cada um montasse o proprio
 * texto, ligar o Immersive Messages mudaria a redacao do servidor sem ninguem pedir, e uma correcao
 * de fala precisaria ser feita duas vezes.
 *
 * <p>Tudo sai como {@link Component#translatable}, nunca como texto montado aqui — mesma razao do
 * {@code TimeFormat} do core: o servidor resolve a mensagem uma vez e manda para todo mundo, entao
 * texto cru chegaria em portugues no cliente de quem joga em ingles.
 */
public final class LimboText {
    /** Ambar: a cor de uma vida no Aurorion, contra o vermelho da vida do vanilla. */
    public static final int AMBER = 0xE8A33D;
    /** Azul frio: o Limbo. */
    public static final int COLD = 0x82B0CD;
    /** Ferrugem: perda definitiva. */
    public static final int RUST = 0xD9614A;

    private LimboText() {
    }

    public static MutableComponent fall() {
        return Component.translatable("aurorion_limbo.queda");
    }

    public static MutableComponent fallPublic() {
        return Component.translatable("aurorion_limbo.queda.publico");
    }

    public static MutableComponent fallPublicNamed(String name) {
        return Component.translatable("aurorion_limbo.queda.publico.nome", name);
    }

    public static MutableComponent arrival(long remainingMillis) {
        return Component.translatable("aurorion_limbo.chegada", TimeFormat.duration(remainingMillis));
    }

    public static MutableComponent deadline(long remainingMillis) {
        return Component.translatable("aurorion_limbo.prazo", TimeFormat.duration(remainingMillis));
    }

    public static MutableComponent leash() {
        return Component.translatable("aurorion_limbo.coleira");
    }

    public static MutableComponent doorWindowOpen() {
        return Component.translatable("aurorion_limbo.porta.janela");
    }

    public static MutableComponent doorAppeared(BlockPos pos) {
        return Component.translatable("aurorion_limbo.porta.apareceu", pos.getX(), pos.getY(), pos.getZ());
    }

    public static MutableComponent escaped(int timesForgotten) {
        // A partir da segunda vez o texto muda: a primeira e azar, a repeticao e um padrao — e o
        // jogador merece perceber que o servidor esta percebendo.
        return timesForgotten <= 1
                ? Component.translatable("aurorion_limbo.porta.saiu")
                : Component.translatable("aurorion_limbo.porta.saiu.repetido", timesForgotten);
    }

    public static MutableComponent rescued() {
        return Component.translatable("aurorion_limbo.resgatado");
    }

    // --- Resgate -------------------------------------------------------------------------------

    public static MutableComponent passageOpened(String name, int cost) {
        return Component.translatable("aurorion_limbo.passagem.aberta", name, cost);
    }

    public static MutableComponent passageCrossed() {
        return Component.translatable("aurorion_limbo.passagem.atravessou");
    }

    public static MutableComponent bondUsed(String name) {
        return Component.translatable("aurorion_limbo.vinculo.usado", name);
    }

    public static MutableComponent bondSelf() {
        return Component.translatable("aurorion_limbo.vinculo.proprio");
    }

    public static MutableComponent bondNotExiled() {
        return Component.translatable("aurorion_limbo.vinculo.nao_exilado");
    }

    public static MutableComponent oracleTooFar() {
        return Component.translatable("aurorion_limbo.oraculo.longe");
    }

    /**
     * Por que a passagem nao abriu, em uma frase.
     *
     * <p>Cada recusa tem texto proprio em vez de um "nao deu" generico: quem acabou de decidir gastar
     * uma vida merece saber se o problema foi a vida que falta, a pessoa que ja saiu do Limbo, ou o
     * chao onde ele esta parado.
     */
    public static MutableComponent refusal(com.aurorion.limbo.rescue.RescueManager.Refusal refusal) {
        return Component.translatable(switch (refusal) {
            case NOT_EXILED -> "aurorion_limbo.passagem.recusa.nao_exilado";
            case SELF -> "aurorion_limbo.passagem.recusa.proprio";
            case RESCUER_EXILED -> "aurorion_limbo.passagem.recusa.voce_exilado";
            case NOT_ENOUGH_LIVES -> "aurorion_limbo.passagem.recusa.sem_vidas";
            case NO_ROOM -> "aurorion_limbo.passagem.recusa.sem_espaco";
            case OK -> "aurorion_limbo.passagem.aberta";
        });
    }

    public static MutableComponent expired() {
        return Component.translatable("aurorion_limbo.vencido");
    }
}
