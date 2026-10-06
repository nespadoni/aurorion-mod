package com.aurorion.magia.spell;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.damage.DamageSources;
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
 * <p>O ponto onde o alvo foi atingido vira a ancora de uma corrente, e ela <b>nao tem prazo</b>: a
 * primeira conjuracao prende, a segunda no mesmo alvo solta. Toda a regra da corrente esta em
 * {@link Binding}; aqui so a mira, o raio e a alternancia.
 *
 * <p>Prender nao abre cooldown, soltar abre ({@link ToggleCooldown}): quem acabou de prender
 * alguem consegue soltar na hora, e nao consegue prender e soltar em sequencia sem pagar.
 */
public final class VinculumCarnificisSpell extends AurorionSpell {
    private static final int RANGE = 16;

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
                Component.translatable("ui.aurorion_magia.raio_corrente", (int) radius(spellLevel)),
                Component.translatable("ui.aurorion_magia.sem_prazo"),
                Component.translatable("ui.aurorion_magia.alternar"));
    }

    /**
     * Prender e so em quem nao e aliado; soltar vale em qualquer preso — inclusive o aliado que outro
     * prendeu, que e o jeito de um companheiro abrir a corrente.
     */
    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return aim(level, entity, playerMagicData, RANGE, true,
                target -> Binding.isBound(target) || !FriendlyFire.spares(entity, target));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            LivingEntity target = target(serverLevel, entity, playerMagicData);
            if (target != null) {
                if (Binding.isBound(target)) {
                    Binding.unbind(target);
                } else {
                    Binding.bind(target, entity, radius(spellLevel));
                    ToggleCooldown.skipNext(entity);
                }
            }
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /** 12 blocos no nivel 1, +2 por nivel (20 no 5): da para andar pela sala, nao para sair dela. */
    private static float radius(int spellLevel) {
        return 12 + 2 * (spellLevel - 1);
    }
}
