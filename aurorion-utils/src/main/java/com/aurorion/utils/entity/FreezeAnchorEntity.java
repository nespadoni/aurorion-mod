package com.aurorion.utils.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * "Congelar" um jogador e monta-lo numa entidade invisivel que nunca se move — montaria e um
 * mecanismo nativo do jogo, nao um {@code MobEffect}: nao anda, nao pula, mas continua olhando
 * livremente ao redor, e nada como um balde de leite tem qualquer efeito sobre isso (de proposito
 * — ver {@code com.aurorion.utils.freeze.FreezeManager}).
 *
 * <p>Se limpa sozinha: perder o passageiro (desconexao, morte, {@code /unfreeze}) sem que alguem
 * chame {@link Entity#discard()} explicitamente deixaria uma ancora fantasma no mundo — por isso
 * ela se descarta no primeiro tick em que percebe que ficou sem ninguem montado.</p>
 */
public class FreezeAnchorEntity extends Entity {

    public FreezeAnchorEntity(EntityType<? extends FreezeAnchorEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > 1 && getPassengers().isEmpty()) {
            discard();
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // Sem estado nenhum pra sincronizar — a ancora nao tem visual, so existe pra ser montada.
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.0;
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
