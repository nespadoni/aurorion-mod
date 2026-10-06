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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
 * Lux Finalis — Centelha Final. A ultimate da Lux: depois de um segundo acumulando luz, um feixe
 * reto de {@value #RANGE} blocos fere todos os inimigos no caminho.
 *
 * <h2>Iluminacao</h2>
 *
 * <p>Todo mundo que o feixe atravessa fica <b>Iluminado</b> por seis segundos. O proximo golpe de quem
 * conjurou nesse alvo — espada, flecha, outra magia — consome a marca e causa um dano extra de luz. A
 * propria Centelha primeiro detona a marca que ja estava la e depois a renova, como no jogo: duas
 * Centelhas seguidas no mesmo alvo detonam a primeira marca e deixam outra.
 *
 * <p>O feixe para no primeiro bloco solido. O dono da marca e o dano dela moram no
 * {@code persistentData} do alvo; o efeito e so o prazo.
 *
 * <p>Custo: um {@code clip} de blocos e uma busca de entidades ao longo do feixe, uma vez por
 * conjuracao. A detonacao e reacao ao dano ({@code MagiaServerEvents}), sem tick.
 */
public final class LuxFinalisSpell extends AurorionSpell {
    public static final int MARK_TICKS = 120;
    private static final int RANGE = 48;
    private static final double WIDTH = 0.6;
    private static final int MAX_TARGETS = 24;
    private static final String MARK_KEY = AurorionMagia.MOD_ID + ":iluminado";

    public LuxFinalisSpell() {
        super("lux_finalis", SchoolRegistry.HOLY_RESOURCE, SpellRarity.EPIC, 3, 40, CastType.LONG);
        this.baseSpellPower = 10;
        this.spellPowerPerLevel = 4;
        this.baseManaCost = 80;
        this.manaCostPerLevel = 20;
        this.castTime = 20;
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.ONE_HANDED_RAY_CHARGE;
    }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return SpellAnimations.ONE_HANDED_RAY_SHOOT;
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundEvents.BEACON_POWER_SELECT);
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.BEACON_ACTIVATE);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(1.0f, 0.95f, 0.6f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.dano", Utils.stringTruncation(getSpellPower(spellLevel, caster), 1)),
                Component.translatable("ui.aurorion_magia.alcance", RANGE),
                Component.translatable("ui.aurorion_magia.iluminacao",
                        Utils.stringTruncation(burst(spellLevel, caster), 1)));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) fire(serverLevel, entity, spellLevel);
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private void fire(ServerLevel level, LivingEntity caster, int spellLevel) {
        Vec3 from = caster.getEyePosition().subtract(0, 0.25, 0);
        Vec3 end = Hits.beamEnd(level, caster, from, caster.getLookAngle(), RANGE, false);
        float damage = getSpellPower(spellLevel, caster);
        float burst = burst(spellLevel, caster);

        for (LivingEntity victim : Hits.along(level, caster, from, end, WIDTH, MAX_TARGETS, t -> Hits.enemy(caster, t))) {
            detonate(victim, caster);
            FriendlyFire.applyDamage(victim, damage, getDamageSource(caster));
            if (victim.isAlive()) illuminate(victim, caster, burst);
        }

        sound(level, end, SoundEvents.BEACON_DEACTIVATE, 1.4f, 1.8f);
        sound(caster, SoundEvents.GUARDIAN_ATTACK, 1.2f, 1.6f);
        // Preso a quem conjurou (e nao ao chunk da ponta): o feixe tem quase cinquenta blocos, e quem esta
        // perto de quem atirou precisa ve-lo inteiro.
        MagiaNetwork.sendVisual(caster, caster, SpellVisualPayload.Kind.LUX_FINALIS, 16, end, (float) WIDTH);
    }

    private static void illuminate(LivingEntity victim, LivingEntity caster, float burst) {
        victim.addEffect(new MobEffectInstance(MagiaEffects.ILLUMINATED, MARK_TICKS, 0, false, false, true), caster);
        CompoundTag mark = new CompoundTag();
        mark.putUUID("By", caster.getUUID());
        mark.putFloat("Burst", burst);
        victim.getPersistentData().put(MARK_KEY, mark);
    }

    /**
     * A passiva: um golpe de {@code attacker} num alvo que ele mesmo Iluminou consome a marca e causa o
     * dano de luz. Chamado pelo evento de dano; a marca sai antes do dano extra, entao ele nao detona
     * de novo.
     */
    public static void detonate(LivingEntity victim, LivingEntity attacker) {
        if (!victim.hasEffect(MagiaEffects.ILLUMINATED)) return;
        CompoundTag data = victim.getPersistentData();
        if (!data.contains(MARK_KEY, Tag.TAG_COMPOUND)) return;
        CompoundTag mark = data.getCompound(MARK_KEY);
        if (!mark.hasUUID("By") || !mark.getUUID("By").equals(attacker.getUUID())) return;

        data.remove(MARK_KEY);
        victim.removeEffect(MagiaEffects.ILLUMINATED);
        FriendlyFire.applyDamage(victim, mark.getFloat("Burst"), MagiaSpells.LUX_FINALIS.get().getDamageSource(attacker));
        sound(victim, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4f, 1.6f);
        MagiaNetwork.sendVisual(attacker, victim, SpellVisualPayload.Kind.LUX_DETONATE, 12);
    }

    /** A marca venceu sem ser detonada: o dono dela sai junto. */
    public static void clearMark(LivingEntity victim) {
        victim.getPersistentData().remove(MARK_KEY);
    }

    /** Metade do dano do feixe. */
    private float burst(int spellLevel, @Nullable LivingEntity caster) {
        return getSpellPower(spellLevel, caster) * 0.5f;
    }
}
