package com.aurorion.magia.spell;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.entity.SpellZoneEntity;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import com.aurorion.magia.registry.MagiaEffects;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manus Abyssi — Garra do Abismo. A ultimate do Malzahar (Nether Grasp), traduzida para blocos.
 *
 * <h2>O que faz</h2>
 *
 * <ul>
 *   <li>Prende uma corrente de vazio no inimigo mirado ({@value #RANGE} blocos — os 700 do LoL) e o
 *       derruba: ele desmonta, para no lugar e fica <b>suprimido</b> — nao anda, nao pula, nao usa item e
 *       nao conjura.</li>
 *   <li>Quem conjura canaliza por ate 2,5 s, parado. A cada 0,25 s o alvo leva dano de magia; os dois
 *       ficam <b>revelados</b> (brilho atraves das paredes).</li>
 *   <li>A canalizacao continua enquanto a corrente nao partir — {@value #TETHER} blocos (1250) —, mesmo
 *       que a supressao seja tirada do alvo. Parte se quem conjura for controlado (silencio, estase,
 *       atordoamento das outras magias) ou morrer.</li>
 *   <li>No ponto onde o alvo estava abre-se a <b>Zona Nula</b>, por 5 s: a cada 0,5 s, quem estiver
 *       dentro leva 10/15/20% da vida maxima no total (+2,5% a cada +10% de poder de magia), com teto de
 *       {@value #CREATURE_CAP} por pulso em criaturas. Ela continua mesmo se a canalizacao for cortada.</li>
 * </ul>
 *
 * <p>Recarga por nivel: 140 / 110 / 80 s (as reducoes de recarga do Iron's continuam valendo). Mana 100.
 *
 * <h2>Custo</h2>
 *
 * <p>O relogio da canalizacao e o efeito {@code garra_abissal} em quem conjura: por tick, uma distancia;
 * a cada 5 ticks, um dano e a renovacao da supressao. A Zona Nula e a {@code SpellZoneEntity} de sempre,
 * que procura vitimas a cada 10 ticks.
 */
public final class ManusAbyssiSpell extends AurorionSpell {
    public static final int RANGE = 10;
    public static final int TETHER = 16;
    private static final int CHANNEL_TICKS = 50;
    private static final int PULSE_TICKS = 5;
    private static final int PULSES = CHANNEL_TICKS / PULSE_TICKS;
    public static final int ZONE_TICKS = 100;
    public static final int ZONE_INTERVAL = 10;
    private static final float ZONE_RADIUS = 2.5F;
    public static final float CREATURE_CAP = 6F;
    private static final int[] COOLDOWN_SECONDS = {140, 110, 80};
    private static final float[] ZONE_PERCENT = {0.10F, 0.15F, 0.20F};

    private static final String KEY = AurorionMagia.MOD_ID + ":garra_abissal";
    /** Nivel da ultima conjuracao, para a recarga por nivel: o evento de recarga do Iron's nao o traz. */
    private static final Map<UUID, Integer> LAST_LEVEL = new ConcurrentHashMap<>();

    public ManusAbyssiSpell() {
        super("manus_abyssi", SchoolRegistry.ELDRITCH_RESOURCE, SpellRarity.EPIC, 3, COOLDOWN_SECONDS[0], CastType.INSTANT);
        this.baseSpellPower = 4;
        this.spellPowerPerLevel = 2;
        this.baseManaCost = 100;
        this.manaCostPerLevel = 0;
        this.castTime = 0;
    }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return SpellAnimations.ANIMATION_INSTANT_CAST;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.WARDEN_SONIC_CHARGE);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.45f, 0.1f, 0.75f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        int level = Math.max(1, Math.min(3, spellLevel));
        return List.of(
                Component.translatable("ui.aurorion_magia.dano", Utils.stringTruncation(damagePower(spellLevel, caster), 1)),
                Component.translatable("ui.aurorion_magia.alcance", RANGE),
                Component.translatable("ui.aurorion_magia.garra_corrente", TETHER),
                Component.translatable("ui.aurorion_magia.garra_zona",
                        Utils.stringTruncation(zonePercent(spellLevel, caster) * 100, 1)),
                Component.translatable("ui.aurorion_magia.garra_recarga", COOLDOWN_SECONDS[level - 1]));
    }

    private static boolean eligible(LivingEntity caster, LivingEntity target) {
        return Hits.hittable(caster, target) && Hits.enemy(caster, target) && !target.hasEffect(MagiaEffects.STASIS);
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return !entity.hasEffect(MagiaEffects.NETHER_GRASP)
                && aim(level, entity, playerMagicData, RANGE, false, target -> eligible(entity, target));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel server) {
            LivingEntity target = target(server, entity, playerMagicData);
            if (target != null && eligible(entity, target) && target.level() == entity.level()
                    && entity.distanceToSqr(target) <= (RANGE + 2) * (RANGE + 2) && entity.hasLineOfSight(target)) {
                start(server, entity, target, spellLevel);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private void start(ServerLevel level, LivingEntity caster, LivingEntity target, int spellLevel) {
        LAST_LEVEL.put(caster.getUUID(), Math.max(1, Math.min(3, spellLevel)));
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Target", target.getUUID());
        tag.putFloat("Pulse", damagePower(spellLevel, caster) / PULSES);
        tag.putInt("Age", 0);
        caster.getPersistentData().put(KEY, tag);

        caster.addEffect(new MobEffectInstance(MagiaEffects.NETHER_GRASP, CHANNEL_TICKS, 0, false, false, true));
        caster.addEffect(new MobEffectInstance(MobEffects.GLOWING, CHANNEL_TICKS, 0, false, false, false));

        // Derrubado: desce da montaria, perde o embalo e para o que estava fazendo.
        target.stopRiding();
        launch(target, new Vec3(0, Math.min(0, target.getDeltaMovement().y), 0));
        target.stopUsingItem();
        if (target instanceof Player player && MagicData.getPlayerMagicData(player).isCasting()) {
            Utils.serverSideCancelCast((ServerPlayer) player);
        }
        suppress(caster, target);

        SpellZoneEntity.create(level, caster, SpellZoneEntity.Shape.NULL_ZONE, target.position(), ZONE_RADIUS, 1.5F,
                ZONE_TICKS, zonePercent(spellLevel, caster), Vec3.ZERO);
        MagiaNetwork.sendVisual(caster, target, SpellVisualPayload.Kind.NETHER_GRASP, CHANNEL_TICKS);
        sound(target, SoundEvents.WARDEN_SONIC_BOOM, 0.8f, 0.6f);
        sound(caster, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.9f, 0.5f);
    }

    /** Um tick da canalizacao, pelo efeito {@code garra_abissal}. {@code false} parte a corrente. */
    public static boolean channel(LivingEntity caster) {
        CompoundTag tag = state(caster);
        if (tag == null || !caster.isAlive() || !(caster.level() instanceof ServerLevel level)) return false;
        Entity found = tag.hasUUID("Target") ? level.getEntity(tag.getUUID("Target")) : null;
        if (!(found instanceof LivingEntity target) || !target.isAlive() || target.level() != caster.level()
                || caster.distanceToSqr(target) > TETHER * TETHER || blockedChannel(caster)) {
            return false;
        }
        int age = tag.getInt("Age") + 1;
        tag.putInt("Age", age);
        if (age % PULSE_TICKS == 0) {
            DamageSources.ignoreNextKnockback(target);
            FriendlyFire.applyDamage(target, tag.getFloat("Pulse"), MagiaSpells.MANUS_ABYSSI.get().getDamageSource(caster));
            if (target.isAlive()) suppress(caster, target);
        }
        return true;
    }

    /** Fim da canalizacao, por qualquer motivo. Idempotente, como os outros fins de efeito. */
    public static void end(LivingEntity caster) {
        CompoundTag tag = state(caster);
        if (tag == null) return;
        caster.getPersistentData().remove(KEY);
        caster.removeEffect(MobEffects.GLOWING);
        if (caster.level() instanceof ServerLevel level && tag.hasUUID("Target")
                && level.getEntity(tag.getUUID("Target")) instanceof LivingEntity target) {
            target.removeEffect(MagiaEffects.SUPPRESSED);
            MagiaNetwork.sendVisual(caster, target, SpellVisualPayload.Kind.NETHER_GRASP, 0);
        }
    }

    /**
     * A recarga por nivel. Proporcao, e nao valor fixo: o que o Iron's ja descontou de reducao de recarga
     * (atributo, anel, foco) continua descontado.
     */
    public static int cooldownFor(Player player, int effective) {
        Integer level = LAST_LEVEL.remove(player.getUUID());
        if (level == null) return effective;
        // O configurado (140 s por padrao) vale para o nivel 1; os outros niveis mantem a proporcao 110/80.
        return (int) Math.round(effective * (COOLDOWN_SECONDS[level - 1] / (double) COOLDOWN_SECONDS[0]));
    }

    /**
     * Dano total da Zona Nula em fracao da vida maxima: 10/15/20%, +2,5% a cada +10% de poder de magia
     * (o "por 100 de AP" do LoL, na escala do Iron's).
     */
    private float zonePercent(int spellLevel, @Nullable LivingEntity caster) {
        int level = Math.max(1, Math.min(3, spellLevel));
        float base = getSpellPower(spellLevel, null);
        float power = caster == null || base <= 0 ? 1 : getSpellPower(spellLevel, caster) / base;
        return ZONE_PERCENT[level - 1] + 0.25F * Math.max(0, power - 1);
    }

    private static void suppress(LivingEntity caster, LivingEntity target) {
        target.addEffect(new MobEffectInstance(MagiaEffects.SUPPRESSED, PULSE_TICKS * 2, 0, false, false, true), caster);
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, PULSE_TICKS * 2, 0, false, false, false), caster);
    }

    /** Quem conjura foi controlado: a corrente parte. A propria canalizacao nao conta. */
    private static boolean blockedChannel(LivingEntity caster) {
        return castBlocked(caster) || caster.hasEffect(MagiaEffects.SUPPRESSED) || caster.hasEffect(MagiaEffects.CAGED);
    }

    @Nullable
    private static CompoundTag state(LivingEntity caster) {
        CompoundTag data = caster.getPersistentData();
        return data.contains(KEY, Tag.TAG_COMPOUND) ? data.getCompound(KEY) : null;
    }
}
