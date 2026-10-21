package com.aurorion.areas.rules;

import com.aurorion.areas.region.AreaRegion;
import org.jetbrains.annotations.Nullable;

/**
 * Resposta de uma consulta de protecao: a posicao esta negada, e por quem.
 *
 * <p>O "por quem" existe para as regras de ambiente ({@code fluido}, {@code pistao}): o que importa
 * nelas e <em>atravessar</em> a borda. Lava escorrendo dentro da propria casa nao e invasao; lava de
 * fora entrando e. Comparar a area que protege a origem com a que protege o destino responde isso
 * sem cadastro novo.
 *
 * @param by area que negou, ou {@code null} quando quem negou foi o padrao da dimensao.
 */
public record Protection(boolean denied, @Nullable AreaRegion by) {
    public static final Protection FREE = new Protection(false, null);
    public static final Protection WORLD = new Protection(true, null);

    /**
     * Algo vindo de {@code from} entrando em {@code to} invade uma protecao? So quando o destino e
     * protegido e a origem nao esta sob a mesma protecao.
     */
    public static boolean crosses(Protection from, Protection to) {
        return to.denied && !(from.denied && from.by == to.by);
    }
}
