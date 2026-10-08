package com.aurorion.magia.spell;

import com.aurorion.magia.compat.AreasMagic;
import com.aurorion.magia.config.MagiaConfig;
import com.aurorion.magia.entity.MagiaProjectileEntity;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import com.aurorion.magia.registry.MagiaSpells;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Sphaera Spiritus — Esfera Espiritual (a Genki Dama). <b>Magia proibida.</b>
 *
 * <p>Segurando a conjuracao com os bracos erguidos, a esfera cresce sobre a cabeca de quem conjura —
 * todos em volta a veem crescer — por ate oito segundos, e quem conjura anda devagar enquanto isso. Ao
 * soltar, ela e arremessada devagar na direcao da mira e explode no primeiro toque.
 *
 * <p>A explosao e <b>totalmente destrutiva</b>: fere e arremessa todos no raio menos quem conjurou —
 * aliado tambem — e abre cratera no terreno ({@code esferaQuebraBlocos}). A cratera nao se abre onde
 * quem conjurou nao poderia construir (protecao de spawn), em area do {@code aurorion-areas} que
 * proiba magia, nem em bloco lacrado; mods de protecao que tratam explosao continuam valendo.
 *
 * <p>Raio e dano crescem com a carga. Solta antes de um segundo, a esfera se desfaz e a magia nao
 * entra em recarga.
 *
 * <h2>Custo</h2>
 *
 * <p>Carregando: um pacote de visual a cada meio segundo. Na explosao: uma busca de entidades no raio
 * e, com a cratera ligada, uma explosao do vanilla de forca ate {@value #MAX_EXPLOSION_POWER}, uma vez
 * a cada tres minutos.
 */
public final class SphaeraSpiritusSpell extends AurorionSpell {
    private static final int MIN_CHARGE_TICKS = 20;
    private static final int MAX_TARGETS = 48;
    /**
     * Forca maxima da explosao do vanilla que abre a cratera (TNT e 4). O custo de uma explosao cresce
     * com a forca — cada um dos ~1350 raios anda mais passos — e a cratera cresce com o cubo dela; 6 ja
     * e um buraco de umas cinco TNTs, uma vez a cada tres minutos.
     */
    private static final float MAX_EXPLOSION_POWER = 6;
    private static final double SPEED = 0.75;

    public SphaeraSpiritusSpell() {
        super("sphaera_spiritus", SchoolRegistry.HOLY_RESOURCE, SpellRarity.LEGENDARY, 3, 180, CastType.CONTINUOUS, true);
        this.baseSpellPower = 20;
        this.spellPowerPerLevel = 10;
        this.baseManaCost = 15;
        this.manaCostPerLevel = 5;
        // Em magia continua, castTime e a carga maxima: 8 segundos.
        this.castTime = 320;
    }

    @Override
    public AnimationHolder getCastStartAnimation() {
        return SpellAnimations.ANIMATION_CONTINUOUS_OVERHEAD;
    }

    // Sem getCastFinishAnimation: ver o aviso no DolorCruciatusSpell.

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundEvents.BEACON_ACTIVATE);
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.empty();
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.55f, 0.85f, 1.0f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.dano_maximo", Utils.stringTruncation(damagePower(spellLevel, caster) * 2, 1)),
                Component.translatable("ui.aurorion_magia.raio_maximo", maxRadius(spellLevel)),
                Component.translatable("ui.aurorion_magia.carga_maxima", Utils.timeFromTicks(castTime, 1)),
                Component.translatable(MagiaConfig.SPHAERA_BREAKS_BLOCKS.get()
                        ? "ui.aurorion_magia.abre_cratera" : "ui.aurorion_magia.sem_cratera"),
                Component.translatable("ui.aurorion_magia.proibida"));
    }

    /** Cada pulso da carga: o visual cresce e quem conjura fica pesado. */
    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel && playerMagicData != null && playerMagicData.isCasting()) {
            float charge = charge(playerMagicData);
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, 3, false, false, false));
            MagiaNetwork.sendVisual(entity, entity, SpellVisualPayload.Kind.SPHAERA_CHARGE, 15, entity.position(),
                    visualRadius(charge));
            sound(entity, SoundEvents.BEACON_AMBIENT, 1.4f, 0.6f + charge);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /** Soltou (ou a carga encheu): a esfera vai. */
    @Override
    public void onServerCastComplete(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData,
                                     boolean cancelled) {
        // Lido antes do super, que zera o estado da conjuracao.
        int charged = playerMagicData == null ? castTime
                : playerMagicData.getCastDuration() - playerMagicData.getCastDurationRemaining();
        float charge = playerMagicData == null ? 1 : charge(playerMagicData);
        super.onServerCastComplete(level, spellLevel, entity, playerMagicData, cancelled);
        if (!(level instanceof ServerLevel serverLevel)) return;

        MagiaNetwork.sendVisual(entity, entity, SpellVisualPayload.Kind.SPHAERA_CHARGE, 0, entity.position(), 0);
        // Interrompida (estase, possessao, silencio, morte): a esfera se desfaz, e a recarga fica — so
        // quem solta o botao por conta propria arremessa.
        if (castBlocked(entity)) {
            sound(entity, SoundEvents.FIRE_EXTINGUISH, 1.0f, 1.2f);
            return;
        }
        if (charged < MIN_CHARGE_TICKS) {
            fizzle(entity);
            return;
        }
        float radius = 3 + (maxRadius(spellLevel) - 3) * charge;
        float damage = damagePower(spellLevel, entity) * (0.5f + 1.5f * charge);
        float size = visualRadius(charge);
        Vec3 from = entity.getEyePosition().add(0, 1.2 + size, 0);
        MagiaProjectileEntity.launch(serverLevel, entity, MagiaProjectileEntity.Shape.SPHAERA, from,
                entity.getLookAngle().scale(SPEED), size, damage, radius, 200);
        sound(entity, SoundEvents.WITHER_SHOOT, 2.0f, 0.5f);
        sound(entity, SoundEvents.BEACON_DEACTIVATE, 1.5f, 0.7f);
    }

    /** Soltou cedo demais: nada sai, e a recarga que o Iron's acabou de por e devolvida. */
    private void fizzle(LivingEntity caster) {
        sound(caster, SoundEvents.FIRE_EXTINGUISH, 1.0f, 1.2f);
        if (!(caster instanceof ServerPlayer player)) return;
        MagicData data = MagicData.getPlayerMagicData(player);
        if (data.getPlayerCooldowns().removeCooldown(getSpellId())) data.getPlayerCooldowns().syncToPlayer(player);
        player.displayClientMessage(Component.translatable("aurorion_magia.esfera_desfeita")
                .withStyle(ChatFormatting.AQUA), true);
    }

    /** A esfera tocou em algo. */
    public static void detonate(ServerLevel level, LivingEntity caster, Vec3 at, double radius, float damage) {
        for (LivingEntity victim : Hits.around(level, caster, at, radius, MAX_TARGETS, target -> true)) {
            double distance = Math.sqrt(victim.position().distanceToSqr(at));
            float scale = (float) (1 - 0.5 * Math.min(1, distance / radius));
            DamageSources.ignoreNextKnockback(victim);
            FriendlyFire.applyDamage(victim, damage * scale, MagiaSpells.SPHAERA_SPIRITUS.get().getDamageSource(caster));
            if (!victim.isAlive() || Displacement.isImmune(victim)) continue;
            Vec3 outward = victim.position().subtract(at);
            if (outward.lengthSqr() < 0.01) outward = new Vec3(0, 1, 0);
            double force = 0.6 + 1.4 * (1 - Math.min(1, distance / radius));
            launch(victim, outward.normalize().scale(force).add(0, 0.5, 0));
            victim.resetFallDistance();
        }
        if (MagiaConfig.SPHAERA_BREAKS_BLOCKS.get() && AreasMagic.allowedAt(level, at, caster.getUUID())) {
            // BLOCK, e nao TNT: segue blockExplosionDropDecay (ligado por padrao), entao a cratera nao
            // despeja milhares de itens no chao da cidade.
            level.explode(caster, null, new Crater(caster), at.x, at.y, at.z,
                    Math.min(MAX_EXPLOSION_POWER, (float) radius * 0.5f), false, Level.ExplosionInteraction.BLOCK);
        }
        sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 6.0f, 0.5f);
        sound(level, at, SoundEvents.LIGHTNING_BOLT_THUNDER, 4.0f, 0.7f);
        MagiaNetwork.sendVisualAt(level, caster, SpellVisualPayload.Kind.SPHAERA_BLAST, 50, at, (float) radius);
    }

    /**
     * A cratera: quebra blocos como TNT, mas nao fere nem empurra ninguem — isso a esfera ja fez, com
     * o dano proprio dela — e nao quebra onde quem conjurou nao poderia construir.
     */
    private static final class Crater extends ExplosionDamageCalculator {
        private final Entity caster;

        Crater(Entity caster) {
            this.caster = caster;
        }

        @Override
        public boolean shouldBlockExplode(Explosion explosion, BlockGetter reader, BlockPos pos, BlockState state, float power) {
            if (!super.shouldBlockExplode(explosion, reader, pos, state, power)) return false;
            return !(caster instanceof ServerPlayer player) || player.serverLevel().mayInteract(player, pos);
        }

        @Override
        public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
            return false;
        }

        @Override
        public float getKnockbackMultiplier(Entity entity) {
            return 0;
        }
    }

    private float charge(MagicData data) {
        int duration = Math.max(1, data.getCastDuration());
        return Mth.clamp((duration - data.getCastDurationRemaining()) / (float) duration, 0, 1);
    }

    /** O raio que a esfera mostra: meio bloco no comeco, tres com a carga cheia. */
    private static float visualRadius(float charge) {
        return 0.5f + 2.5f * charge;
    }

    /** 10 blocos no nivel 1, +2 por nivel. */
    private static int maxRadius(int spellLevel) {
        return 8 + 2 * spellLevel;
    }
}
