package com.aurorion.magia.spell;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import com.aurorion.magia.registry.MagiaEffects;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.TargetEntityCastData;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
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
 * Imperium Mentis: a vontade do alvo passa a ser a do conjurador.
 *
 * <ul>
 *   <li><b>Mob</b>: fica {@code dominado} e passa a lutar pelo conjurador — ver {@link Domination}.
 *       Tipos na tag {@code aurorion_magia:imune_dominacao} resistem.</li>
 *   <li><b>Jogador</b>: fica {@code desorientado}. O servidor manda no efeito (quando comeca,
 *       quanto dura) e bloqueia magia e uso de item. Os controles invertidos e o escurecimento sao
 *       aplicados pelo cliente do alvo, porque e o cliente quem le o teclado — ver o README sobre o
 *       que isso significa contra cliente modificado.</li>
 * </ul>
 *
 * <p>Conjuracao {@link CastType#LONG}: um segundo e meio concentrando, efeito unico no fim. Nenhum
 * custo depois disso alem do efeito, que o vanilla ja tica.
 *
 * <p>Escola padrao Eldritch (a mais proxima de "mente" no Iron's); ajustavel pelo config de magias do
 * Iron's.
 */
public final class ImperiumMentisSpell extends AbstractSpell {
    private static final int RANGE = 16;
    private static final float AIM_ASSIST = 0.3f;

    private final ResourceLocation spellId = AurorionMagia.id("imperium_mentis");
    private final DefaultConfig defaultConfig = new DefaultConfig()
            .setMinRarity(SpellRarity.EPIC)
            .setSchoolResource(SchoolRegistry.ELDRITCH_RESOURCE)
            .setMaxLevel(3)
            .setCooldownSeconds(45)
            .build();

    public ImperiumMentisSpell() {
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 1;
        this.baseManaCost = 60;
        this.manaCostPerLevel = 15;
        this.castTime = 30;
    }

    @Override
    public ResourceLocation getSpellResource() {
        return spellId;
    }

    @Override
    public DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public CastType getCastType() {
        return CastType.LONG;
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundEvents.EVOKER_PREPARE_WOLOLO);
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.ILLUSIONER_CAST_SPELL);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.6f, 0.95f, 0.78f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.dominio",
                        Utils.timeFromTicks(dominationTicks(spellLevel), 1)),
                Component.translatable("ui.aurorion_magia.desorientacao",
                        Utils.timeFromTicks(disorientationTicks(spellLevel), 1)));
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return Utils.preCastTargetHelper(level, entity, playerMagicData, this, RANGE, AIM_ASSIST, true,
                target -> target != entity && !DamageSources.isFriendlyFireBetween(entity, target));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            LivingEntity target = resolveTarget(serverLevel, entity, playerMagicData);
            if (target != null && target.isAlive()) {
                imperium(entity, target, spellLevel);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private void imperium(LivingEntity caster, LivingEntity target, int spellLevel) {
        int duration;
        if (target instanceof ServerPlayer player) {
            duration = disorientationTicks(spellLevel);
            player.addEffect(new MobEffectInstance(MagiaEffects.DISORIENTED, duration, 0, false, false, true), caster);
            // O bloqueio vale ja: conjuracao em andamento e item em uso (arco, escudo, comida) caem.
            if (MagicData.getPlayerMagicData(player).isCasting()) Utils.serverSideCancelCast(player);
            player.stopUsingItem();
        } else if (target instanceof Mob mob && Domination.canDominate(mob)) {
            duration = dominationTicks(spellLevel);
            Domination.dominate(mob, caster, duration);
        } else {
            // Imune: so o lampejo do impacto, para quem conjurou entender que a mente resistiu.
            duration = 10;
        }
        MagiaNetwork.sendVisual(caster, target, SpellVisualPayload.Kind.IMPERIUM_AURA, duration);
    }

    /** 8 s no nivel 1, +4 s por nivel. */
    private static int dominationTicks(int spellLevel) {
        return 160 + 80 * (spellLevel - 1);
    }

    /** 4 s no nivel 1, +1 s por nivel. Curto de proposito: contra jogador, perder o controle irrita rapido. */
    private static int disorientationTicks(int spellLevel) {
        return 80 + 20 * (spellLevel - 1);
    }

    @Nullable
    private static LivingEntity resolveTarget(ServerLevel level, LivingEntity caster, @Nullable MagicData magicData) {
        if (magicData != null && magicData.getAdditionalCastData() instanceof TargetEntityCastData data) {
            return data.getTarget(level);
        }
        return caster instanceof Mob mob ? mob.getTarget() : null;
    }
}
