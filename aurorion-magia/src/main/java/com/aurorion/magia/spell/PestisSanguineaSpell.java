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
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
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
 * Pestis Sanguinea — Hemopraga. A ultimate do Vladimir: uma praga cai sobre a area mirada e infecta
 * todos os inimigos dentro dela por quatro segundos.
 *
 * <p>Infectado recebe {@value #AMPLIFY_PERCENT}% a mais de dano de tudo — espada, flecha, magia, de quem
 * for. Quando a praga termina, ela cobra: dano magico em cada infectado, e quem conjurou se cura um
 * pouco por cada um.
 *
 * <p>Custo: uma busca na area ao conjurar. O aumento de dano e reacao ao dano; a cobranca e o fim do
 * efeito. Nada roda por tick.
 */
public final class PestisSanguineaSpell extends AurorionSpell {
    public static final float AMPLIFY = 1.1f;
    private static final int AMPLIFY_PERCENT = 10;
    private static final int RANGE = 24;
    private static final float RADIUS = 4.5f;
    private static final int DURATION = 80;
    private static final int MAX_TARGETS = 16;
    private static final String PLAGUE_KEY = AurorionMagia.MOD_ID + ":hemopraga";

    public PestisSanguineaSpell() {
        super("pestis_sanguinea", SchoolRegistry.BLOOD_RESOURCE, SpellRarity.LEGENDARY, 3, 80, CastType.INSTANT);
        this.baseSpellPower = 10;
        this.spellPowerPerLevel = 4;
        this.baseManaCost = 120;
        this.manaCostPerLevel = 25;
        this.castTime = 0;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.WITHER_AMBIENT);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.6f, 0.0f, 0.12f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.dano", Utils.stringTruncation(getSpellPower(spellLevel, caster), 1)),
                Component.translatable("ui.aurorion_magia.dano_recebido", AMPLIFY_PERCENT),
                Component.translatable("ui.aurorion_magia.raio", Utils.stringTruncation(RADIUS, 1)),
                Component.translatable("ui.aurorion_magia.alcance", RANGE));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            Vec3 at = Hits.aimGround(serverLevel, entity, RANGE);
            float damage = getSpellPower(spellLevel, entity);
            for (LivingEntity victim : Hits.around(serverLevel, entity, at, RADIUS, MAX_TARGETS, t -> Hits.enemy(entity, t))) {
                infect(victim, entity, damage);
            }
            sound(serverLevel, at, SoundEvents.WITHER_AMBIENT, 1.4f, 0.6f);
            sound(serverLevel, at, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1.2f, 0.5f);
            MagiaNetwork.sendVisualAt(serverLevel, entity, SpellVisualPayload.Kind.PESTIS_AREA, 30, at, RADIUS);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private static void infect(LivingEntity victim, LivingEntity caster, float damage) {
        CompoundTag plague = new CompoundTag();
        plague.putUUID("By", caster.getUUID());
        plague.putFloat("Damage", damage);
        victim.getPersistentData().put(PLAGUE_KEY, plague);
        victim.addEffect(new MobEffectInstance(MagiaEffects.HEMOPLAGUE, DURATION, 0, false, false, true), caster);
        MagiaNetwork.sendVisual(caster, victim, SpellVisualPayload.Kind.PESTIS_MARK, DURATION);
    }

    /** A praga terminou: a cobranca. */
    public static void burst(LivingEntity victim) {
        CompoundTag data = victim.getPersistentData();
        if (!data.contains(PLAGUE_KEY, Tag.TAG_COMPOUND) || !(victim.level() instanceof ServerLevel level)) return;
        CompoundTag plague = data.getCompound(PLAGUE_KEY);
        data.remove(PLAGUE_KEY);
        if (!plague.hasUUID("By") || !(level.getEntity(plague.getUUID("By")) instanceof LivingEntity caster)) return;
        float damage = plague.getFloat("Damage");
        if (FriendlyFire.applyDamage(victim, damage, MagiaSpells.PESTIS_SANGUINEA.get().getDamageSource(caster))
                && caster.isAlive()) {
            caster.heal(damage * 0.3f);
        }
        sound(victim, SoundEvents.SLIME_SQUISH, 1.2f, 0.5f);
        MagiaNetwork.sendVisual(caster, victim, SpellVisualPayload.Kind.PESTIS_MARK, 0);
    }

    /** A praga foi curada (leite) antes de cobrar: a cobranca nao vem. */
    public static void clear(LivingEntity victim) {
        victim.getPersistentData().remove(PLAGUE_KEY);
    }
}
