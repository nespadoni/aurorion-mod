package com.aurorion.profissoes.npc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * O corpo de um NPC de oficio. A entidade so guarda <b>qual</b> NPC ela e ({@code NpcId}); nome,
 * skin, servicos e loja vem do {@link NpcCatalog} — editar o JSON e recarregar muda todos os
 * corpos daquele id de uma vez, sem invocar de novo.
 *
 * <p>Mesmo contrato do Oraculo: nao anda, nao empurra, nao apanha, nao desaparece. A imunidade vem
 * de {@code setInvulnerable}, que ainda deixa {@code /kill} e {@code /npc remover} funcionarem.
 */
public class ProfessionNpcEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> NPC_ID =
            SynchedEntityData.defineId(ProfessionNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SKIN =
            SynchedEntityData.defineId(ProfessionNpcEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> SLIM =
            SynchedEntityData.defineId(ProfessionNpcEntity.class, EntityDataSerializers.BOOLEAN);

    public ProfessionNpcEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setInvulnerable(true);
        setSilent(true);
        setNoAi(true);
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.MOVEMENT_SPEED, 0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(NPC_ID, "");
        builder.define(SKIN, "");
        builder.define(SLIM, false);
    }

    public String npcId() { return entityData.get(NPC_ID); }
    public String skin() { return entityData.get(SKIN); }
    public boolean slimSkin() { return entityData.get(SLIM); }

    public void setNpcId(String id) {
        entityData.set(NPC_ID, id == null ? "" : id);
        applyConfig();
    }

    /** Copia do catalogo o que o cliente precisa ver. Somente no servidor. */
    public void applyConfig() {
        if (level().isClientSide()) return;
        var loaded = NpcCatalog.get(npcId());
        if (loaded == null) {
            setCustomName(Component.literal("NPC sem configuração: " + npcId()));
            entityData.set(SKIN, "");
            entityData.set(SLIM, false);
        } else {
            var definition = loaded.definition();
            setCustomName(Component.literal(definition.displayName()));
            entityData.set(SKIN, definition.skin());
            entityData.set(SLIM, definition.slimSkin());
        }
        setCustomNameVisible(true);
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("NpcId", npcId());
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(NPC_ID, tag.getString("NpcId"));
    }

    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer serverPlayer) NpcService.open(serverPlayer, this);
        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    @Override protected void registerGoals() { }
    @Override public boolean isPushable() { return false; }
    @Override protected void doPush(Entity entity) { }
    @Override protected void pushEntities() { }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean isPersistenceRequired() { return true; }
    @Override public boolean isNoGravity() { return true; }
    @Override public boolean canBeLeashed() { return false; }

    /** Olhar reativo barato: uma busca na lista de jogadores da dimensao a cada 5 ticks. */
    @Override public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level && tickCount % 5 == 0) {
            ServerPlayer nearest = null;
            double best = 25.0D;
            for (ServerPlayer player : level.players()) {
                if (!player.isAlive() || player.isSpectator()) continue;
                double distance = distanceToSqr(player);
                if (distance <= best) { best = distance; nearest = player; }
            }
            if (nearest != null) {
                lookAt(nearest, 30.0F, 30.0F);
                setYHeadRot(getYRot());
                setYBodyRot(getYRot());
            }
        }
    }
}
