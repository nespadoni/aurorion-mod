package com.aurorion.magia.spell;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import com.aurorion.magia.registry.MagiaEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A corrente do Vinculum Carnificis.
 *
 * <p>Quem esta preso anda, bate, conjura e se defende a vontade — dentro do raio. Ao cruzar a borda,
 * leva um puxao na direcao da ancora; cada puxao seguido aumenta a "tensao", e o proximo vem mais
 * forte. Ficar dentro do raio alivia a tensao aos poucos.
 *
 * <p>Teleporte nao salva: perola, chorus e teleportes de magia sao cancelados pelo
 * {@code MagiaServerEvents}; e se alguma coisa colocar o preso muito longe mesmo assim (um mod de
 * teleporte sem evento), a corrente o arranca de volta para dentro.
 *
 * <p>A ancora mora no {@code persistentData} do preso, ao lado do efeito que tambem e salvo:
 * deslogar preso e voltar continua preso, no mesmo lugar.
 */
public final class Binding {
    private static final String KEY = AurorionMagia.MOD_ID + ":vinculo";
    private static final String KEY_X = "X";
    private static final String KEY_Y = "Y";
    private static final String KEY_Z = "Z";
    private static final String KEY_DIM = "Dim";
    private static final String KEY_RADIUS = "Radius";
    private static final String KEY_STRAIN = "Strain";

    /** Alem do raio mais isto, nao e fuga a pe: foi teleporte. Volta para dentro na hora. */
    private static final double SNAP_BACK = 8;
    private static final int MAX_STRAIN = 8;

    private Binding() {
    }

    public static void bind(LivingEntity target, LivingEntity caster, float radius, int duration) {
        Vec3 anchor = target.position();
        CompoundTag tag = new CompoundTag();
        tag.putDouble(KEY_X, anchor.x);
        tag.putDouble(KEY_Y, anchor.y);
        tag.putDouble(KEY_Z, anchor.z);
        tag.putString(KEY_DIM, target.level().dimension().location().toString());
        tag.putFloat(KEY_RADIUS, radius);
        target.getPersistentData().put(KEY, tag);

        target.addEffect(new MobEffectInstance(MagiaEffects.BOUND, duration, 0, false, false, true), caster);
        MagiaNetwork.sendVisual(caster, target, SpellVisualPayload.Kind.VINCULUM, duration, anchor, radius);
    }

    /** Chamado pelo tick do efeito, 5 vezes por segundo, so em quem esta preso. */
    public static void enforce(LivingEntity entity) {
        CompoundTag tag = tagOf(entity);
        if (tag == null || !tag.getString(KEY_DIM).equals(entity.level().dimension().location().toString())) return;

        Vec3 anchor = new Vec3(tag.getDouble(KEY_X), tag.getDouble(KEY_Y), tag.getDouble(KEY_Z));
        float radius = tag.getFloat(KEY_RADIUS);
        Vec3 offset = entity.position().subtract(anchor);
        double distance = offset.length();
        int strain = tag.getInt(KEY_STRAIN);

        if (distance <= radius) {
            if (strain > 0) tag.putInt(KEY_STRAIN, strain - 1);
            return;
        }

        strain = Math.min(MAX_STRAIN, strain + 1);
        tag.putInt(KEY_STRAIN, strain);
        Vec3 inward = offset.scale(-1 / distance);

        if (distance > radius + SNAP_BACK) {
            Vec3 inside = anchor.subtract(inward.scale(Math.max(0, radius - 1.5)));
            entity.teleportTo(inside.x, inside.y, inside.z);
            AurorionSpell.launch(entity, Vec3.ZERO);
        } else {
            // Quanto mais passou da borda e quanto mais vezes ja tentou, mais violento.
            double force = Math.min(2.6, 0.6 + 0.2 * strain + (distance - radius) * 0.3);
            AurorionSpell.launch(entity, inward.scale(force).add(0, 0.2 + 0.03 * strain, 0));
        }
        entity.resetFallDistance();
        snap(entity, anchor, strain);
    }

    /** Perola ou teleporte negado: mesmo estalo do puxao, sem mover ninguem. */
    public static void refuseTeleport(LivingEntity entity) {
        CompoundTag tag = tagOf(entity);
        if (tag == null) return;
        Vec3 anchor = new Vec3(tag.getDouble(KEY_X), tag.getDouble(KEY_Y), tag.getDouble(KEY_Z));
        snap(entity, anchor, MAX_STRAIN);
    }

    public static boolean isBound(Entity entity) {
        return entity instanceof LivingEntity living && living.hasEffect(MagiaEffects.BOUND) && tagOf(living) != null;
    }

    public static void release(LivingEntity entity) {
        entity.getPersistentData().remove(KEY);
    }

    private static void snap(LivingEntity entity, Vec3 anchor, int strain) {
        AurorionSpell.sound(entity, SoundEvents.CHAIN_BREAK, 1.0f, 0.55f + strain * 0.04f);
        AurorionSpell.sound(entity, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.8f, 0.6f);
        MagiaNetwork.sendVisual(null, entity, SpellVisualPayload.Kind.VINCULUM_SNAP, 8, anchor, strain);
        if (entity instanceof ServerPlayer player && strain >= MAX_STRAIN) {
            player.displayClientMessage(Component.translatable("aurorion_magia.corrente"), true);
        }
    }

    @Nullable
    private static CompoundTag tagOf(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        return data.contains(KEY, Tag.TAG_COMPOUND) ? data.getCompound(KEY) : null;
    }
}
