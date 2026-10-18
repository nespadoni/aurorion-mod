package com.aurorion.magia.effect;

import com.aurorion.magia.AurorionMagia;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.resources.ResourceLocation;
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
            if (!sameDimension(anchor.getString("Dim"), entity.level().dimension().location())) return false;
            // Por tick: primitivos direto da tag, sem Vec3 nem String novos.
            double x = anchor.getDouble("X"), y = anchor.getDouble("Y"), z = anchor.getDouble("Z");
            float yaw = anchor.getFloat("Yaw"), pitch = anchor.getFloat("Pitch");
            if (!Double.isFinite(x * x + y * y + z * z) || !Float.isFinite(yaw) || !Float.isFinite(pitch)) return false;
            if (entity.distanceToSqr(x, y, z) > .0025) {
                if (entity instanceof ServerPlayer player) player.connection.teleport(x, y, z, yaw, pitch);
                else entity.setPos(x, y, z);
            }
            entity.setYRot(yaw);
            entity.setXRot(pitch);
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

    /** {@code stored.equals(dim.toString())} sem montar a String "namespace:path" a cada tick. */
    private static boolean sameDimension(String stored, ResourceLocation dim) {
        String namespace = dim.getNamespace();
        String path = dim.getPath();
        int split = namespace.length();
        return stored.length() == split + 1 + path.length() && stored.startsWith(namespace)
                && stored.charAt(split) == ':' && stored.startsWith(path, split + 1);
    }
}
