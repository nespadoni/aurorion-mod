package com.aurorion.limbo.rescue;

import com.aurorion.limbo.registry.LimboEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A passagem aberta pelo Oraculo. Uma entidade, e nao um bloco.
 *
 * <h2>Por que entidade</h2>
 *
 * <p>Bloco registrado e conteudo permanente: uma vez colocado num mundo, remover o mod deixa buraco no
 * save (SDD §6.1). A passagem vive quinze minutos e some — nada disso justifica um bloco. Entidade
 * tambem nasce de graca com posicao interpolada, sincronia com o cliente e remocao automatica.
 *
 * <h2>O que ela guarda, e o que nao guarda</h2>
 *
 * <p>Guarda o <b>alvo</b>: para quem esta passagem foi aberta. Sem isso, duas passagens abertas ao
 * mesmo tempo levariam o resgatador errado para o exilado errado — e com 80 jogadores isso nao e
 * hipotese, e sabado a noite.
 *
 * <p>Nao guarda quem abriu. Qualquer pessoa pode atravessar uma passagem aberta, de proposito: a casa
 * paga uma vida e manda tres pessoas se quiser. O custo e por passagem, nao por cabeca.
 */
public class RescuePortalEntity extends Entity {
    /** Contagem regressiva em ticks. Sincronizada porque o cliente desenha o portal fechando. */
    private static final EntityDataAccessor<Integer> DATA_TICKS_LEFT =
            SynchedEntityData.defineId(RescuePortalEntity.class, EntityDataSerializers.INT);

    private static final String KEY_TARGET = "Target";
    private static final String KEY_TICKS = "TicksLeft";

    /** Raio de entrada. Generoso: atravessar nao pode depender de mira. */
    private static final double ENTER_RADIUS = 1.25D;

    @Nullable
    private UUID target;

    public RescuePortalEntity(EntityType<? extends RescuePortalEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public RescuePortalEntity(Level level, UUID target, int ticks) {
        this(LimboEntities.RESCUE_PORTAL.get(), level);
        this.target = target;
        setTicksLeft(ticks);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_TICKS_LEFT, 0);
    }

    public int ticksLeft() {
        return entityData.get(DATA_TICKS_LEFT);
    }

    private void setTicksLeft(int ticks) {
        entityData.set(DATA_TICKS_LEFT, Math.max(0, ticks));
    }

    @Nullable
    public UUID target() {
        return target;
    }

    @Override
    public void tick() {
        // O cliente so conta para desenhar o fechamento; quem decide e o servidor.
        if (level().isClientSide) {
            if (ticksLeft() > 0) setTicksLeft(ticksLeft() - 1);
            return;
        }

        int left = ticksLeft() - 1;
        setTicksLeft(left);
        if (left <= 0 || target == null) {
            discard();
            return;
        }

        // Varredura so entre quem esta encostando: getEntitiesOfClass usa a AABB, nao a lista inteira
        // do mundo. Com a passagem parada num lugar so, isso e algumas entidades por tick no pior caso.
        for (ServerPlayer player : level().getEntitiesOfClass(ServerPlayer.class,
                getBoundingBox().inflate(ENTER_RADIUS))) {
            if (RescueManager.enterPassage(player, target)) {
                // Uma travessia por tick: quem entrou ja mudou de dimensao e a lista acima virou
                // invalida para o resto do laco.
                break;
            }
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        target = tag.hasUUID(KEY_TARGET) ? tag.getUUID(KEY_TARGET) : null;
        setTicksLeft(tag.getInt(KEY_TICKS));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (target != null) tag.putUUID(KEY_TARGET, target);
        tag.putInt(KEY_TICKS, ticksLeft());
    }

    /** Nao empurra, nao e empurrada, nao apanha. E cenario com uma regra. */
    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    /** Desenhada de longe: quem paga uma vida tem que conseguir achar a passagem que abriu. */
    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        return distanceSqr < 128 * 128;
    }
}
