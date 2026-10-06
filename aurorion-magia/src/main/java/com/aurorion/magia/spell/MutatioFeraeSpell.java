package com.aurorion.magia.spell;

import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import com.aurorion.magia.registry.MagiaEffects;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
 * Mutatio Ferae — Capricho. O polimorfo da Lulu: o alvo vira um <b>guaxinim</b> (o do Alex's Mobs) por
 * alguns segundos. Anda devagar, nao ataca, nao usa item e nao conjura — mas continua sendo ele: a
 * vida, a hitbox e o nome sao os mesmos.
 *
 * <p>O servidor manda no efeito {@code polimorfo}; quem desenha o guaxinim no lugar do corpo e cada
 * cliente, pelo visual {@code MUTATIO_FERAE} preso ao alvo ({@code PolymorphRender}). Sem o Alex's
 * Mobs no pack, vira raposa.
 */
public final class MutatioFeraeSpell extends AurorionSpell {
    private static final int RANGE = 16;

    public MutatioFeraeSpell() {
        super("mutatio_ferae", SchoolRegistry.NATURE_RESOURCE, SpellRarity.EPIC, 3, 30, CastType.INSTANT);
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 0;
        this.baseManaCost = 50;
        this.manaCostPerLevel = 10;
        this.castTime = 0;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.ILLUSIONER_MIRROR_MOVE);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.65f, 0.85f, 0.35f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.vira_guaxinim", Utils.timeFromTicks(duration(spellLevel), 1)),
                Component.translatable("ui.aurorion_magia.alcance", RANGE),
                Component.translatable("ui.aurorion_magia.maos_atadas"));
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return aim(level, entity, playerMagicData, RANGE, false, target -> Hits.pvpAllowed(entity, target));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel) {
            LivingEntity target = target((ServerLevel) level, entity, playerMagicData);
            if (target != null) transform(entity, target, duration(spellLevel));
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private static void transform(LivingEntity caster, LivingEntity target, int ticks) {
        target.addEffect(new MobEffectInstance(MagiaEffects.POLYMORPH, ticks, 0, false, false, true), caster);
        if (target instanceof ServerPlayer player) {
            if (MagicData.getPlayerMagicData(player).isCasting()) Utils.serverSideCancelCast(player);
            player.stopUsingItem();
        } else if (target instanceof Mob mob) {
            mob.setTarget(null);
        }
        sound(target, SoundEvents.FOX_SCREECH, 1.0f, 1.4f);
        sound(target, SoundEvents.PUFFER_FISH_BLOW_UP, 1.0f, 1.2f);
        MagiaNetwork.sendVisual(caster, target, SpellVisualPayload.Kind.MUTATIO_FERAE, ticks);
    }

    /** 2,5 s no nivel 1, +0,75 s por nivel. */
    private static int duration(int spellLevel) {
        return 50 + 15 * (spellLevel - 1);
    }
}
