package com.aurorion.utils.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Ancora visual e logica de uma abducao. So existe enquanto a animacao dura — quem controla o
 * ciclo de vida dela (spawn, fases e descarte) e o {@code AbductionManager}, nunca a propria
 * entidade. O alvo fica montado nela durante as fases HOLD/ASCEND — montaria e o que garante
 * imobilidade real (nao anda, nao pula, so olha ao redor), sem depender de nenhum
 * {@code MobEffect} — ver {@code AbductionManager} e {@code FreezeAnchorEntity}, que usa a mesma
 * ideia pro {@code /freeze}.
 *
 * <p>De proposito, nao tem colisao fisica nem hitbox de interacao (comportamento padrao de
 * {@link Entity}, nao sobrescrito aqui): a barreira "ninguem atravessa" pra quem NAO e o alvo e
 * feita empurrando quem chega perto, a cada tick em que ela existe — nunca uma VoxelShape solida
 * customizada. E exatamente a classe de bug (blocos "torcendo", travando no lugar) que motivou
 * nao repetir a abordagem por blocos usada antes no MCreator.</p>
 */
public class AbductionBeamEntity extends Entity {
    /** Mesma ordem de {@code ActiveAbduction.Phase} (HOLD/ASCEND/RETRACT) — so pro renderer. */
    public static final int PHASE_HOLD = 0;
    public static final int PHASE_ASCEND = 1;
    public static final int PHASE_RETRACT = 2;

    private static final EntityDataAccessor<Integer> DATA_PHASE =
            SynchedEntityData.defineId(AbductionBeamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_PROGRESS =
            SynchedEntityData.defineId(AbductionBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(AbductionBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_COLOR =
            SynchedEntityData.defineId(AbductionBeamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_GROUND_Y =
            SynchedEntityData.defineId(AbductionBeamEntity.class, EntityDataSerializers.FLOAT);

    /** O quanto mais forte empurra alguem colado na borda do raio de repulsao, por tick. */
    private static final double PUSH_STRENGTH = 0.12;

    @Nullable
    private UUID targetPlayerId;

    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private int lerpSteps;

    public AbductionBeamEntity(EntityType<? extends AbductionBeamEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_PHASE, PHASE_HOLD);
        builder.define(DATA_PROGRESS, 0.0F);
        builder.define(DATA_RADIUS, 1.5F);
        builder.define(DATA_COLOR, 0xFFFFFF);
        builder.define(DATA_GROUND_Y, 0.0F);
    }

    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide) {
            stepTowardsServerPosition();
            return;
        }
        if (!(level() instanceof ServerLevel serverLevel) || targetPlayerId == null) return;

        pushAwayBystanders(serverLevel);
    }

    /**
     * Mesma tecnica do minecart: o cliente caminha ate a posicao que o servidor mandou em alguns
     * passos, em vez de saltar direto pra ela (que e o que {@link Entity#lerpTo} faz por padrao).
     * A subida e movida pelo servidor tick a tick e o passageiro so acompanha o veiculo, entao sem
     * esse amortecimento qualquer irregularidade na chegada dos pacotes vira tranco na tela de quem
     * esta sendo abduzido.
     */
    private void stepTowardsServerPosition() {
        if (lerpSteps <= 0) return;

        lerpPositionAndRotationStep(lerpSteps, lerpX, lerpY, lerpZ, getYRot(), getXRot());
        lerpSteps--;
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpSteps = steps;
    }

    @Override
    public double lerpTargetX() {
        return lerpSteps > 0 ? lerpX : getX();
    }

    @Override
    public double lerpTargetY() {
        return lerpSteps > 0 ? lerpY : getY();
    }

    @Override
    public double lerpTargetZ() {
        return lerpSteps > 0 ? lerpZ : getZ();
    }

    /**
     * Busca limitada a uma caixa pequena ao redor do proprio feixe — nunca a lista global de
     * jogadores online — entao o custo por tick e proporcional a quantas abducoes estao ativas
     * agora, nao a populacao do servidor.
     */
    private void pushAwayBystanders(ServerLevel serverLevel) {
        float radius = getRadius();
        AABB pushZone = getBoundingBox().inflate(radius + 1.0, 4.0, radius + 1.0);

        List<ServerPlayer> nearby = serverLevel.getEntitiesOfClass(ServerPlayer.class, pushZone,
                player -> !player.getUUID().equals(targetPlayerId));

        for (ServerPlayer player : nearby) {
            double dx = player.getX() - getX();
            double dz = player.getZ() - getZ();
            double distSq = dx * dx + dz * dz;
            if (distSq >= radius * radius) continue;

            double dist = Math.sqrt(distSq);
            double nx = dist < 1.0E-4 ? 1.0 : dx / dist;
            double nz = dist < 1.0E-4 ? 0.0 : dz / dist;
            double push = (radius - dist) * PUSH_STRENGTH;

            player.setDeltaMovement(player.getDeltaMovement().add(nx * push, 0.0, nz * push));
            player.hurtMarked = true;
        }
    }

    /**
     * O feixe visual sobe bem alem da hitbox real (que fica pequena, so pra nao interferir com
     * nada) — sem isso o cliente poderia cortar o feixe da tela quando a base sai do frustum mas
     * o topo, la em cima, continua visivel. Tambem desce: durante a subida a entidade se afasta do
     * chao, mas o feixe continua ancorado la embaixo (ver {@code AbductionBeamRenderer}).
     */
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox()
                .expandTowards(0.0, 320.0, 0.0)
                .expandTowards(0.0, -320.0, 0.0)
                .inflate(2.0);
    }

    public void setTargetPlayer(UUID targetPlayerId) {
        this.targetPlayerId = targetPlayerId;
    }

    public void setPhase(int phase) {
        entityData.set(DATA_PHASE, phase);
    }

    public int getPhase() {
        return entityData.get(DATA_PHASE);
    }

    public void setProgress(float progress) {
        entityData.set(DATA_PROGRESS, progress);
    }

    public float getProgress() {
        return entityData.get(DATA_PROGRESS);
    }

    public void setRadius(float radius) {
        entityData.set(DATA_RADIUS, radius);
    }

    public float getRadius() {
        return entityData.get(DATA_RADIUS);
    }

    public void setColor(int argb) {
        entityData.set(DATA_COLOR, argb);
    }

    public int getColor() {
        return entityData.get(DATA_COLOR);
    }

    /**
     * Altura do chao de onde a abducao comecou. Sincronizado uma unica vez, no spawn: com ele o
     * cliente deduz sozinho o quanto o feixe ja subiu ({@code getY() - groundY}) e mantem a base
     * plantada no chao durante a subida, sem precisar de nenhum dado novo por tick.
     */
    public void setGroundY(float groundY) {
        entityData.set(DATA_GROUND_Y, groundY);
    }

    public float getGroundY() {
        return entityData.get(DATA_GROUND_Y);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // Entidade e noSave() de proposito (efemera, dona so do AbductionManager) — nada a ler.
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // Idem — nunca persiste em disco.
    }
}
