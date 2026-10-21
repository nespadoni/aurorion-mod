package com.aurorion.magia.spell;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * A execucao: quem esta abaixo do limiar de vida morre de uma vez, como no Mortem Dico — armadura,
 * resistencia, encantamento, escudo e totem nao seguram. O tipo de dano de cada magia esta nas mesmas
 * tags {@code bypasses_*} do Mortem Dico e nomeia quem executou na mensagem de morte.
 *
 * <p>Jogador em criativo nunca e executado: diferente do Mortem Dico, as execucoes sao magias de
 * combate, e nao a "morte declarada" que a staff usa em cena.
 */
public final class Execution {
    private Execution() {
    }

    /** Fracao da vida que falta, de 0 (cheio) a 1 (morto). */
    public static float missing(LivingEntity target) {
        float max = target.getMaxHealth();
        return max <= 0 ? 0 : Math.max(0, 1 - target.getHealth() / max);
    }

    /** {@code true} se a vida atual esta em ou abaixo de {@code fraction} da maxima. */
    public static boolean below(LivingEntity target, double fraction) {
        return target.getHealth() <= target.getMaxHealth() * fraction;
    }

    /**
     * Executa. O {@code kill()} so entra quando o golpe <b>entrou</b> e algum mod ainda segurou a
     * entidade viva: se o dano foi cancelado (area sem PvP, estase, inalvejavel), a execucao tambem e —
     * senao ela passaria por cima de toda protecao que cancela dano.
     *
     * @return {@code true} so se o alvo saiu morto daqui. Quem devolve a recarga no abate (Profundezas)
     *         depende disso: o golpe cancelado pela zona segura ou salvo por outro mod nao e abate.
     */
    public static boolean execute(ServerLevel level, LivingEntity caster, LivingEntity target, ResourceKey<DamageType> type) {
        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) return false;
        if (target.hurt(FriendlyFire.source(source(level, caster, type), target), Float.MAX_VALUE) && target.isAlive()) {
            target.kill();
        }
        return !target.isAlive();
    }

    public static DamageSource source(ServerLevel level, LivingEntity caster, ResourceKey<DamageType> type) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), caster);
    }
}
