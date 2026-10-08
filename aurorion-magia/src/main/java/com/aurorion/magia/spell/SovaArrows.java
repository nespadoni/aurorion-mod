package com.aurorion.magia.spell;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.entity.MagiaProjectileEntity;
import com.aurorion.magia.registry.MagiaEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * As flechas encantadas do Sova: a magia nao atira nada — ela encanta a <b>proxima flecha</b> que sair
 * do arco (ou da besta) de quem conjurou, em ate {@value #WINDOW_TICKS} ticks. Quem mira e o arco, com
 * a forca e a queda de uma flecha de verdade.
 *
 * <ul>
 *   <li><b>Reconhecimento</b> (amplificador 0): onde a flecha cravar, ela pulsa duas vezes e revela os
 *       inimigos em volta (Brilho, visivel atraves das paredes).</li>
 *   <li><b>Choque</b> (amplificador 1): onde a flecha bater, ela descarrega em area.</li>
 * </ul>
 *
 * <p>O encanto e o efeito {@code flecha_imbuida} em quem conjurou; o que a flecha carrega (tipo, dano,
 * raio) vai no {@code persistentData} dela no instante do disparo. Tudo e reacao a evento — a flecha
 * entrando no mundo e o impacto — e nada roda por tick.
 */
public final class SovaArrows {
    public static final int WINDOW_TICKS = 1200;
    public static final int RECON = 0;
    public static final int SHOCK = 1;
    private static final String CHARGE_KEY = AurorionMagia.MOD_ID + ":sova_carga";
    private static final String ARROW_KEY = AurorionMagia.MOD_ID + ":sova_flecha";

    private SovaArrows() {
    }

    /** Encanta a proxima flecha. {@code power} e o dano (choque) ou a duracao da revelacao (reconhecimento). */
    public static void imbue(LivingEntity caster, int kind, float power, float radius) {
        CompoundTag charge = new CompoundTag();
        charge.putInt("Kind", kind);
        charge.putFloat("Power", power);
        charge.putFloat("Radius", radius);
        caster.getPersistentData().put(CHARGE_KEY, charge);
        caster.addEffect(new MobEffectInstance(MagiaEffects.IMBUED_ARROW, WINDOW_TICKS, kind, false, false, true));
        if (caster instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable(kind == SHOCK
                    ? "aurorion_magia.flecha_choque_pronta" : "aurorion_magia.flecha_reconhecimento_pronta")
                    .withStyle(ChatFormatting.AQUA), true);
        }
    }

    /** Uma flecha entrou no mundo. Se quem a disparou tinha o encanto, ela o leva. */
    public static void onArrowSpawn(AbstractArrow arrow) {
        if (!(arrow.getOwner() instanceof LivingEntity owner) || !owner.hasEffect(MagiaEffects.IMBUED_ARROW)) return;
        CompoundTag data = owner.getPersistentData();
        if (!data.contains(CHARGE_KEY, Tag.TAG_COMPOUND)) return;
        arrow.getPersistentData().put(ARROW_KEY, data.getCompound(CHARGE_KEY).copy());
        data.remove(CHARGE_KEY);
        owner.removeEffect(MagiaEffects.IMBUED_ARROW);
        AurorionSpell.sound(owner, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.6f);
    }

    /** A flecha encantada bateu. O encanto sai dela na hora: um impacto, um efeito. */
    public static void onImpact(AbstractArrow arrow, HitResult hit) {
        CompoundTag data = arrow.getPersistentData();
        if (!data.contains(ARROW_KEY, Tag.TAG_COMPOUND) || !(arrow.level() instanceof ServerLevel level)
                || !(arrow.getOwner() instanceof LivingEntity owner)) return;
        CompoundTag charge = data.getCompound(ARROW_KEY);
        data.remove(ARROW_KEY);
        Vec3 at = hit instanceof EntityHitResult entityHit ? entityHit.getEntity().position() : hit.getLocation();
        float power = charge.getFloat("Power");
        float radius = charge.getFloat("Radius");
        if (charge.getInt("Kind") == SHOCK) {
            SagittaFulminisSpell.discharge(level, owner, at, radius, power);
        } else {
            // O relogio dos pulsos: o mesmo projetil das outras magias, parado e invisivel onde a flecha cravou.
            MagiaProjectileEntity.launch(level, owner, MagiaProjectileEntity.Shape.SPECULA, at, Vec3.ZERO, 0,
                    power, radius, SagittaExploratrixSpell.PULSE_TICKS * SagittaExploratrixSpell.PULSES + 5);
            AurorionSpell.sound(level, at, SoundEvents.BEACON_POWER_SELECT, 1.0f, 1.8f);
        }
    }

    /** O encanto venceu sem flecha nenhuma: a carga guardada sai junto. */
    public static void clear(LivingEntity caster) {
        caster.getPersistentData().remove(CHARGE_KEY);
    }
}
