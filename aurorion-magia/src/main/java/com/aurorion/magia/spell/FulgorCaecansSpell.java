package com.aurorion.magia.spell;

import com.aurorion.magia.config.MagiaConfig;
import com.aurorion.magia.entity.MagiaProjectileEntity;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import com.aurorion.magia.registry.MagiaEffects;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellAnimations;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.AnimationHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Fulgor Caecans — Clarao. Uma biribinha de luz jogada para o alto que estoura como a flashbang do CS:
 * todo mundo num raio grande fica com a tela <b>toda branca</b>, sem ver nada, e o som abafado.
 *
 * <ul>
 *   <li>Olhando para o estouro: 5 s. De lado: 4 s. De costas: 3 s — o clarao ainda bate em tudo em volta.
 *       Na borda do raio, ate 30% menos.</li>
 *   <li>Parede nao protege, a menos que {@code [clarao].exigeVisao} esteja ligado (um raycast por pessoa).</li>
 *   <li>Criatura perde o alvo e fica cega e lenta pelos mesmos 5 s.</li>
 *   <li>Nao fere ninguem. Respeita PvP e area protegida como toda magia do mod.</li>
 * </ul>
 *
 * <p>A tela branca e do cliente de quem foi ofuscado ({@code FlashLayer}), lendo o efeito {@code ofuscado}
 * que o vanilla ja sincroniza — nenhum pacote alem do efeito.
 */
public final class FulgorCaecansSpell extends AurorionSpell {
    /** Ticks de voo ate estourar no ar, se nao bater antes. */
    private static final int FUSE_TICKS = 14;
    /** Olhando para o estouro. De lado e de costas sao 80% e 60% disto. */
    private static final int FLASH_TICKS = 100;
    private static final int MAX_TARGETS = 64;

    public FulgorCaecansSpell() {
        super("fulgor_caecans", SchoolRegistry.HOLY_RESOURCE, SpellRarity.UNCOMMON, 3, 18, CastType.INSTANT);
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 0;
        this.baseManaCost = 35;
        this.manaCostPerLevel = 5;
        this.castTime = 0;
    }

    @Override
    public AnimationHolder getCastFinishAnimation() {
        return SpellAnimations.ANIMATION_INSTANT_CAST;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.SNOWBALL_THROW);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(1.0f, 1.0f, 0.92f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.raio", (int) radius(spellLevel)),
                Component.translatable("ui.aurorion_magia.clarao_tempo", FLASH_TICKS / 20));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            Vec3 look = entity.getLookAngle();
            Vec3 from = entity.getEyePosition().add(look.scale(0.6));
            // Para cima e um pouco para a frente: a biribinha sobe, faz o arco e estoura no alto.
            Vec3 velocity = look.scale(0.45).add(0, 0.7, 0);
            MagiaProjectileEntity.launch(serverLevel, entity, MagiaProjectileEntity.Shape.FULGOR, from, velocity,
                    0.22f, 0, (float) radius(spellLevel), FUSE_TICKS);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /** O estouro. Chamado pelo projetil, no ar ou no primeiro toque. */
    public static void flash(ServerLevel level, LivingEntity caster, Vec3 at, double radius) {
        boolean spareCaster = MagiaConfig.FLASH_SPARES_CASTER.get();
        boolean needsSight = MagiaConfig.FLASH_NEEDS_SIGHT.get();
        double radiusSqr = radius * radius;
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
                // So quem tem olhos para cegar: suporte de armadura e afins nao gastam o teto de alvos.
                target -> (target instanceof Player || target instanceof Mob) && target.isAlive()
                        && target.getEyePosition().distanceToSqr(at) <= radiusSqr
                        && (target == caster ? !spareCaster : Hits.hittable(caster, target)));
        // Os mais perto primeiro: com o teto de alvos, quem esta embaixo do estouro nunca fica de fora.
        near.sort(java.util.Comparator.comparingDouble(target -> target.getEyePosition().distanceToSqr(at)));
        int touched = 0;
        for (LivingEntity target : near) {
            if (touched >= MAX_TARGETS) break;
            Vec3 eye = target.getEyePosition();
            if (needsSight && level.clip(new ClipContext(at, eye, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, target))
                    .getType() != HitResult.Type.MISS) continue;
            touched++;
            if (target instanceof Player) {
                double facing = target.getViewVector(1).dot(at.subtract(eye).normalize());
                double share = facing > 0.3 ? 1 : facing > -0.3 ? 0.8 : 0.6;
                // Ate 60% do raio, inteiro; dali ate a borda cai para 70%.
                double distance = Math.sqrt(eye.distanceToSqr(at)) / Math.max(1, radius);
                if (distance > 0.6) share *= 1 - 0.3 * Math.min(1, (distance - 0.6) / 0.4);
                int ticks = (int) Math.round(FLASH_TICKS * share);
                // Novo estouro so estende: dois claroes seguidos nao encurtam a cegueira de ninguem.
                MobEffectInstance current = target.getEffect(MagiaEffects.FLASHED);
                if (current != null && current.getDuration() >= ticks) continue;
                target.addEffect(new MobEffectInstance(MagiaEffects.FLASHED, ticks, 0, false, false, false), caster);
            } else if (target instanceof Mob mob) {
                mob.setTarget(null);
                mob.getNavigation().stop();
                mob.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, FLASH_TICKS, 0, false, false), caster);
                mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, FLASH_TICKS, 2, false, false), caster);
            }
        }
        sound(level, at, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 2.2f, 1.6f);
        sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.9f);
        sound(level, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0f, 2.0f);
        MagiaNetwork.sendVisualAt(level, caster, SpellVisualPayload.Kind.FULGOR_BLAST, 20, at, (float) radius);
    }

    /** 24 blocos no nivel 1 (config), +4 por nivel. */
    private static double radius(int spellLevel) {
        return MagiaConfig.FLASH_RADIUS.get() + 4.0 * (spellLevel - 1);
    }
}
