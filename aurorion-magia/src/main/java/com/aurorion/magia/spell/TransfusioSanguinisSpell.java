package com.aurorion.magia.spell;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Transfusio Sanguinis — Transfusao. O Q do Vladimir: rouba vida de um alvo a vista — o dano vira cura.
 *
 * <p>Cada conjuracao enche a reserva de sangue; a <b>terceira</b> sai potencializada: quase o dobro de
 * dano, o dobro de cura e um arranque de velocidade. Quando a reserva fica cheia, a barra de acao avisa.
 * O contador mora no {@code persistentData} de quem conjura.
 */
public final class TransfusioSanguinisSpell extends AurorionSpell {
    private static final int RANGE = 16;
    private static final String STACKS_KEY = AurorionMagia.MOD_ID + ":transfusao";

    public TransfusioSanguinisSpell() {
        super("transfusio_sanguinis", SchoolRegistry.BLOOD_RESOURCE, SpellRarity.RARE, 5, 8, CastType.INSTANT);
        this.baseSpellPower = 4;
        this.spellPowerPerLevel = 2;
        this.baseManaCost = 30;
        this.manaCostPerLevel = 4;
        this.castTime = 0;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.WARDEN_HEARTBEAT);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.75f, 0.05f, 0.1f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        float damage = getSpellPower(spellLevel, caster);
        return List.of(
                Component.translatable("ui.aurorion_magia.dano", Utils.stringTruncation(damage, 1)),
                Component.translatable("ui.aurorion_magia.cura", Utils.stringTruncation(damage * 0.3f, 1)),
                Component.translatable("ui.aurorion_magia.alcance", RANGE),
                Component.translatable("ui.aurorion_magia.terceira_potencializada"));
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return aim(level, entity, playerMagicData, RANGE, false, target -> Hits.pvpAllowed(entity, target));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            LivingEntity target = target(serverLevel, entity, playerMagicData);
            if (target != null) transfuse(entity, target, spellLevel);
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private void transfuse(LivingEntity caster, LivingEntity target, int spellLevel) {
        CompoundTag data = caster.getPersistentData();
        int stacks = data.getInt(STACKS_KEY) + 1;
        boolean empowered = stacks >= 3;
        data.putInt(STACKS_KEY, empowered ? 0 : stacks);

        float damage = getSpellPower(spellLevel, caster) * (empowered ? 1.8f : 1f);
        float heal = damage * (empowered ? 0.6f : 0.3f);
        if (FriendlyFire.applyDamage(target, damage, getDamageSource(caster))) caster.heal(heal);

        if (empowered) {
            caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, false, false, true));
            sound(caster, SoundEvents.WARDEN_HEARTBEAT, 2.0f, 0.6f);
        } else if (stacks == 2 && caster instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("aurorion_magia.reserva_cheia")
                    .withStyle(ChatFormatting.DARK_RED), true);
        }
        MagiaNetwork.sendVisual(caster, target, SpellVisualPayload.Kind.TRANSFUSIO, 16, target.position(), empowered ? 1 : 0);
    }
}
