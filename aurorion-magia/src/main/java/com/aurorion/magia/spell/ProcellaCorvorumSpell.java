package com.aurorion.magia.spell;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import com.aurorion.magia.registry.MagiaEffects;
import com.aurorion.magia.registry.MagiaSpells;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Procella Corvorum — Tempestade de Corvos. A ultimate do Fiddlesticks: depois de um segundo e meio
 * de concentracao, quem conjura salta ate {@value #BLINK} blocos para onde esta olhando e uma revoada
 * de corvos passa a girar em volta dele por cinco a sete segundos, ferindo todo inimigo a
 * {@value #RADIUS} blocos duas vezes por segundo.
 *
 * <p>O relogio da revoada e o efeito {@code tempestade_de_corvos} em quem conjurou: tica so nele, so
 * enquanto dura, uma busca no raio por pulso.
 */
public final class ProcellaCorvorumSpell extends AurorionSpell {
    public static final int PULSE_TICKS = 10;
    private static final int BLINK = 8;
    private static final int RADIUS = 5;
    private static final int MAX_TARGETS = 16;
    private static final String DAMAGE_KEY = AurorionMagia.MOD_ID + ":corvos";

    public ProcellaCorvorumSpell() {
        super("procella_corvorum", SchoolRegistry.ELDRITCH_RESOURCE, SpellRarity.LEGENDARY, 3, 90, CastType.LONG);
        this.baseSpellPower = 3;
        this.spellPowerPerLevel = 1;
        this.baseManaCost = 100;
        this.manaCostPerLevel = 25;
        this.castTime = 30;
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.CAST_T_POSE;
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundEvents.PHANTOM_AMBIENT);
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.PHANTOM_SWOOP);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.3f, 0.1f, 0.35f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.dano_por_segundo", Utils.stringTruncation(damagePower(spellLevel, caster), 1)),
                Component.translatable("ui.aurorion_magia.raio", RADIUS),
                Component.translatable("ui.aurorion_magia.duracao", Utils.timeFromTicks(duration(spellLevel), 1)),
                Component.translatable("ui.aurorion_magia.salto", BLINK));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            Vec3 destination = Hits.aimGround(serverLevel, entity, BLINK);
            if (destination.distanceToSqr(entity.position()) > 2.25) Utils.handleSpellTeleport(this, entity, destination);
            int duration = duration(spellLevel);
            // Dois pulsos por segundo: metade do poder em cada um.
            entity.getPersistentData().putFloat(DAMAGE_KEY, damagePower(spellLevel, entity) * 0.5f);
            entity.addEffect(new MobEffectInstance(MagiaEffects.CROW_STORM, duration, spellLevel - 1, false, false, true));
            MagiaNetwork.sendVisual(entity, entity, SpellVisualPayload.Kind.PROCELLA_CORVORUM, duration, entity.position(), RADIUS);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /** Um pulso da revoada, chamado pelo efeito. */
    public static void pulse(LivingEntity caster, int spellLevel) {
        if (!(caster.level() instanceof ServerLevel level) || !caster.isAlive()) return;
        float damage = caster.getPersistentData().contains(DAMAGE_KEY) ? caster.getPersistentData().getFloat(DAMAGE_KEY) : 1.5f;
        for (LivingEntity victim : Hits.around(level, caster, caster.position(), RADIUS, MAX_TARGETS, t -> Hits.enemy(caster, t))) {
            DamageSources.ignoreNextKnockback(victim);
            FriendlyFire.applyDamage(victim, damage, MagiaSpells.PROCELLA_CORVORUM.get().getDamageSource(caster));
        }
        if (caster.tickCount % (PULSE_TICKS * 2) == 0) sound(caster, SoundEvents.PHANTOM_FLAP, 1.2f, 0.7f);
    }

    /** Base: 5 s no nivel 1, +1 s por nivel. SpellBalance dobra este tempo. */
    private static int duration(int spellLevel) {
        return SpellBalance.duration(100 + 20 * (spellLevel - 1));
    }
}
