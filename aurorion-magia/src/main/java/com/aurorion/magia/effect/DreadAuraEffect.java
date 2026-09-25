package com.aurorion.magia.effect;

import com.aurorion.magia.passive.DreadAura;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * O marcador de quem esta com a <b>Presenca Aterradora</b> ligada — e o relogio dela.
 *
 * <p>Este e o unico ponto periodico da passiva, e ele mora aqui por um motivo de arquitetura do
 * modulo (SDD §5.1 e a tabela de desempenho do README): <b>nada neste mod assina
 * {@code ServerTickEvent} nem {@code PlayerTickEvent}</b>. Uma varredura "de todo jogador que tem
 * aura" custaria uma passada na lista de jogadores a cada tick do servidor, com 80 pessoas online,
 * mesmo quando ninguem tem aura nenhuma. Como efeito, o vanilla ja tica a entidade afetada e so ela:
 * sem aura ligada no servidor, o custo e exatamente zero.
 *
 * <p>O efeito e infinito e invisivel. Quem o coloca e quem o tira e o comando de ligar/desligar
 * ({@code /aurorion passivas ligar presenca_terrivel}); ele tambem sai sozinho no logout e volta no
 * proximo login se a passiva continuar ligada.
 */
public final class DreadAuraEffect extends MobEffect {
    private static final int INTERVAL = DreadAura.INTERVAL_TICKS;

    public DreadAuraEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x140A1E);
    }

    /**
     * <b>Sempre {@code true}, e o intervalo e conferido no tick.</b> Este efeito e infinito, e a
     * duracao de um efeito infinito nao anda: ela fica em {@code -1} para sempre. O filtro habitual
     * do modulo ({@code duration % INTERVALO == 0}) nunca daria zero aqui, e a aura simplesmente nao
     * pulsaria — o unico jeito de errar isso em silencio, porque tudo o resto continuaria certo.
     */
    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.tickCount % INTERVAL == 0 && entity instanceof ServerPlayer player) DreadAura.pulse(player);
        return true;
    }
}
