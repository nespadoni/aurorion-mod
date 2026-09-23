package com.aurorion.magia.spell;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Vinculum Carnificis — Vinculo do Carrasco. "Voce ainda nao recebeu permissao para sair."
 *
 * <p>O ponto onde o alvo foi atingido vira a ancora de uma corrente de {@value #RADIUS} blocos. Toda a
 * regra esta em {@link Binding}; aqui so a mira e a duracao.
 */
public final class VinculumCarnificisSpell extends AurorionSpell {
    private static final int RANGE = 16;
    private static final float RADIUS = 6;

    public VinculumCarnificisSpell() {
        super("vinculum_carnificis", SchoolRegistry.BLOOD_RESOURCE, SpellRarity.RARE, 5, 40, CastType.INSTANT);
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 1;
        this.baseManaCost = 45;
        this.manaCostPerLevel = 10;
        this.castTime = 0;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.CHAIN_PLACE);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.55f, 0.06f, 0.1f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.raio_corrente", (int) RADIUS),
                Component.translatable("ui.aurorion_magia.duracao", Utils.timeFromTicks(duration(spellLevel), 1)));
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return aim(level, entity, playerMagicData, RANGE, false, target -> true);
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            LivingEntity target = target(serverLevel, entity, playerMagicData);
            if (target != null) Binding.bind(target, entity, RADIUS, duration(spellLevel));
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /** 10 s no nivel 1, +2 s por nivel. */
    private static int duration(int spellLevel) {
        return 200 + 40 * (spellLevel - 1);
    }
}
