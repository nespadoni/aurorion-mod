package com.aurorion.utils.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * A montaria invisivel do freeze: o que prende a pessoa no lugar, inclusive no ar. O estado
 * "congelado" em si e o efeito {@code aurorion_utils:congelado} — ver
 * {@code com.aurorion.utils.freeze.FreezeManager}.
 *
 * <p>Se limpa sozinha: perder o passageiro (desconexao, morte, {@code /unfreeze}) sem que alguem
 * chame {@link Entity#discard()} explicitamente deixaria uma ancora fantasma no mundo — por isso
 * ela se descarta no primeiro tick em que percebe que ficou sem ninguem montado.</p>
 *
 * <p>No cliente, cada ancora solta um pouco de geada em volta de quem esta preso. E a propria ancora
 * que faz isso, no tick que o jogo ja da a ela: nao ha lista de congelados para varrer.</p>
 */
public class FreezeAnchorEntity extends Entity {
    private static final int FROST_INTERVAL = 6;

    public FreezeAnchorEntity(EntityType<? extends FreezeAnchorEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if (tickCount > 1 && getPassengers().isEmpty()) discard();
            return;
        }
        if (tickCount % FROST_INTERVAL == 0 && !getPassengers().isEmpty()) frost(getPassengers().get(0));
    }

    /** O anel de geada e mais largo que a ancora: sem isto ele sumiria na borda da tela. */
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(1.0, 2.0, 1.0);
    }

    /** Congelado fica de pe, parado como estava — nao sentado no ar. */
    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    private void frost(Entity passenger) {
        double radius = passenger.getBbWidth() * 0.7 + 0.2;
        double angle = random.nextDouble() * Math.PI * 2;
        double y = passenger.getY() + random.nextDouble() * passenger.getBbHeight();
        level().addParticle(ParticleTypes.SNOWFLAKE, passenger.getX() + Math.cos(angle) * radius, y,
                passenger.getZ() + Math.sin(angle) * radius, 0, -0.01, 0);
        if (random.nextInt(3) == 0) {
            level().addParticle(ParticleTypes.WHITE_ASH, passenger.getX(), passenger.getY() + 0.05, passenger.getZ(),
                    (random.nextDouble() - 0.5) * 0.1, 0.02, (random.nextDouble() - 0.5) * 0.1);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // Sem estado nenhum pra sincronizar — a ancora so existe pra ser montada.
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // noSave() de proposito — nunca persiste em disco.
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // Idem.
    }
}
