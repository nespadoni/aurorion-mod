package com.aurorion.magia.spell;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.entity.MagiaProjectileEntity;
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
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Incus Caelestis — Bigorna Celeste. Voce escolhe alguem, e uma bigorna cai do ceu na cabeca dele.
 *
 * <p>A bigorna mira <b>o lugar</b> onde o alvo estava no instante da conjuracao, nao o alvo: um
 * circulo de aviso aparece no chao e ela leva cerca de um segundo para cair. Quem ficar parado debaixo
 * dela morre; quem andar, escapa, e ela cai no chao, fica ali um instante e some. Outro inimigo
 * debaixo dela tambem e esmagado; aliado de quem conjurou nao, e chefe leva um golpe pesado, mas nao
 * letal.
 *
 * <p>O golpe ignora armadura, encantamento e resistencia, mas nao a invulnerabilidade: totem salva, e
 * criativo nao sente nada. A bigorna <b>nunca vira bloco</b> — nao da para pega-la, nem quebrar a
 * construcao de ninguem com ela. Se houver teto em cima do alvo, ela nasce logo abaixo dele.
 */
public final class IncusCaelestisSpell extends AurorionSpell {
    public static final ResourceKey<DamageType> DAMAGE_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE, AurorionMagia.id("incus_caelestis"));
    private static final int RANGE = 24;
    private static final double DROP_HEIGHT = 22;
    /** O golpe: alto o bastante para nenhuma vida de jogador ou criatura comum aguentar. */
    private static final float CRUSH = 1000;
    /** O que a bigorna tira de um chefe. */
    private static final float BOSS_CRUSH = 30;

    public IncusCaelestisSpell() {
        super("incus_caelestis", SchoolRegistry.EVOCATION_RESOURCE, SpellRarity.RARE, 3, 30, CastType.INSTANT);
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 0;
        this.baseManaCost = 50;
        this.manaCostPerLevel = 10;
        this.castTime = 0;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.ANVIL_USE);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.55f, 0.55f, 0.6f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(Component.translatable("ui.aurorion_magia.letal_embaixo"),
                Component.translatable("ui.aurorion_magia.alcance", RANGE),
                Component.translatable("ui.aurorion_magia.desvia_andando"));
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return aim(level, entity, playerMagicData, RANGE, false, target -> Hits.pvpAllowed(entity, target));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            LivingEntity target = target(serverLevel, entity, playerMagicData);
            if (target != null) drop(serverLevel, entity, target.position());
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private static void drop(ServerLevel level, LivingEntity caster, Vec3 spot) {
        Vec3 sky = spot.add(0, DROP_HEIGHT, 0);
        BlockHitResult ceiling = level.clip(new ClipContext(spot.add(0, 2.2, 0), sky,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        Vec3 top = ceiling.getType() == HitResult.Type.MISS ? sky
                : new Vec3(spot.x, Math.max(spot.y + 3, ceiling.getLocation().y - 1.2), spot.z);
        // Centraliza a bigorna (caixa de 0,6) sobre o ponto, e nao com o canto nele.
        MagiaProjectileEntity.launch(level, caster, MagiaProjectileEntity.Shape.INCUS, top.subtract(0, 0.3, 0),
                new Vec3(0, -0.2, 0), 1.0f, CRUSH, 0, 120);
        sound(level, spot, SoundEvents.ANVIL_PLACE, 0.8f, 0.5f);
        MagiaNetwork.sendVisualAt(level, caster, SpellVisualPayload.Kind.INCUS_SHADOW, fallTicks(top.y - spot.y) + 6, spot, 0.9f);
    }

    /**
     * A bigorna desceu em cima de alguem. Aliado de quem conjurou passa ileso; chefe
     * ({@code imune_deslocamento}) leva so {@value #BOSS_CRUSH} — uma magia rara com meio minuto de
     * recarga nao derruba um Warden ou um Wither de uma vez.
     */
    public static void crush(ServerLevel level, @Nullable LivingEntity caster, LivingEntity victim, float damage) {
        if (caster != null && !Hits.enemy(caster, victim)) return;
        float dealt = Displacement.isImmune(victim) ? Math.min(damage, BOSS_CRUSH) : damage;
        victim.hurt(caster != null ? FriendlyFire.source(Execution.source(level, caster, DAMAGE_TYPE), victim)
                : level.damageSources().anvil(null), dealt);
        sound(victim, SoundEvents.ANVIL_LAND, 1.8f, 0.6f);
    }

    /** Ticks para cair {@code height} blocos com a gravidade da bigorna, partindo de 0,2 por tick. */
    private static int fallTicks(double height) {
        double fallen = 0, speed = 0.2;
        int ticks = 0;
        while (fallen < height && ticks < 120) {
            speed += 0.08;
            fallen += speed;
            ticks++;
        }
        return ticks;
    }
}
