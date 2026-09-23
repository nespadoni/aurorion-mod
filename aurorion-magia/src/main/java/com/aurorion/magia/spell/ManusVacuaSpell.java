package com.aurorion.magia.spell;

import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

/**
 * Manus Vacua — Mao Vazia. Arranca o item da mao do alvo e o joga no chao, entre voce e ele.
 *
 * <p>Mao principal por padrao; <b>agachado</b>, a mao secundaria (escudo, totem, foco). O item cai com
 * {@value #PICKUP_DELAY} ticks sem poder ser pego — a janela para voce, ou um aliado, chegar antes.
 * Nada e destruido: o item existe no chao como qualquer drop.
 */
public final class ManusVacuaSpell extends AurorionSpell {
    private static final int RANGE = 14;
    private static final int PICKUP_DELAY = 40;

    public ManusVacuaSpell() {
        super("manus_vacua", SchoolRegistry.EVOCATION_RESOURCE, SpellRarity.UNCOMMON, 3, 20, CastType.INSTANT);
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 1;
        this.baseManaCost = 35;
        this.manaCostPerLevel = 5;
        this.castTime = 0;
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.88f, 0.9f, 0.96f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(Component.translatable("ui.aurorion_magia.desarme"),
                Component.translatable("ui.aurorion_magia.alcance", RANGE));
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        InteractionHand hand = handFor(entity);
        return aim(level, entity, playerMagicData, RANGE, false, target -> !target.getItemInHand(hand).isEmpty());
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            LivingEntity target = target(serverLevel, entity, playerMagicData);
            if (target != null) disarm(serverLevel, entity, target, handFor(entity));
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private static void disarm(ServerLevel level, LivingEntity caster, LivingEntity target, InteractionHand hand) {
        ItemStack stack = target.getItemInHand(hand);
        if (stack.isEmpty()) return;

        if (target.isUsingItem() && target.getUsedItemHand() == hand) target.stopUsingItem();
        target.setItemInHand(hand, ItemStack.EMPTY);

        Vec3 from = target.position().add(0, target.getBbHeight() * 0.6, 0);
        Vec3 toward = caster.position().subtract(target.position());
        Vec3 fling = toward.lengthSqr() < 1.0E-4 ? Vec3.ZERO : toward.normalize().scale(0.22);
        ItemEntity drop = new ItemEntity(level, from.x, from.y, from.z, stack, fling.x, 0.32, fling.z);
        drop.setPickUpDelay(PICKUP_DELAY);
        level.addFreshEntity(drop);

        if (target instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("aurorion_magia.desarmado"), true);
        }
        sound(target, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 1.4f);
        sound(target, SoundEvents.ARMOR_EQUIP_CHAIN.value(), 1.0f, 0.6f);
        MagiaNetwork.sendVisual(caster, target, SpellVisualPayload.Kind.MANUS_VACUA, 20, from, hand.ordinal());
    }

    private static InteractionHand handFor(LivingEntity caster) {
        return caster.isShiftKeyDown() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }
}
