package com.aurorion.magia.effect;

import com.aurorion.magia.AurorionMagia;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/** Sobrecarga mental. Paralisa, mas permite que o alvo receba dano. */
public final class InfiniteVoidEffect extends MobEffect {
    private static final String ANCHOR = "aurorion_magia:vazio_infinito_ancora";

    public InfiniteVoidEffect() {
        super(MobEffectCategory.HARMFUL, 0x859EFF);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, AurorionMagia.id("vazio_infinito_speed"),
                -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.JUMP_STRENGTH, AurorionMagia.id("vazio_infinito_jump"),
                -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.GRAVITY, AurorionMagia.id("vazio_infinito_gravity"),
                -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return true; }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        entity.setDeltaMovement(Vec3.ZERO);
        entity.resetFallDistance();
        entity.stopUsingItem();
        if (!entity.level().isClientSide) {
            CompoundTag data = entity.getPersistentData();
            if (!data.contains(ANCHOR)) {
                CompoundTag anchor = new CompoundTag();
                anchor.putDouble("X", entity.getX()); anchor.putDouble("Y", entity.getY()); anchor.putDouble("Z", entity.getZ());
                anchor.putFloat("Yaw", entity.getYRot()); anchor.putFloat("Pitch", entity.getXRot());
                anchor.putString("Dim", entity.level().dimension().location().toString());
                data.put(ANCHOR, anchor);
            }
            CompoundTag anchor = data.getCompound(ANCHOR);
            // Uma troca por comando da staff nao arrasta coordenadas de outra dimensao.
            if (!anchor.getString("Dim").equals(entity.level().dimension().location().toString())) return false;
            Vec3 position = new Vec3(anchor.getDouble("X"), anchor.getDouble("Y"), anchor.getDouble("Z"));
            if (!Double.isFinite(position.lengthSqr()) || !Float.isFinite(anchor.getFloat("Yaw"))
                    || !Float.isFinite(anchor.getFloat("Pitch"))) return false;
            if (entity.position().distanceToSqr(position) > .0025) {
                if (entity instanceof ServerPlayer player) player.connection.teleport(position.x, position.y, position.z,
                        anchor.getFloat("Yaw"), anchor.getFloat("Pitch"));
                else entity.setPos(position.x, position.y, position.z);
            }
            entity.setYRot(anchor.getFloat("Yaw"));
            entity.setXRot(anchor.getFloat("Pitch"));
            entity.setYHeadRot(entity.getYRot());
        }
        if (entity instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setTarget(null);
        }
        if (entity instanceof ServerPlayer player && MagicData.getPlayerMagicData(player).isCasting()) {
            Utils.serverSideCancelCast(player);
        }
        return true;
    }

    public static void release(LivingEntity entity) { entity.getPersistentData().remove(ANCHOR); }
}
