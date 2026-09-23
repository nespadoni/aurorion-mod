package com.aurorion.magia.spell;

import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import com.aurorion.magia.registry.MagiaEffects;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.ICastDataSerializable;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.RaycastBuilder;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.RecastInstance;
import io.redspace.ironsspellbooks.capabilities.magic.RecastResult;
import io.redspace.ironsspellbooks.capabilities.magic.TargetEntityCastData;
import io.redspace.ironsspellbooks.spells.EntityCastData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

/**
 * Furtum Impetus — Sequestro de Impulso. Duas conjuracoes, pelo sistema de recast do Iron's:
 *
 * <ol>
 *   <li><b>Roubar</b>: mira uma criatura, flecha, tridente ou item em voo. Ele para no lugar e o
 *       impulso fica guardado na sua mao por ate 30 s. Sem nada na mira, rouba o seu proprio
 *       movimento — anula o knockback que voce acabou de levar, ou a queda.</li>
 *   <li><b>Devolver</b>: mira outra criatura e ela recebe o impulso na direcao do seu olhar. Sem
 *       alvo, o impulso e seu: um arranque na direcao em que voce olha.</li>
 * </ol>
 *
 * <p>O cooldown so comeca depois da devolucao (ou quando os 30 s acabam) — e isso que o recast
 * garante. Mob conjurador so rouba.
 */
public final class FurtumImpetusSpell extends AurorionSpell {
    private static final int RANGE = 20;
    private static final int STORE_TICKS = 600;
    /** Abaixo disto o proprio conjurador esta parado: nao ha o que roubar dele. */
    private static final double SELF_MIN_SPEED_SQR = 0.04;

    public FurtumImpetusSpell() {
        super("furtum_impetus", SchoolRegistry.EVOCATION_RESOURCE, SpellRarity.RARE, 5, 18, CastType.INSTANT);
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 1;
        this.baseManaCost = 30;
        this.manaCostPerLevel = 6;
        this.castTime = 0;
    }

    @Override
    public int getRecastCount(int spellLevel, @Nullable LivingEntity entity) {
        return 2;
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.75f, 0.93f, 1.0f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.multiplicador_impulso",
                        Utils.stringTruncation(1.4 + 0.2 * spellLevel, 1)),
                Component.translatable("ui.aurorion_magia.alcance", RANGE));
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData data) {
        if (level.isClientSide || data == null) return true;
        data.resetAdditionalCastData();
        if (releasing(entity, data)) {
            // Alvo opcional: sem ninguem na mira, o arranque e do proprio conjurador.
            Utils.preCastTargetHelper(level, entity, data, this, RANGE, AIM_ASSIST, false,
                    target -> target != entity);
            return true;
        }
        Entity hit = aimAnything(level, entity);
        if (hit != null) {
            data.setAdditionalCastData(new EntityCastData(hit));
            return true;
        }
        if (motionOf(entity).lengthSqr() > SELF_MIN_SPEED_SQR) return true;
        if (entity instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("aurorion_magia.nada_a_roubar"), true);
        }
        return false;
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData data) {
        if (level instanceof ServerLevel serverLevel) {
            if (releasing(entity, data)) {
                release(serverLevel, entity, data);
            } else {
                steal(entity, data, spellLevel, castSource);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, data);
    }

    @Override
    public void onRecastFinished(ServerPlayer player, RecastInstance recast, RecastResult result,
                                 ICastDataSerializable castData) {
        // Timeout, morte, contra-magia: o impulso guardado se perde.
        if (result != RecastResult.USED_ALL_RECASTS) Momentum.clear(player);
        super.onRecastFinished(player, recast, result, castData);
    }

    private void steal(LivingEntity caster, MagicData data, int spellLevel, CastSource castSource) {
        Entity victim = data != null && data.getAdditionalCastData() instanceof EntityCastData cast
                ? cast.getCastingEntity() : null;
        if (victim == null || !victim.isAlive()) victim = caster;

        double amount = Momentum.steal(victim, caster, spellLevel);
        if (!(caster instanceof Player)) return;

        Momentum.store(caster, amount, STORE_TICKS);
        data.getPlayerRecasts().addRecast(new RecastInstance(getSpellId(), spellLevel,
                getRecastCount(spellLevel, caster), STORE_TICKS, castSource, null), data);

        sound(victim, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0f, 1.4f);
        MagiaNetwork.sendVisual(caster, victim, SpellVisualPayload.Kind.FURTUM_STEAL, 14);
        MagiaNetwork.sendVisual(caster, caster, SpellVisualPayload.Kind.FURTUM_HELD, STORE_TICKS);
    }

    private void release(ServerLevel level, LivingEntity caster, MagicData data) {
        double amount = Momentum.take(caster);
        LivingEntity target = data.getAdditionalCastData() instanceof TargetEntityCastData cast
                ? cast.getTarget(level) : null;
        Entity receiver = target != null && target.isAlive() ? target : caster;

        Vec3 impulse = caster.getViewVector(1.0f).scale(amount).add(0, 0.25, 0);
        launch(receiver, motionOf(receiver).add(impulse));
        receiver.resetFallDistance();
        // Empurrao forte vira arremesso: bater na parede machuca, como na Mao do Algoz.
        if (amount > 1.2 && receiver != caster && receiver instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MagiaEffects.THROWN, 30, 0, false, false, false), caster);
        }

        sound(receiver, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2f, 0.6f);
        sound(receiver, SoundEvents.FIREWORK_ROCKET_BLAST, 0.8f, 0.7f);
        MagiaNetwork.sendVisual(caster, receiver, SpellVisualPayload.Kind.FURTUM_RELEASE, 20, impulse, (float) amount);
    }

    private boolean releasing(LivingEntity caster, @Nullable MagicData data) {
        return caster instanceof Player && data != null
                && data.getPlayerRecasts().hasRecastForSpell(this) && Momentum.has(caster);
    }

    /**
     * O raycast do Iron's com filtro proprio: alem de criaturas, pega projeteis e itens em voo — que
     * o filtro padrao ignora.
     */
    @Nullable
    private static Entity aimAnything(Level level, LivingEntity caster) {
        HitResult hit = RaycastBuilder.begin(level, caster)
                .range(RANGE)
                .checkForBlocks(true)
                .bbInflation(0.5f)
                .filter(entity -> entity != caster && entity.isAlive() && !entity.isSpectator()
                        && (entity instanceof LivingEntity || entity instanceof Projectile || entity instanceof ItemEntity))
                .build();
        return hit instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
    }
}
