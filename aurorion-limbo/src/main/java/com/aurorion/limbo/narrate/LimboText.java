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

    public static MutableComponent expired() {
        return Component.translatable("aurorion_limbo.vencido");
    }
}
