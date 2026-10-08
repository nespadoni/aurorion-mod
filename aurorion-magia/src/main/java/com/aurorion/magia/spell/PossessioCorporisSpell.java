package com.aurorion.magia.spell;

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
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Possessio Corporis — Possessao. <b>Magia proibida.</b> Depois de dois segundos de concentracao, quem
 * conjura entra no corpo de outro jogador e o conduz: anda, olha e fala por ele, enquanto o dono do
 * corpo assiste sem conseguir fazer nada alem de falar. Ver {@link Possession}.
 *
 * <p>Conjurar de novo (sem concentracao) devolve o corpo antes do tempo. So pega jogador: criatura nao
 * tem chat para alguem falar por ela.
 */
public final class PossessioCorporisSpell extends AurorionSpell {
    private static final int RANGE = 12;

    public PossessioCorporisSpell() {
        super("possessio_corporis", SchoolRegistry.ELDRITCH_RESOURCE, SpellRarity.LEGENDARY, 3, 120, CastType.LONG, true);
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 0;
        this.baseManaCost = 150;
        this.manaCostPerLevel = 30;
        this.castTime = 40;
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.CAST_T_POSE;
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundEvents.SCULK_SHRIEKER_SHRIEK);
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.SOUL_ESCAPE.value());
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.35f, 0.05f, 0.45f);
    }

    @Override
    public int getRecastCount(int spellLevel, @Nullable LivingEntity entity) {
        return 2;
    }

    /** A segunda conjuracao, que devolve o corpo, nao pede concentracao. */
    @Override
    public int getEffectiveCastTime(int spellLevel, @Nullable LivingEntity entity) {
        return entity != null && Possession.isPossessing(entity) ? 0 : super.getEffectiveCastTime(spellLevel, entity);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.possessao", Utils.timeFromTicks(duration(spellLevel), 1)),
                Component.translatable("ui.aurorion_magia.alcance", RANGE),
                Component.translatable("ui.aurorion_magia.fala_pelo_corpo"),
                Component.translatable("ui.aurorion_magia.reconjurar_solta"),
                Component.translatable("ui.aurorion_magia.proibida"));
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        if (Possession.isPossessing(entity)) return true;
        return entity instanceof ServerPlayer && aim(level, entity, playerMagicData, RANGE, false,
                target -> target instanceof ServerPlayer player && possessable(entity, player));
    }

    /**
     * Pode ser possuido agora: jogador fora do criativo, alcancavel ({@link Hits#hittable}: vivo,
     * nao inalvejavel, em lugar com PvP), sem ninguem dentro dele e sem estar dentro de ninguem — e quem
     * conjura tambem nao pode estar possuido.
     */
    private static boolean possessable(LivingEntity caster, ServerPlayer target) {
        return Hits.hittable(caster, target)
                && !target.hasEffect(MagiaEffects.POSSESSED) && !Possession.isPossessing(target)
                && !target.hasEffect(MagiaEffects.STASIS)
                && !caster.hasEffect(MagiaEffects.POSSESSED);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel && entity instanceof ServerPlayer caster && playerMagicData != null
                && !playerMagicData.getPlayerRecasts().hasRecastForSpell(getSpellId())) {
            // A mira foi conferida dois segundos atras. Nesse tempo outro possessor pode ter chegado
            // primeiro, o alvo pode ter fugido, entrado em area segura ou no criativo — confere de novo.
            if (target(serverLevel, caster, playerMagicData) instanceof ServerPlayer target
                    && possessable(caster, target) && target.level() == caster.level()
                    && target.distanceToSqr(caster) <= (RANGE + 4) * (RANGE + 4)) {
                int duration = duration(spellLevel);
                Possession.start(caster, target, duration);
                playerMagicData.getPlayerRecasts().addRecast(new RecastInstance(getSpellId(), spellLevel, 2, duration,
                        castSource, null), playerMagicData);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /** Segunda conjuracao, prazo, morte: o corpo e devolvido. */
    @Override
    public void onRecastFinished(ServerPlayer player, RecastInstance recastInstance, RecastResult recastResult,
                                 ICastDataSerializable castData) {
        super.onRecastFinished(player, recastInstance, recastResult, castData);
        Possession.end(player);
    }

    /** Base: 15 s no nivel 1, +5 s por nivel. SpellBalance dobra este tempo. */
    private static int duration(int spellLevel) {
        return SpellBalance.duration(300 + 100 * (spellLevel - 1));
    }
}
