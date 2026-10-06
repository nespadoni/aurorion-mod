package com.aurorion.magia.spell;

import com.aurorion.magia.entity.MagiaProjectileEntity;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import com.aurorion.magia.registry.MagiaSpells;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Manus Rapax — Puxao Bionico. A garra do Blitzcrank: uma mao eletrica voa reto por {@value #RANGE}
 * blocos e o primeiro vivo que ela tocar e ferido e puxado ate a frente de quem conjurou, chegando
 * atordoado por meio segundo.
 *
 * <p>Chefes ({@code imune_deslocamento}) levam o dano mas nao saem do lugar. O puxao e uma velocidade
 * dada uma vez — o cliente do jogador puxado faz o resto do voo, como num empurrao do vanilla.
 */
public final class ManusRapaxSpell extends AurorionSpell {
    private static final int RANGE = 20;
    private static final double SPEED = 2.0;
    private static final int STUN_TICKS = 12;

    public ManusRapaxSpell() {
        super("manus_rapax", SchoolRegistry.LIGHTNING_RESOURCE, SpellRarity.RARE, 5, 18, CastType.INSTANT);
        this.baseSpellPower = 3;
        this.spellPowerPerLevel = 1;
        this.baseManaCost = 40;
        this.manaCostPerLevel = 5;
        this.castTime = 0;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.CROSSBOW_SHOOT);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(1.0f, 0.85f, 0.2f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.dano", Utils.stringTruncation(getSpellPower(spellLevel, caster), 1)),
                Component.translatable("ui.aurorion_magia.alcance", RANGE),
                Component.translatable("ui.aurorion_magia.puxa_ate_voce"));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            Vec3 look = entity.getLookAngle();
            Vec3 from = entity.getEyePosition().subtract(0, 0.3, 0).add(look.scale(0.5));
            MagiaProjectileEntity.launch(serverLevel, entity, MagiaProjectileEntity.Shape.RAPAX, from, look.scale(SPEED),
                    0.3f, getSpellPower(spellLevel, entity), 0, (int) Math.ceil(RANGE / SPEED));
            sound(entity, SoundEvents.PISTON_EXTEND, 1.0f, 1.4f);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /** A garra pegou alguem: dano, e o puxao ate a frente de quem conjurou. */
    public static void grab(ServerLevel level, LivingEntity caster, LivingEntity victim, float damage) {
        DamageSources.ignoreNextKnockback(victim);
        // Sem dano, sem puxao: o que cancelou o golpe (estase, area protegida, outro mod) segura a garra.
        if (!FriendlyFire.applyDamage(victim, damage, MagiaSpells.MANUS_RAPAX.get().getDamageSource(caster))) return;
        sound(victim, SoundEvents.CHAIN_BREAK, 1.4f, 0.7f);
        sound(victim, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6f, 1.6f);
        if (!victim.isAlive()) return;
        MagiaNetwork.sendVisual(caster, victim, SpellVisualPayload.Kind.MANUS_RAPAX, 14);
        if (Displacement.isImmune(victim)) return;

        Vec3 front = caster.getLookAngle().multiply(1, 0, 1);
        front = front.lengthSqr() < 1.0E-4 ? Vec3.ZERO : front.normalize().scale(1.4);
        Vec3 delta = caster.position().add(front).subtract(victim.position());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        // No ar, o atrito do vanilla (0,91 por tick) leva um impulso v a ~10v blocos: v = distancia/10.
        double speed = Math.min(2.6, horizontal / 10);
        Vec3 pull = horizontal < 0.05 ? Vec3.ZERO : new Vec3(delta.x / horizontal * speed, 0, delta.z / horizontal * speed);
        launch(victim, pull.add(0, 0.42 + Math.max(0, delta.y) * 0.08, 0));
        victim.resetFallDistance();
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, STUN_TICKS, 4, false, false, true), caster);
    }
}
