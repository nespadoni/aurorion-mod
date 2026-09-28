package com.aurorion.magia.spell;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
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
 * Tempus Sistere — Tempo Suspenso. <b>Magia proibida.</b>
 *
 * <p>Uma sombra se abre no chao e o tempo para dentro dela: pessoas e criaturas ficam congeladas com
 * o mesmo freeze do {@code /freeze} (ver {@code FrozenLink}) — nao andam, nao pulam, nao batem, nao
 * usam nada, so olham. Flechas que entram voando perdem o impulso. So quem conjurou se move.
 *
 * <p><b>Ativavel</b>: a primeira conjuracao para o tempo, e ele fica parado ate a segunda. O raio e o
 * de um salao inteiro:
 *
 * <table>
 *   <caption>Por nivel</caption>
 *   <tr><th>Nivel</th><th>Raio</th></tr>
 *   <tr><td>1</td><td>30</td></tr>
 *   <tr><td>3</td><td>35</td></tr>
 *   <tr><td>5</td><td>40</td></tr>
 * </table>
 *
 * <p>A zona, o relogio e o custo estao em {@link TimeStop}. Parar o tempo nao abre cooldown; devolve-lo
 * abre ({@link ToggleCooldown}) — senao os 120 s de cooldown prenderiam o conjurador a propria magia.
 *
 * <p>Staff em criativo/espectador, chefes ({@code imune_deslocamento}) e o proprio conjurador ficam de
 * fora.
 */
public final class TempusSistereSpell extends AurorionSpell {
    public TempusSistereSpell() {
        super("tempus_sistere", SchoolRegistry.ENDER_RESOURCE, SpellRarity.LEGENDARY, 5, 120, CastType.LONG, true);
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 1;
        this.baseManaCost = 150;
        this.manaCostPerLevel = 30;
        this.castTime = 20;
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundEvents.RESPAWN_ANCHOR_CHARGE);
    }

    /** Os sons de parar e de devolver o tempo saem da zona ({@link TimeStop}), no centro dela. */
    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.empty();
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.32f, 0.18f, 0.5f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(Component.translatable("ui.aurorion_magia.raio", radius(spellLevel)),
                Component.translatable("ui.aurorion_magia.alternar_zona"),
                Component.translatable("ui.aurorion_magia.proibida"));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            if (TimeStop.isActive(entity)) {
                TimeStop.stop(entity);
            } else {
                TimeStop.start(serverLevel, entity, radius(spellLevel));
                ToggleCooldown.skipNext(entity);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /** 30 blocos no nivel 1 ate 40 no 5: o salao principal inteiro. */
    private static int radius(int spellLevel) {
        return 30 + 10 * (spellLevel - 1) / 4;
    }
}
