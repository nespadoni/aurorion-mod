package com.aurorion.ethereal.ranking;

import net.minecraft.network.chat.Component;

/**
 * Uma linha ja pronta do holograma: o texto e a cor com que ele deve sair.
 *
 * <p>A cor vem calculada do servidor junto com o texto, e nao resolvida no cliente a partir do id da
 * casa, porque o cliente nao tem os datapacks — ele nunca soube que casa tem cor. Isso tambem
 * mantem o renderizador com zero trabalho por frame alem de medir e desenhar (SDD §4.2).
 *
 * @param text  ja montado com numero e unidade, mas ainda como {@link Component}: quem traduz "pts"
 *              e o cliente, no idioma dele.
 * @param color ARGB, ou {@code 0} para a cor padrao da posicao no ranking.
 */
public record BoardLine(Component text, int color) {
}
