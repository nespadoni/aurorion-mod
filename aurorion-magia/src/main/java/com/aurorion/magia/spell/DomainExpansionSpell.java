package com.aurorion.magia.spell;

import com.aurorion.magia.config.MagiaConfig;
import com.aurorion.magia.entity.SpellZoneEntity;
import com.aurorion.magia.passive.DomainPassives;
import com.aurorion.magia.registry.MagiaEffects;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.ICastDataSerializable;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.RecastInstance;
import io.redspace.ironsspellbooks.capabilities.magic.RecastResult;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.Optional;

/** Expansoes proibidas: a segunda conjuracao recolhe o dominio antes do prazo. */
public abstract class DomainExpansionSpell extends AurorionSpell {
    private final SpellZoneEntity.Shape shape;

    protected DomainExpansionSpell(String id, SpellZoneEntity.Shape shape) {
        super(id, SchoolRegistry.ELDRITCH_RESOURCE, SpellRarity.LEGENDARY, 3, 180, CastType.LONG, true);
        this.shape = shape;
        baseSpellPower = 8; spellPowerPerLevel = 3;
        baseManaCost = 200; manaCostPerLevel = 50;
        castTime = 40;
    }

    @Override
    public AnimationHolder getCastStartAnimation() { return SpellAnimations.CAST_T_POSE; }
    @Override
    public Optional<SoundEvent> getCastStartSound() { return Optional.of(SoundEvents.BEACON_POWER_SELECT); }
    @Override
    public int getRecastCount(int spellLevel, @Nullable LivingEntity entity) { return 2; }
    @Override
    public int getEffectiveCastTime(int spellLevel, @Nullable LivingEntity entity) {
        var marker = entity == null ? null : entity.getEffect(MagiaEffects.DOMAIN_EXPANSION);
        return marker != null && marker.getAmplifier() == shape.ordinal() ? 0 : super.getEffectiveCastTime(spellLevel, entity);
    }

    private int duration(int level, LivingEntity caster) {
        int ticks = shape == SpellZoneEntity.Shape.INFINITE_VOID
                ? MagiaConfig.VOID_DURATION.get() : MagiaConfig.SHRINE_DURATION.get();
        ticks += ticks * Math.max(0, level - 1) / 5;
        return caster == null ? ticks : DomainPassives.duration(caster, ticks);
    }

    private float radius(@Nullable LivingEntity caster) {
        float radius = shape == SpellZoneEntity.Shape.INFINITE_VOID ? MagiaConfig.VOID_RADIUS.get()
                : MagiaConfig.SHRINE_RADIUS.get();
        return caster == null ? radius : DomainPassives.radius(caster, radius,
                shape == SpellZoneEntity.Shape.INFINITE_VOID ? 50 : 200);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int level, @Nullable LivingEntity caster) {
        return List.of(Component.translatable("ui.aurorion_magia.raio", Utils.stringTruncation(radius(caster), 1)),
                Component.translatable("ui.aurorion_magia.dominio_tempo", Utils.timeFromTicks(duration(level, caster), 1)),
                shape == SpellZoneEntity.Shape.INFINITE_VOID ? Component.translatable("ui.aurorion_magia.paralisia_total")
                        : Component.translatable("ui.aurorion_magia.cortes", Utils.stringTruncation(damagePower(level, caster), 1)),
                Component.translatable("ui.aurorion_magia.reconjurar_solta"),
                Component.translatable("ui.aurorion_magia.proibida"));
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData data) {
        return entity instanceof ServerPlayer;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource source, MagicData data) {
        if (level instanceof ServerLevel server && entity instanceof ServerPlayer && data != null
                && !data.getPlayerRecasts().hasRecastForSpell(getSpellId())) {
            int ticks = duration(spellLevel, entity);
            Domains.start(server, entity, shape, radius(entity), ticks, damagePower(spellLevel, entity));
            data.getPlayerRecasts().addRecast(new RecastInstance(getSpellId(), spellLevel, 2, ticks, source, null), data);
        }
        super.onCast(level, spellLevel, entity, source, data);
    }

    @Override
    public void onRecastFinished(ServerPlayer player, RecastInstance instance, RecastResult result,
                                 ICastDataSerializable castData) {
        super.onRecastFinished(player, instance, result, castData);
        Domains.stop(player, shape);
    }
}
