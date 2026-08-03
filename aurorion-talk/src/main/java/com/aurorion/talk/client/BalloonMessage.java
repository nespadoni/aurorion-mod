package com.aurorion.talk.client;

import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Uma fala ja preparada para desenhar.
 *
 * <p>As linhas e a largura sao calculadas <em>uma vez</em>, quando a mensagem chega. O caminho
 * antigo (do Talk Balloons) refazia {@code font.split} a cada frame, o que com dezenas de
 * jogadores falando vira alocacao pesada no render thread.</p>
 *
 * @param lines          texto ja quebrado respeitando a largura maxima
 * @param widestLine     largura, em pixels, da maior linha
 * @param expiresAtTick  {@code level.getGameTime()} em que a mensagem some
 */
public record BalloonMessage(List<FormattedCharSequence> lines, int widestLine, long expiresAtTick) {
    public int lineCount() {
        return lines.size();
    }
}
