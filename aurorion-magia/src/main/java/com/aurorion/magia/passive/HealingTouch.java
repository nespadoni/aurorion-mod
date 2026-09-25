package com.aurorion.magia.passive;

import com.aurorion.magia.compat.LsoHealing;
import com.aurorion.magia.config.MagiaConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * A <b>Mao que Cura</b>: bater em alguem cura em vez de ferir.
 *
 * <p>E a passiva da medica que trata no tapa — ela vai batendo e vai curando. O braço ainda balança e
 * o alvo recebe a animacao, o som e o recuo do golpe; o dano na saude vira cura.
 *
 * <h2>Onde ela entra</h2>
 *
 * <p>No {@code LivingDamageEvent.Pre}, zerar o dano permite que o fluxo normal do acerto continue:
 * o alvo pisca, recua e ouve o impacto. O LSO le o mesmo valor e nao cria novos ferimentos.
 *
 * <p>So vale para o golpe <b>corpo a corpo direto</b>: flecha, poçao, magia e explosao da mesma pessoa
 * continuam machucando normalmente. Curar com arco a 40 blocos seria outra coisa, e nao a mecanica
 * que a passiva descreve.
 *
 * <h2>Hostis</h2>
 *
 * <p>Por padrao ({@code healingTouchHealsHostiles = false}) criatura hostil <b>continua levando
 * dano</b>. Sem isso, o personagem ficaria literalmente incapaz de se defender de um zumbi: toda
 * mordida seria respondida com cura. Quem quiser a leitura radical — "ela cura tudo o que toca, e
 * por isso nao luta" — liga a chave no config.
 */
public final class HealingTouch {
    private static final float HEAL_PER_HIT = 1.0F;

    private HealingTouch() {
    }

    public static void intercept(LivingDamageEvent.Pre event) {
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer healer)) return;
        if (!isDirectMelee(source) || event.getNewDamage() <= 0) return;
        if (!PassiveData.get(healer.server).isActive(healer.getUUID(), Passive.HEALING_TOUCH)) return;
        if (victim == healer) return;
        if (victim instanceof Enemy && !MagiaConfig.HEALING_TOUCH_HOSTILES.get()) return;

        event.setNewDamage(0);
        heal(victim);
    }

    private static void heal(LivingEntity victim) {
        float missing = victim.getMaxHealth() - victim.getHealth();
        if (missing > 0.01F) {
            victim.heal(Math.min(HEAL_PER_HIT, missing));
        }
        if (victim instanceof ServerPlayer player) LsoHealing.healMostWounded(player, HEAL_PER_HIT);
        if (victim.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HEART, victim.getX(), victim.getY() + victim.getBbHeight() * 0.9,
                    victim.getZ(), 4, 0.35, 0.3, 0.35, 0.02);
            level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                    SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7F, 1.4F);
        }
    }

    /**
     * O golpe de mao do jogador, e so ele: {@code minecraft:player_attack} com o proprio corpo como
     * causa direta. Flecha, poçao, magia e explosao tem outra causa direta (o projetil) ou outro tipo,
     * e continuam machucando normalmente.
     *
     * <p>Arma de mod que soma um segundo dano com tipo proprio nao vira cura — so o golpe base vira.
     * E o comportamento que da para explicar em uma frase, e uma regra de "tudo o que sair da mao
     * dela" exigiria conhecer cada arma do pack.
     */
    private static boolean isDirectMelee(DamageSource source) {
        Entity direct = source.getDirectEntity();
        return direct != null && direct == source.getEntity() && source.is(DamageTypes.PLAYER_ATTACK);
    }
}
