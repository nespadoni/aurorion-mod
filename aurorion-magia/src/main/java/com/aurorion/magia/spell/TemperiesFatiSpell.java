package com.aurorion.magia.spell;

import com.aurorion.magia.entity.MagiaProjectileEntity;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import com.aurorion.magia.registry.MagiaEffects;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Temperies Fati — Tempera do Destino. A ultimate do Bardo: um arco de energia dourada voa ate o ponto
 * mirado e, onde cai, tudo fica em <b>estase</b> por {@value #STASIS_TICKS} ticks — imovel, suspenso no
 * ar e <b>intocavel</b>: nenhum dano entra, nem do proprio conjurador.
 *
 * <p>Como no jogo, a estase nao escolhe lado: aliado, inimigo, criatura e o proprio conjurador, se
 * estiver dentro do raio. Serve tanto para salvar um amigo quase morto quanto para segurar um grupo
 * inteiro enquanto o time se reposiciona. Chefes ({@code imune_deslocamento}) ficam de fora.
 *
 * <p>Custo: um projetil por alguns ticks e uma busca de entidades na queda. A estase e efeito de status,
 * que o vanilla tica sozinho.
 */
public final class TemperiesFatiSpell extends AurorionSpell {
    public static final int STASIS_TICKS = SpellBalance.duration(50);
    private static final int RANGE = 40;
    private static final int MAX_TARGETS = 24;

    public TemperiesFatiSpell() {
        super("temperies_fati", SchoolRegistry.ENDER_RESOURCE, SpellRarity.EPIC, 3, 60, CastType.LONG);
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 0;
        this.baseManaCost = 70;
        this.manaCostPerLevel = 15;
        this.castTime = 15;
    }

    @Override
    public Optional<SoundEvent> getCastStartSound() {
        return Optional.of(SoundEvents.AMETHYST_BLOCK_CHIME);
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.ILLUSIONER_CAST_SPELL);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(1.0f, 0.8f, 0.3f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.estase", Utils.timeFromTicks(STASIS_TICKS, 1)),
                Component.translatable("ui.aurorion_magia.raio", Utils.stringTruncation(radius(spellLevel), 1)),
                Component.translatable("ui.aurorion_magia.alcance", RANGE),
                Component.translatable("ui.aurorion_magia.estase_todos"));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            Vec3 at = Hits.aimGround(serverLevel, entity, RANGE);
            Vec3 from = entity.getEyePosition().add(entity.getLookAngle().scale(0.6));
            int ticks = Math.clamp((int) (from.distanceTo(at) * 0.8), 8, 32);
            MagiaProjectileEntity.launch(serverLevel, entity, MagiaProjectileEntity.Shape.TEMPERIES, from,
                    MagiaProjectileEntity.arc(from, at, ticks, MagiaProjectileEntity.Shape.TEMPERIES),
                    0.35f, STASIS_TICKS, radius(spellLevel), ticks + 20);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /** O arco caiu: todos no raio, quem conjurou incluido, entram em estase. */
    public static void land(ServerLevel level, LivingEntity caster, Vec3 at, double radius, int ticks) {
        double radiusSqr = radius * radius;
        List<LivingEntity> caught = level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
                target -> target.isAlive() && !target.isSpectator() && !untouchable(target)
                        && !(target instanceof Player player && player.isCreative())
                        && !Displacement.isImmune(target)
                        && target.position().distanceToSqr(at) <= radiusSqr);
        caught.sort(Comparator.comparingDouble(target -> target.position().distanceToSqr(at)));
        if (caught.size() > MAX_TARGETS) caught = caught.subList(0, MAX_TARGETS);
        for (LivingEntity target : caught) stasis(target, caster, ticks);

        sound(level, at, SoundEvents.BEACON_ACTIVATE, 1.6f, 1.4f);
        sound(level, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4f, 0.8f);
    }

    public static void stasis(LivingEntity target, LivingEntity by, int ticks) {
        target.addEffect(new MobEffectInstance(MagiaEffects.STASIS, ticks, 0, false, false, true), by);
        launch(target, Vec3.ZERO);
        target.resetFallDistance();
        if (target instanceof ServerPlayer player) {
            if (MagicData.getPlayerMagicData(player).isCasting()) Utils.serverSideCancelCast(player);
            player.stopUsingItem();
        } else if (target instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        MagiaNetwork.sendVisual(by, target, SpellVisualPayload.Kind.TEMPERIES_FATI, ticks);
    }

    /** 4,25 blocos no nivel 1, +0,75 por nivel. */
    private static float radius(int spellLevel) {
        return 3.5f + 0.75f * spellLevel;
    }
}
