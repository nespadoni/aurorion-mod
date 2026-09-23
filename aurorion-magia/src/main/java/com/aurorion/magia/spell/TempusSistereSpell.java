package com.aurorion.magia.spell;

import com.aurorion.magia.compat.FrozenLink;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Tempus Sistere — Tempo Suspenso. <b>Magia proibida.</b>
 *
 * <p>Tudo em volta para, menos quem conjurou. Criaturas e pessoas no raio ficam congeladas com o
 * mesmo freeze do {@code /freeze} (ver {@link FrozenLink}): nao andam, nao pulam, nao agacham, nao
 * batem, nao usam nada — so olham. Flechas e outros projeteis no ar perdem o impulso e caem.
 *
 * <table>
 *   <caption>Por nivel</caption>
 *   <tr><th>Nivel</th><th>Raio</th><th>Duracao</th></tr>
 *   <tr><td>1</td><td>6</td><td>5 s</td></tr>
 *   <tr><td>3</td><td>12</td><td>9 s</td></tr>
 *   <tr><td>5</td><td>18</td><td>13 s</td></tr>
 * </table>
 *
 * <p>Custo: UMA busca espacial no raio, no instante da conjuracao, com teto de {@value #MAX_TARGETS}
 * alvos. Depois disso so existe o efeito, que o vanilla ja tica em cada congelado.
 *
 * <p>Staff em criativo/espectador, chefes ({@code imune_deslocamento}) e o proprio conjurador ficam de
 * fora.
 */
public final class TempusSistereSpell extends AurorionSpell {
    private static final int MAX_TARGETS = 64;

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
        return Optional.of(SoundEvents.BEACON_POWER_SELECT);
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.AMETHYST_BLOCK_RESONATE);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.75f, 0.91f, 1.0f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(Component.translatable("ui.aurorion_magia.raio", radius(spellLevel)),
                Component.translatable("ui.aurorion_magia.duracao", Utils.timeFromTicks(duration(spellLevel), 1)),
                Component.translatable("ui.aurorion_magia.proibida"));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) stopTime(serverLevel, entity, spellLevel);
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private static void stopTime(ServerLevel level, LivingEntity caster, int spellLevel) {
        int radius = radius(spellLevel);
        int duration = duration(spellLevel);
        double radiusSqr = radius * radius;
        AABB box = caster.getBoundingBox().inflate(radius);

        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, box,
                target -> target != caster && target.isAlive() && !target.isSpectator()
                        && !(target instanceof Player player && player.isCreative())
                        && !Displacement.isImmune(target)
                        && target.distanceToSqr(caster) <= radiusSqr);
        victims.sort(Comparator.comparingDouble(target -> target.distanceToSqr(caster)));
        for (LivingEntity victim : victims.subList(0, Math.min(MAX_TARGETS, victims.size()))) {
            FrozenLink.freeze(victim, duration, caster);
        }

        // O que estava no ar para no ar: flechas, tridentes, bolas de fogo perdem o impulso.
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, box,
                projectile -> projectile.distanceToSqr(caster) <= radiusSqr)) {
            launch(projectile, Vec3.ZERO);
        }

        Vec3 at = caster.position();
        sound(level, at, SoundEvents.BELL_RESONATE, 2.0f, 0.5f);
        sound(level, at, SoundEvents.GLASS_BREAK, 1.0f, 0.4f);
        MagiaNetwork.sendVisualAt(level, caster, SpellVisualPayload.Kind.TEMPUS_SISTERE, duration, at, radius);
    }

    /** 6 blocos no nivel 1, +3 por nivel (18 no 5). */
    private static int radius(int spellLevel) {
        return 6 + 3 * (spellLevel - 1);
    }

    /** 5 s no nivel 1, +2 s por nivel (13 s no 5). */
    private static int duration(int spellLevel) {
        return 100 + 40 * (spellLevel - 1);
    }
}
