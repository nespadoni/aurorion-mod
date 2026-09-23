package com.aurorion.magia.spell;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.registry.MagiaEffects;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * A fisica da Mao do Algoz: segurar no ar, conduzir pela mira, soltar e arremessar.
 *
 * <p>Enquanto segura, o alvo e puxado a cada tick para um ponto a {@value #HOLD_DISTANCE} blocos a
 * frente dos olhos de quem conjura. A velocidade e proporcional a distancia ate esse ponto (uma
 * mola sem oscilacao): virar a mira arrasta o alvo, olhar para cima o levanta.
 *
 * <p>Ao soltar, o alvo conserva o movimento que tinha, ampliado, mais um empurrao na direcao da
 * mira. Quem voa rapido recebe o efeito {@code arremessado}, que confere a colisao a cada tick e
 * machuca na primeira parede.
 */
public final class Telekinesis {
    private static final String KEY_SPEED = AurorionMagia.MOD_ID + ":velocidade_voo";
    static final double HOLD_DISTANCE = 4.5;
    private static final double SPRING = 0.35;
    private static final double MAX_HOLD_SPEED = 1.4;
    private static final double MAX_THROW_SPEED = 3.0;
    private static final double THROWN_MIN_SPEED = 0.8;
    private static final int THROWN_TICKS = 30;

    private Telekinesis() {
    }

    /** Um tick de segurar. */
    public static void hold(LivingEntity caster, LivingEntity target) {
        Vec3 look = caster.getViewVector(1.0f);
        Vec3 desired = caster.getEyePosition().add(look.scale(HOLD_DISTANCE))
                .subtract(0, target.getBbHeight() * 0.5, 0);
        Vec3 velocity = desired.subtract(target.position()).scale(SPRING);
        if (velocity.length() > MAX_HOLD_SPEED) velocity = velocity.normalize().scale(MAX_HOLD_SPEED);
        AurorionSpell.launch(target, velocity);
        target.resetFallDistance();
    }

    public static void release(LivingEntity caster, LivingEntity target, int spellLevel) {
        Vec3 carried = target.getDeltaMovement();
        Vec3 thrown = carried.scale(1.8).add(caster.getViewVector(1.0f).scale(0.35 + 0.1 * spellLevel));
        if (thrown.length() > MAX_THROW_SPEED) thrown = thrown.normalize().scale(MAX_THROW_SPEED);
        AurorionSpell.launch(target, thrown);
        if (thrown.length() > THROWN_MIN_SPEED) {
            target.addEffect(new MobEffectInstance(MagiaEffects.THROWN, THROWN_TICKS, spellLevel - 1,
                    false, false, false), caster);
        }
    }

    /**
     * Tick do efeito {@code arremessado}.
     *
     * <p>A velocidade de antes do choque vem do tick anterior: no tick da colisao o vanilla ja zerou o
     * eixo que bateu.
     *
     * @return {@code true} quando o voo acabou (bateu ou pousou) e o efeito deve sair
     */
    public static boolean checkImpact(LivingEntity entity, int amplifier) {
        var data = entity.getPersistentData();
        double previous = data.getDouble(KEY_SPEED);
        boolean hitWall = entity.horizontalCollision || entity.verticalCollision && !entity.onGround();

        if (hitWall && previous > 0.5) {
            float damage = (float) (previous * (4 + amplifier));
            entity.hurt(entity.damageSources().flyIntoWall(), damage);
            AurorionSpell.sound(entity, SoundEvents.PLAYER_BIG_FALL, 1.0f, 0.8f);
            AurorionSpell.sound(entity, SoundEvents.ANVIL_LAND, 0.3f, 0.6f);
            data.remove(KEY_SPEED);
            return true;
        }
        if (entity.onGround() && previous < 0.3 && previous > 0) {
            data.remove(KEY_SPEED);
            return true;
        }
        data.putDouble(KEY_SPEED, AurorionSpell.motionOf(entity).length());
        return false;
    }
}
