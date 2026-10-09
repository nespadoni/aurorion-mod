package com.aurorion.magia.spell;

import com.aurorion.magia.config.MagiaConfig;
import com.aurorion.magia.compat.AreasMagic;
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

/** Arrasta outro jogador a um duelo isolado; o conjurador recebe poder e resistencia espirituais. */
public final class ReinoAlmasSpell extends AurorionSpell {
    private static final int RANGE = 24;
    public ReinoAlmasSpell() {
        super("reino_almas", SchoolRegistry.ELDRITCH_RESOURCE, SpellRarity.LEGENDARY, 1, 120, CastType.LONG, true);
        baseSpellPower = 1; spellPowerPerLevel = 0;
        baseManaCost = 150; manaCostPerLevel = 0; castTime = 30;
    }

    @Override
    public AnimationHolder getCastStartAnimation() { return SpellAnimations.CAST_T_POSE; }
    @Override
    public Optional<SoundEvent> getCastStartSound() { return Optional.of(SoundEvents.SOUL_ESCAPE.value()); }
    @Override
    public int getRecastCount(int spellLevel, @Nullable LivingEntity entity) { return 2; }
    @Override
    public int getEffectiveCastTime(int spellLevel, @Nullable LivingEntity entity) {
        var marker = entity == null ? null : entity.getEffect(MagiaEffects.SOUL_REALM);
        return marker != null && marker.getAmplifier() == 1 ? 0 : super.getEffectiveCastTime(spellLevel, entity);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int level, @Nullable LivingEntity caster) {
        int ticks = caster == null ? MagiaConfig.SOUL_DURATION.get() : DomainPassives.duration(caster, MagiaConfig.SOUL_DURATION.get());
        return List.of(Component.translatable("ui.aurorion_magia.alcance", RANGE),
                Component.translatable("ui.aurorion_magia.dominio_tempo", Utils.timeFromTicks(ticks, 1)),
                Component.translatable("ui.aurorion_magia.reino_vantagem"),
                Component.translatable("ui.aurorion_magia.reconjurar_solta"),
                Component.translatable("ui.aurorion_magia.proibida"));
    }

    private static boolean eligible(LivingEntity caster, LivingEntity target) {
        return target instanceof ServerPlayer && Hits.hittable(caster, target) && Hits.enemy(caster, target)
                && target.level() instanceof ServerLevel level && AreasMagic.allowedAt(level, target.position(), caster.getUUID())
                && !SoulRealm.active(target) && !Binding.isBound(target)
                && !target.hasEffect(MagiaEffects.INFINITE_VOID) && !target.hasEffect(MagiaEffects.STASIS) && !target.hasEffect(MagiaEffects.POSSESSED)
                && !target.hasEffect(MagiaEffects.POSSESSING);
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity caster, MagicData data) {
        if (!(caster instanceof ServerPlayer)) return false;
        if (SoulRealm.active(caster)) return SoulRealm.empowered(caster);
        return !Binding.isBound(caster) && aim(level, caster, data, RANGE, false, t -> eligible(caster, t));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource source, MagicData data) {
        if (level instanceof ServerLevel server && entity instanceof ServerPlayer caster && data != null
                && !data.getPlayerRecasts().hasRecastForSpell(getSpellId())
                && target(server, caster, data) instanceof ServerPlayer target && eligible(caster, target)
                && target.level() == caster.level() && caster.hasLineOfSight(target)
                && !Binding.isBound(caster) && target.distanceToSqr(caster) <= RANGE * RANGE) {
            int ticks = DomainPassives.duration(caster, MagiaConfig.SOUL_DURATION.get());
            if (SoulRealm.start(caster, target, ticks)) data.getPlayerRecasts().addRecast(
                    new RecastInstance(getSpellId(), spellLevel, 2, ticks, source, null), data);
        }
        super.onCast(level, spellLevel, entity, source, data);
    }

    @Override
    public void onRecastFinished(ServerPlayer player, RecastInstance instance, RecastResult result,
                                 ICastDataSerializable castData) {
        super.onRecastFinished(player, instance, result, castData);
        SoulRealm.end(player);
    }
}
