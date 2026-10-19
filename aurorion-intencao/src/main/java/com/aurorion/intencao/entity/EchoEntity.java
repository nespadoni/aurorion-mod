package com.aurorion.intencao.entity;

import com.aurorion.intencao.registry.IntencaoEntities;
import com.aurorion.intencao.server.IntencaoData;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FastColor;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Optional;
import java.util.UUID;

/**
 * O Eco: o corpo de quem foi chamado, sentado em transe no lugar onde estava. Quem passa entende
 * que a pessoa esta numa batalha de intencao — e nao offline, nem AFK, nem morta.
 *
 * <p>Nao e atacavel, nao colide e nao e alvo de nada. A pele vem do dono, sincronizado. A cada dois
 * segundos o servidor confere se a ancora do dono ainda aponta para este Eco; se nao (veredito dado
 * com o chunk descarregado, ancora recuperada pela staff), ele some sozinho.
 */
public class EchoEntity extends Entity {
    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(EchoEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> DATA_COLOR =
            SynchedEntityData.defineId(EchoEntity.class, EntityDataSerializers.INT);
    private static final int CHECK_INTERVAL = 40;

    public EchoEntity(EntityType<? extends EchoEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.blocksBuilding = false;
    }

    /**
     * @param at   onde o Eco senta: o chao abaixo do dono, se ele foi chamado no ar
     * @param name o nome do personagem, o mesmo da tela da batalha — nunca o nick da conta de um alt.
     */
    public static EchoEntity spawn(ServerPlayer owner, Vec3 at, int color, String name) {
        ServerLevel level = owner.serverLevel();
        EchoEntity echo = new EchoEntity(IntencaoEntities.ECHO.get(), level);
        echo.moveTo(at.x, at.y, at.z, owner.getYRot(), 0);
        echo.entityData.set(DATA_OWNER, Optional.of(owner.getUUID()));
        echo.entityData.set(DATA_COLOR, color & 0xFFFFFF);
        echo.setCustomName(Component.translatable("aurorion_intencao.eco.nome", name));
        echo.setCustomNameVisible(true);
        level.addFreshEntity(echo);
        return echo;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OWNER, Optional.empty());
        builder.define(DATA_COLOR, 0xFFFFFF);
    }

    @Nullable
    public UUID ownerId() {
        return entityData.get(DATA_OWNER).orElse(null);
    }

    public int color() {
        return entityData.get(DATA_COLOR);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            // Fio de luz subindo devagar: o "puxao" de quem esta longe.
            if (tickCount % 6 == 0) {
                int c = color();
                DustParticleOptions dust = new DustParticleOptions(new Vector3f(FastColor.ARGB32.red(c) / 255F,
                        FastColor.ARGB32.green(c) / 255F, FastColor.ARGB32.blue(c) / 255F), .8F);
                level().addParticle(dust, getX() + (random.nextDouble() - .5) * .7, getY() + .2 + random.nextDouble() * 1.2,
                        getZ() + (random.nextDouble() - .5) * .7, 0, .03, 0);
            }
            return;
        }
        // Tambem no primeiro tick depois de carregar: um Eco cujo chunk estava descarregado no veredito nao
        // pode ficar dois segundos ao lado de quem acabou de voltar.
        if (tickCount != 1 && tickCount % CHECK_INTERVAL != 0 || !(level() instanceof ServerLevel server)) return;
        UUID owner = ownerId();
        IntencaoData.Anchor anchor = owner == null ? null : IntencaoData.get(server.getServer()).anchor(owner);
        if (anchor == null || !getUUID().equals(anchor.echo())) discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
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
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 64 * 64;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) entityData.set(DATA_OWNER, Optional.of(tag.getUUID("Owner")));
        entityData.set(DATA_COLOR, tag.getInt("Color"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        UUID owner = ownerId();
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putInt("Color", color());
    }
}
