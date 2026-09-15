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
 * <h2>O que ela guarda</h2>
 *
 * <p>Guarda o <b>alvo</b>: para quem esta passagem foi aberta. Sem isso, duas passagens abertas ao
 * mesmo tempo levariam o resgatador errado para o exilado errado — e com 80 jogadores isso nao e
 * hipotese, e sabado a noite.
 *
 * <p>Tambem guarda quem pagou. So essa pessoa ativa a passagem; para todos os outros ela e puramente
 * visual. Quando o dono atravessa, a entidade some no mesmo tick para ninguem segui-lo e acabar no
 * Limbo sem um Vinculo de Alma.
 */
public class RescuePortalEntity extends Entity {
    /** Contagem regressiva em ticks. Sincronizada porque o cliente desenha o portal fechando. */
    private static final EntityDataAccessor<Integer> DATA_TICKS_LEFT =
            SynchedEntityData.defineId(RescuePortalEntity.class, EntityDataSerializers.INT);

    private static final String KEY_TARGET = "Target";
    private static final String KEY_RESCUER = "Rescuer";
    private static final String KEY_TICKS = "TicksLeft";

    /** Raio de entrada. Generoso: atravessar nao pode depender de mira. */
    private static final double ENTER_RADIUS = 1.25D;

    @Nullable
    private UUID target;
    @Nullable
    private UUID rescuer;

    public RescuePortalEntity(EntityType<? extends RescuePortalEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public RescuePortalEntity(Level level, UUID target, UUID rescuer, int ticks) {
        this(LimboEntities.RESCUE_PORTAL.get(), level);
        this.target = target;
        this.rescuer = rescuer;
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
        if (left <= 0 || target == null || rescuer == null) {
            discard();
            return;
        }

        // Consulta direta ao dono: terceiros veem a passagem, mas nunca entram nela. Alem de fechar
        // o acidente de jogo, isto evita uma busca de entidades a cada tick por passagem aberta.
        ServerPlayer owner = level().getServer().getPlayerList().getPlayer(rescuer);
        if (owner != null && owner.level() == level()
                && getBoundingBox().inflate(ENTER_RADIUS).intersects(owner.getBoundingBox())
                && RescueManager.enterPassage(owner, target)) {
            discard();
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        target = tag.hasUUID(KEY_TARGET) ? tag.getUUID(KEY_TARGET) : null;
        rescuer = tag.hasUUID(KEY_RESCUER) ? tag.getUUID(KEY_RESCUER) : null;
        setTicksLeft(tag.getInt(KEY_TICKS));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (target != null) tag.putUUID(KEY_TARGET, target);
        if (rescuer != null) tag.putUUID(KEY_RESCUER, rescuer);
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
