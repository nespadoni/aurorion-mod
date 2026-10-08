package com.aurorion.magia.spell;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.config.MagiaConfig;
import com.aurorion.magia.entity.MagiaProjectileEntity;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import com.aurorion.magia.registry.MagiaSpells;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Mors ex Profundis — Morte Vinda das Profundezas. <b>Magia proibida.</b> A ultimate do Pyke: um X de
 * agua se desenha no chao mirado e, {@value #DELAY} ticks depois, detona.
 *
 * <p>Quem estiver sobre o X e abaixo do limiar de vida ({@code limiarProfundezas}: 20/25/30% por
 * nivel) e <b>executado</b> — sem totem. O resto leva o dano normal. Executar alguem devolve a magia na
 * hora: a recarga some e da para jogar o proximo X, como o Pyke encadeando abates.
 *
 * <p>O atraso usa o mesmo projetil das outras magias, parado e invisivel no centro do X: ele e o
 * relogio, e some na detonacao.
 */
public final class MorsExProfundisSpell extends AurorionSpell {
    public static final ResourceKey<DamageType> DAMAGE_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE, AurorionMagia.id("mors_ex_profundis"));
    public static final int DELAY = 10;
    private static final int RANGE = 24;
    private static final float RADIUS = 4;
    /** Meia largura de cada braco do X. */
    private static final double ARM_WIDTH = 1.2;
    private static final int MAX_TARGETS = 16;

    public MorsExProfundisSpell() {
        super("mors_ex_profundis", SchoolRegistry.ICE_RESOURCE, SpellRarity.LEGENDARY, 3, 60, CastType.INSTANT, true);
        this.baseSpellPower = 8;
        this.spellPowerPerLevel = 3;
        this.baseManaCost = 100;
        this.manaCostPerLevel = 25;
        this.castTime = 0;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.DROWNED_SHOOT);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.2f, 0.85f, 0.75f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.dano", Utils.stringTruncation(damagePower(spellLevel, caster), 1)),
                Component.translatable("ui.aurorion_magia.execucao", (int) Math.round(threshold(spellLevel) * 100)),
                Component.translatable("ui.aurorion_magia.alcance", RANGE),
                Component.translatable("ui.aurorion_magia.abate_devolve"),
                Component.translatable("ui.aurorion_magia.proibida"));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            Vec3 at = Hits.aimGround(serverLevel, entity, RANGE);
            MagiaProjectileEntity clock = MagiaProjectileEntity.launch(serverLevel, entity, MagiaProjectileEntity.Shape.ABYSSUS,
                    at, Vec3.ZERO, 0, damagePower(spellLevel, entity), RADIUS, DELAY);
            clock.aux((float) threshold(spellLevel));
            // O X segue a direcao para onde quem conjurou olhava: os bracos cruzam a frente dele.
            clock.setYRot(entity.getYRot());
            MagiaNetwork.sendVisualAt(serverLevel, entity, SpellVisualPayload.Kind.MORS_EX_PROFUNDIS, DELAY + 24, at,
                    visualExtra(entity.getYRot()));
            sound(serverLevel, at, SoundEvents.CONDUIT_ACTIVATE, 1.0f, 1.4f);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /** O X detona. {@code yaw} orienta os bracos; {@code threshold} e o limiar de execucao. */
    public static void detonate(ServerLevel level, LivingEntity caster, Vec3 at, float yaw, double radius, float damage,
                                float threshold) {
        double angle = yaw * Mth.DEG_TO_RAD;
        // Os dois bracos, a 45 graus da frente de quem conjurou.
        Vec3 armA = new Vec3(-Math.sin(angle + Math.PI / 4), 0, Math.cos(angle + Math.PI / 4));
        Vec3 armB = new Vec3(-Math.sin(angle - Math.PI / 4), 0, Math.cos(angle - Math.PI / 4));
        boolean executed = false;
        for (LivingEntity victim : Hits.around(level, caster, at, radius, MAX_TARGETS,
                t -> Hits.enemy(caster, t) && onCross(t.position().subtract(at), armA, armB))) {
            if (Execution.below(victim, threshold)) {
                Execution.execute(level, caster, victim, DAMAGE_TYPE);
                executed = true;
            } else {
                FriendlyFire.applyDamage(victim, damage, MagiaSpells.MORS_EX_PROFUNDIS.get().getDamageSource(caster));
            }
        }
        sound(level, at, SoundEvents.GENERIC_SPLASH, 1.6f, 0.6f);
        sound(level, at, SoundEvents.TRIDENT_THUNDER.value(), 1.0f, 1.2f);
        if (executed && caster instanceof ServerPlayer player) refund(player);
    }

    /** Abate: a recarga some, e a magia volta para a mao. */
    private static void refund(ServerPlayer player) {
        MagicData data = MagicData.getPlayerMagicData(player);
        if (data.getPlayerCooldowns().removeCooldown(MagiaSpells.MORS_EX_PROFUNDIS.get().getSpellId())) {
            data.getPlayerCooldowns().syncToPlayer(player);
        }
        player.displayClientMessage(Component.translatable("aurorion_magia.profundezas_de_novo")
                .withStyle(ChatFormatting.DARK_AQUA), true);
    }

    /** Distancia ao eixo de algum dos bracos do X, no plano do chao. */
    private static boolean onCross(Vec3 offset, Vec3 armA, Vec3 armB) {
        Vec3 flat = offset.multiply(1, 0, 1);
        return distanceToAxis(flat, armA) <= ARM_WIDTH || distanceToAxis(flat, armB) <= ARM_WIDTH;
    }

    private static double distanceToAxis(Vec3 point, Vec3 axis) {
        double along = point.dot(axis);
        return Math.sqrt(Math.max(0, point.lengthSqr() - along * along));
    }

    private static double threshold(int spellLevel) {
        return MagiaConfig.MORS_THRESHOLD.get() + 0.05 * (spellLevel - 1);
    }

    /**
     * O pacote de visual so tem um numero livre, e o X precisa de dois: o raio (inteiro) vai na parte
     * inteira e a direcao, como fracao de volta, na parte decimal. O cliente desfaz em
     * {@code KitVisuals}.
     */
    private static float visualExtra(float yaw) {
        float turn = Mth.positiveModulo(yaw, 360f) / 360f;
        return RADIUS + Math.min(turn, 0.999f);
    }
}
