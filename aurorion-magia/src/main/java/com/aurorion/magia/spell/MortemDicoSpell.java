package com.aurorion.magia.spell;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Mortem Dico — Sentenca Final. <b>Magia proibida.</b> "Eu declaro a morte."
 *
 * <p>Mata o alvo na hora, como {@code /kill}: ignora armadura, resistencia, encantamento, escudo,
 * invulnerabilidade de criativo e totem. O dano sai com o tipo proprio
 * {@code aurorion_magia:mortem_dico} (nas tags {@code bypasses_*} do vanilla), que registra quem
 * conjurou na mensagem de morte. Se algum mod ainda segurar a entidade viva, cai no {@code kill()}
 * de verdade.
 *
 * <p>Morte comum para todos os efeitos: conta vida no {@code aurorion-vidas}, dropa inventario pelas
 * regras normais. Sem nivel — nao tem como matar "mais".
 */
public final class MortemDicoSpell extends AurorionSpell {
    public static final ResourceKey<DamageType> DAMAGE_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE, AurorionMagia.id("mortem_dico"));
    private static final int RANGE = 32;

    public MortemDicoSpell() {
        super("mortem_dico", SchoolRegistry.ELDRITCH_RESOURCE, SpellRarity.LEGENDARY, 1, 90, CastType.INSTANT, true);
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 0;
        this.baseManaCost = 200;
        this.manaCostPerLevel = 0;
        this.castTime = 0;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.WITHER_SHOOT);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.23f, 1.0f, 0.42f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(Component.translatable("ui.aurorion_magia.morte_instantanea"),
                Component.translatable("ui.aurorion_magia.alcance", RANGE),
                Component.translatable("ui.aurorion_magia.proibida"));
    }

    /** Aliado incluido: a sentenca nao escolhe lado. */
    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return aim(level, entity, playerMagicData, RANGE, true, target -> !target.isSpectator());
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            LivingEntity target = target(serverLevel, entity, playerMagicData);
            if (target != null) sentence(serverLevel, entity, target);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private static void sentence(ServerLevel level, LivingEntity caster, LivingEntity target) {
        Vec3 feet = target.position();
        float height = target.getBbHeight();

        DamageSource death = new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE_TYPE), caster);
        target.hurt(death, Float.MAX_VALUE);
        if (target.isAlive()) target.kill();

        sound(level, feet, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6f, 1.6f);
        sound(level, feet, SoundEvents.SOUL_ESCAPE.value(), 2.0f, 0.5f);
        // Preso ao ponto, nao ao alvo: o corpo some, o selo fica.
        MagiaNetwork.sendVisualAt(level, caster, SpellVisualPayload.Kind.MORTEM_DICO, 40, feet, height);
    }
}
