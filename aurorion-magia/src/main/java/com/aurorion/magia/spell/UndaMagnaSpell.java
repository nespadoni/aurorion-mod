package com.aurorion.magia.spell;

import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Unda Magna — <b>Maremoto</b>. Uma parede de agua abre de dentro de quem conjurou e joga tudo para
 * longe.
 *
 * <p>Nao tem mira: ela e a magia de <i>abrir espaço</i>. Cercado numa viela, no meio de uma multidao
 * ou prensado contra a muralha, a onda tira todo mundo de cima de voce ao mesmo tempo e ainda apaga o
 * fogo de quem estiver queimando.
 *
 * <p>Sem agachar, a onda sai <b>na direcao em que voce olha</b>, num arco de 120° a frente: e o
 * empurrao de quem esta abrindo caminho. <b>Agachado</b>, ela sai em circulo, para todos os lados —
 * quem esta cercado nao tem um lado para escolher.
 *
 * <p>O empurrao e forte e o dano e pequeno de proposito: quem joga alguem de um penhasco com esta
 * magia matou pela queda, e nao pela agua. Projeteis em voo tambem sao varridos.
 */
public final class UndaMagnaSpell extends AurorionSpell {
    private static final int MAX_TARGETS = 24;
    /** Cosseno de 60°: o limite do arco de 120° a frente, na versao em pe. */
    private static final double FORWARD_CONE = 0.5;

    public UndaMagnaSpell() {
        super("unda_magna", SchoolRegistry.ICE_RESOURCE, SpellRarity.COMMON, 5, 16, CastType.INSTANT);
        this.baseSpellPower = 2;
        this.spellPowerPerLevel = 1;
        this.baseManaCost = 30;
        this.manaCostPerLevel = 6;
        this.castTime = 0;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.empty();
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.3f, 0.7f, 0.95f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.raio", radius(spellLevel)),
                Component.translatable("ui.aurorion_magia.dano_impacto",
                        Utils.stringTruncation(getSpellPower(spellLevel, caster), 1)),
                Component.translatable("ui.aurorion_magia.arco_frontal"),
                Component.translatable("ui.aurorion_magia.agachado_circulo"));
    }

    /** Onda nao mira: ela sempre pode ser conjurada, mesmo com ninguem em volta. */
    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return true;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) wave(serverLevel, entity, spellLevel);
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private void wave(ServerLevel level, LivingEntity caster, int spellLevel) {
        double radius = radius(spellLevel);
        boolean circle = AreaCast.wide(caster);
        Vec3 center = caster.position();
        Vec3 look = caster.getLookAngle().multiply(1, 0, 1).normalize();

        for (LivingEntity victim : AreaCast.victims(level, caster, center, radius, MAX_TARGETS,
                target -> circle || inFront(center, look, target))) {
            push(caster, victim, center, spellLevel, radius);
        }
        // Flecha, tridente e bola de fogo no ar tambem sao varridos: a onda nao escolhe o que e vivo.
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class,
                new AABB(center, center).inflate(radius),
                shot -> shot.getOwner() != caster && (circle || inFront(center, look, shot)))) {
            Vec3 away = away(center, projectile).scale(1.4);
            launch(projectile, away.add(0, 0.3, 0));
        }

        sound(level, center, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 1.6f, 0.55f);
        sound(level, center, SoundEvents.GENERIC_EXPLODE.value(), 0.7f, 1.6f);
        MagiaNetwork.sendVisualAt(level, caster, SpellVisualPayload.Kind.UNDA_MAGNA, 30, center,
                (float) (circle ? radius : -radius));
    }

    private void push(LivingEntity caster, LivingEntity victim, Vec3 center, int spellLevel, double radius) {
        Vec3 away = away(center, victim);
        // Perto do centro a onda esta inteira; na borda ela ja se abriu e empurra menos.
        double distance = Math.sqrt(victim.position().distanceToSqr(center));
        double force = (1.15 + 0.15 * spellLevel) * (1 - 0.45 * Math.min(1, distance / radius));
        launch(victim, new Vec3(away.x * force, 0.42 + 0.03 * spellLevel, away.z * force));
        victim.resetFallDistance();
        victim.clearFire();
        DamageSources.applyDamage(victim, getSpellPower(spellLevel, caster), getDamageSource(caster));
        sound(victim, SoundEvents.PLAYER_SPLASH, 0.9f, 0.9f);
    }

    /** Direcao horizontal do centro para o alvo; quem esta exatamente no centro vai para frente. */
    private static Vec3 away(Vec3 center, Entity victim) {
        Vec3 delta = victim.position().subtract(center).multiply(1, 0, 1);
        return delta.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : delta.normalize();
    }

    private static boolean inFront(Vec3 center, Vec3 look, Entity target) {
        Vec3 delta = target.position().subtract(center).multiply(1, 0, 1);
        return delta.lengthSqr() < 1.0E-4 || delta.normalize().dot(look) >= FORWARD_CONE;
    }

    /** 7 blocos no nivel 1, +1,5 por nivel (13 no 5). */
    private static int radius(int spellLevel) {
        return (int) Math.round(7 + 1.5 * (spellLevel - 1));
    }
}
