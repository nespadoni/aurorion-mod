package com.aurorion.magia.spell;

import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import com.aurorion.magia.registry.MagiaEffects;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Dolor Cruciatus: canalizacao de sangue que prende o alvo numa dor que nao deixa andar.
 *
 * <h2>Como roda</h2>
 *
 * <p>Magia {@link CastType#CONTINUOUS}: o Iron's chama {@link #onCast} a cada
 * {@link MagicManager#CONTINUOUS_CAST_TICK_INTERVAL} ticks (10) enquanto o botao estiver segurado.
 * Cada chamada e um <b>pulso</b>:
 *
 * <ol>
 *   <li>confere se o alvo ainda existe, esta no alcance e a vista — uma distancia e UM raycast de
 *       linha de visao a cada meio segundo, nada por tick;</li>
 *   <li>causa o dano do pulso, sem empurrao (paralisado nao voa para tras);</li>
 *   <li>renova o efeito {@code cruciatus} por {@value #EFFECT_TICKS} ticks — um pouco mais que o
 *       intervalo, entao a paralisia cai sozinha logo depois que a canalizacao para;</li>
 *   <li>reenvia o visual do feixe com o mesmo ttl curto.</li>
 * </ol>
 *
 * <p>O alvo e escolhido uma vez, no inicio, pelo raycast do proprio Iron's
 * ({@code Utils.preCastTargetHelper}: um raio com hitbox inflada para ajudar a mira). Perder o alvo no
 * meio (morreu, saiu de vista, fugiu do alcance) nao troca de alvo: os pulsos seguintes so falham.
 *
 * <p>A escola padrao e Sangue; a staff muda pelo config de magias do Iron's, sem recompilar.
 */
public final class DolorCruciatusSpell extends AurorionSpell {
    private static final int RANGE = 20;
    /** Alcance para manter a canalizacao: um pouco de folga sobre o de mirar. */
    private static final double KEEP_RANGE_SQR = (RANGE * 1.25) * (RANGE * 1.25);
    private static final int EFFECT_TICKS = MagicManager.CONTINUOUS_CAST_TICK_INTERVAL + 5;
    private static final int PULSES_PER_SECOND = 20 / MagicManager.CONTINUOUS_CAST_TICK_INTERVAL;

    public DolorCruciatusSpell() {
        super("dolor_cruciatus", SchoolRegistry.BLOOD_RESOURCE, SpellRarity.EPIC, 5, 35, CastType.CONTINUOUS);
        this.baseSpellPower = 2;
        this.spellPowerPerLevel = 1;
        this.baseManaCost = 6;
        this.manaCostPerLevel = 2;
        // Em magia continua, castTime e a duracao maxima da canalizacao: 4 segundos.
        this.castTime = 80;
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.ANIMATION_CONTINUOUS_CAST;
    }

    // Sem getCastFinishAnimation: o padrao de magia continua no Iron's e AnimationHolder.none(), que
    // MANDA PARAR a animacao no fim da canalizacao. Devolver pass() aqui (como este arquivo fazia)
    // quer dizer "nao mexa na animacao", e o ClientSpellCastHelper so cancela a pose quando a
    // conjuracao e interrompida. Quem segurava o botao ate o tempo acabar ficava com o boneco de mao
    // estendida para sempre, ate conjurar outra coisa. Nao reponha o override.

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundEvents.EVOKER_PREPARE_ATTACK);
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.empty();
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.6f, 0.02f, 0.06f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.dano_por_segundo",
                        Utils.stringTruncation(pulseDamage(spellLevel, caster) * PULSES_PER_SECOND, 1)),
                Component.translatable("ui.aurorion_magia.alcance", RANGE));
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return aim(level, entity, playerMagicData, RANGE, false, target -> true);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            LivingEntity target = target(serverLevel, entity, playerMagicData);
            if (target != null && inReach(entity, target)) {
                pulse(entity, target, spellLevel);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    @Override
    public boolean shouldAIStopCasting(int spellLevel, Mob mob, LivingEntity target) {
        return !inReach(mob, target);
    }

    private void pulse(LivingEntity caster, LivingEntity target, int spellLevel) {
        DamageSources.ignoreNextKnockback(target);
        DamageSources.applyDamage(target, pulseDamage(spellLevel, caster), getDamageSource(caster));

        // visible=false: sem as bolhas de pocao do vanilla, que viajam como dado de entidade para
        // todo mundo por perto. O visual e o nosso feixe; o icone continua no HUD do alvo.
        target.addEffect(new MobEffectInstance(MagiaEffects.CRUCIATUS, EFFECT_TICKS, 0, false, false, true), caster);

        MagiaNetwork.sendVisual(caster, target, SpellVisualPayload.Kind.CRUCIATUS_BEAM, EFFECT_TICKS);
    }

    private float pulseDamage(int spellLevel, @Nullable LivingEntity caster) {
        return getSpellPower(spellLevel, caster) * 0.5f;
    }

    private static boolean inReach(LivingEntity caster, LivingEntity target) {
        return target.isAlive()
                && caster.level() == target.level()
                && caster.distanceToSqr(target) <= KEEP_RANGE_SQR
                && caster.hasLineOfSight(target);
    }
}
