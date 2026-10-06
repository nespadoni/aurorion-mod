package com.aurorion.magia.spell;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.entity.ShadowEntity;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import com.aurorion.magia.registry.MagiaEffects;
import com.aurorion.magia.registry.MagiaSpells;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Signum Mortis — Marca Fatal. A ultimate do Zed: quem conjura fica inalvejavel por um instante,
 * aparece <b>atras</b> do alvo e deixa uma Sombra Viva onde estava. O alvo fica marcado por tres
 * segundos; todo dano que quem conjurou causar nele nesse tempo e somado e, quando a marca vence, uma
 * parte dele (30/40/50% por nivel) e repetida de uma vez, mais o dano base.
 *
 * <p>A soma mora no {@code persistentData} do alvo e e alimentada pelo evento de dano; o estouro e o fim
 * do efeito. Nada roda por tick.
 */
public final class SignumMortisSpell extends AurorionSpell {
    private static final int RANGE = 12;
    private static final int MARK_TICKS = 60;
    private static final int UNTARGETABLE_TICKS = 15;
    private static final String MARK_KEY = AurorionMagia.MOD_ID + ":marca_fatal";

    public SignumMortisSpell() {
        super("signum_mortis", SchoolRegistry.ENDER_RESOURCE, SpellRarity.LEGENDARY, 3, 60, CastType.INSTANT);
        this.baseSpellPower = 6;
        this.spellPowerPerLevel = 2;
        this.baseManaCost = 100;
        this.manaCostPerLevel = 20;
        this.castTime = 0;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.ENDERMAN_SCREAM);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.55f, 0.0f, 0.05f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.dano", Utils.stringTruncation(getSpellPower(spellLevel, caster), 1)),
                Component.translatable("ui.aurorion_magia.repete_dano", (int) (ratio(spellLevel) * 100)),
                Component.translatable("ui.aurorion_magia.alcance", RANGE),
                Component.translatable("ui.aurorion_magia.deixa_sombra"));
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return aim(level, entity, playerMagicData, RANGE, false, target -> Hits.pvpAllowed(entity, target));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            LivingEntity target = target(serverLevel, entity, playerMagicData);
            if (target != null) mark(serverLevel, entity, target, spellLevel);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private void mark(ServerLevel level, LivingEntity caster, LivingEntity target, int spellLevel) {
        Vec3 origin = caster.position();
        caster.addEffect(new MobEffectInstance(MagiaEffects.UNTARGETABLE, UNTARGETABLE_TICKS, 0, false, false, true));

        // Atras do alvo, no lado oposto ao que ele esta olhando. So vale se o lugar esta livre E se ha
        // passagem do corpo do alvo ate ele: sem a segunda conferencia, um alvo de costas para a parede
        // de uma base fechada levaria quem conjura para dentro dela. Sem as duas, fica onde o alvo esta.
        Vec3 back = target.getLookAngle().multiply(1, 0, 1);
        back = back.lengthSqr() < 1.0E-4 ? Vec3.ZERO : back.normalize().scale(-1.5);
        Vec3 behind = target.position().add(back);
        Vec3 chest = target.position().add(0, target.getBbHeight() * 0.5, 0);
        boolean free = level.noCollision(caster, caster.getBoundingBox().move(behind.subtract(caster.position())))
                && level.clip(new ClipContext(chest, behind.add(0, target.getBbHeight() * 0.5, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target)).getType() == HitResult.Type.MISS;
        if (!free) behind = target.position();
        Utils.handleSpellTeleport(this, caster, behind);
        caster.resetFallDistance();
        ShadowEntity.spawn(level, caster, origin, origin, 0, MARK_TICKS + 60);

        CompoundTag mark = new CompoundTag();
        mark.putUUID("By", caster.getUUID());
        mark.putFloat("Base", getSpellPower(spellLevel, caster));
        mark.putFloat("Ratio", ratio(spellLevel));
        mark.putFloat("Stored", 0);
        target.getPersistentData().put(MARK_KEY, mark);
        target.addEffect(new MobEffectInstance(MagiaEffects.DEATH_MARK, MARK_TICKS, 0, false, false, true), caster);

        sound(caster, SoundEvents.ENDERMAN_TELEPORT, 1.2f, 0.5f);
        MagiaNetwork.sendVisual(caster, target, SpellVisualPayload.Kind.SIGNUM_MORTIS, MARK_TICKS);
        MagiaNetwork.sendVisualAt(level, caster, SpellVisualPayload.Kind.UMBRA_SWAP, 14, origin, 0);
    }

    /** Dano de quem marcou no marcado: entra na soma. Chamado pelo evento de dano. */
    public static void record(LivingEntity victim, LivingEntity attacker, float amount) {
        if (!victim.hasEffect(MagiaEffects.DEATH_MARK)) return;
        CompoundTag data = victim.getPersistentData();
        if (!data.contains(MARK_KEY, Tag.TAG_COMPOUND)) return;
        CompoundTag mark = data.getCompound(MARK_KEY);
        if (mark.hasUUID("By") && mark.getUUID("By").equals(attacker.getUUID())) {
            mark.putFloat("Stored", mark.getFloat("Stored") + amount);
        }
    }

    /** A marca venceu: o estouro. A marca sai antes do dano, entao o estouro nao soma em si mesmo. */
    public static void pop(LivingEntity victim) {
        CompoundTag data = victim.getPersistentData();
        if (!data.contains(MARK_KEY, Tag.TAG_COMPOUND) || !(victim.level() instanceof ServerLevel level)) return;
        CompoundTag mark = data.getCompound(MARK_KEY);
        data.remove(MARK_KEY);
        if (!mark.hasUUID("By") || !(level.getEntity(mark.getUUID("By")) instanceof LivingEntity attacker)) return;
        float damage = mark.getFloat("Base") + mark.getFloat("Stored") * mark.getFloat("Ratio");
        FriendlyFire.applyDamage(victim, damage, MagiaSpells.SIGNUM_MORTIS.get().getDamageSource(attacker));
        sound(victim, SoundEvents.WITHER_BREAK_BLOCK, 0.8f, 1.4f);
        MagiaNetwork.sendVisual(attacker, victim, SpellVisualPayload.Kind.SIGNUM_POP, 16);
    }

    /** A marca saiu sem estourar (leite): a soma guardada sai junto. */
    public static void clearMark(LivingEntity victim) {
        victim.getPersistentData().remove(MARK_KEY);
    }

    /** 30% no nivel 1, +10% por nivel. */
    private static float ratio(int spellLevel) {
        return 0.2f + 0.1f * spellLevel;
    }
}
