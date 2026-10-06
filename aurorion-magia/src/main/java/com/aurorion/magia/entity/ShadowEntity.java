package com.aurorion.magia.entity;

import com.aurorion.magia.registry.MagiaEntities;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A Sombra Viva do Zed: o corpo de quem conjurou, feito de sombra. Nao luta, nao tem vida e nao e
 * alvo de nada — ela so <i>esta</i> num lugar: as magias do kit saem tambem dela (shuriken, corte) e
 * a Sombra Viva troca o dono de lugar com ela.
 *
 * <p>Entidade, e nao estado no dono, pelo mesmo motivo da {@link SpellZoneEntity}: e um lugar no
 * mundo que o cliente precisa ver e que some sozinho. O dono vai sincronizado para o cliente desenhar
 * a pele certa. Uma sombra custa um tick de entidade parada; o avanco inicial dura poucos ticks.
 */
public class ShadowEntity extends Entity {
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(ShadowEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> DATA_LIFE =
            SynchedEntityData.defineId(ShadowEntity.class, EntityDataSerializers.INT);

    /** Teto de sombras vivas por dono. A terceira empurra a mais antiga para fora. */
    public static final int MAX_PER_OWNER = 2;
    private static final DustParticleOptions SMOKE = new DustParticleOptions(new Vector3f(0.05f, 0.0f, 0.06f), 1.3f);
    private static final DustParticleOptions EMBER = new DustParticleOptions(new Vector3f(0.75f, 0.05f, 0.08f), 0.7f);

    private Vec3 destination = Vec3.ZERO;
    private int dashTicks;

    public ShadowEntity(EntityType<? extends ShadowEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.blocksBuilding = false;
    }

    /** Sombra nova em {@code at}, avancando ate {@code destination} em {@code dashTicks} ticks. */
    public static ShadowEntity spawn(ServerLevel level, LivingEntity owner, Vec3 at, Vec3 destination, int dashTicks,
                                     int life) {
        List<ShadowEntity> mine = of(level, owner);
        while (mine.size() >= MAX_PER_OWNER) {
            ShadowEntity oldest = mine.removeFirst();
            oldest.discard();
        }
        ShadowEntity shadow = new ShadowEntity(MagiaEntities.SHADOW.get(), level);
        shadow.setPos(at.x, at.y, at.z);
        shadow.setYRot(owner.getYRot());
        shadow.yRotO = owner.getYRot();
        shadow.entityData.set(DATA_OWNER, Optional.of(owner.getUUID()));
        shadow.entityData.set(DATA_LIFE, Math.max(1, life));
        shadow.destination = destination;
        shadow.dashTicks = Math.max(0, dashTicks);
        level.addFreshEntity(shadow);
        return shadow;
    }

    /** As sombras vivas de {@code owner} na dimensao dele, da mais antiga para a mais nova. */
    public static List<ShadowEntity> of(ServerLevel level, LivingEntity owner) {
        List<ShadowEntity> found = level.getEntitiesOfClass(ShadowEntity.class,
                new AABB(owner.position(), owner.position()).inflate(48),
                shadow -> shadow.isAlive() && shadow.isOwnedBy(owner));
        found.sort((a, b) -> Integer.compare(b.tickCount, a.tickCount));
        return found;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OWNER, Optional.empty());
        builder.define(DATA_LIFE, 100);
    }

    @Nullable
    public UUID ownerId() {
        return entityData.get(DATA_OWNER).orElse(null);
    }

    public boolean isOwnedBy(Entity entity) {
        return entity.getUUID().equals(ownerId());
    }

    /** 0 ao nascer, 1 no auge, 0 de novo ao sumir. */
    public float fade(float partial) {
        float age = tickCount + partial;
        return Mth.clamp(age / 4F, 0, 1) * Mth.clamp((entityData.get(DATA_LIFE) - age) / 10F, 0, 1);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (tickCount % 2 == 0) {
                level().addParticle(SMOKE, getX() + (random.nextDouble() - 0.5) * 0.6, getY() + random.nextDouble() * 1.8,
                        getZ() + (random.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
            }
            if (tickCount % 7 == 0) level().addParticle(EMBER, getX(), getY() + 1.5, getZ(), 0, 0.01, 0);
            return;
        }
        if (tickCount >= entityData.get(DATA_LIFE)) {
            discard();
            return;
        }
        if (dashTicks > 0) {
            Vec3 left = destination.subtract(position());
            Vec3 step = left.scale(1.0 / dashTicks);
            setPos(getX() + step.x, getY() + step.y, getZ() + step.z);
            dashTicks--;
            if (level() instanceof ServerLevel server) {
                server.playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.PHANTOM_FLAP,
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.2F, 1.6F);
            }
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 64 * 64;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) entityData.set(DATA_OWNER, Optional.of(tag.getUUID("Owner")));
        entityData.set(DATA_LIFE, Math.max(1, tag.getInt("Life")));
        tickCount = tag.getInt("Age");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        UUID owner = ownerId();
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putInt("Life", entityData.get(DATA_LIFE));
        tag.putInt("Age", tickCount);
    }
}
