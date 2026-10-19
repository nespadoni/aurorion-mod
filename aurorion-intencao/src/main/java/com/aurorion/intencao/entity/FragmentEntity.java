package com.aurorion.intencao.entity;

import com.aurorion.intencao.registry.IntencaoEntities;
import com.aurorion.intencao.server.IntencaoData;
import com.aurorion.intencao.server.Plane;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fragmento de memoria: um caco de eco flutuando no plano. Clique com o botao direito e ele diz uma
 * frase do tema, com um sussurro. E so cosmetico — da o que fazer e o que ler enquanto a mesa decide.
 *
 * <p>O giro e o balanco sao do renderizador; a entidade fica parada. Some sozinha quando a sessao da
 * arena acaba, conferindo a cada cinco segundos.
 */
public class FragmentEntity extends Entity {
    private static final int CHECK_INTERVAL = 100;
    /** Um fragmento por pessoa a cada 3 s: a frase precisa de tempo para ser lida. */
    private static final int COOLDOWN = 60;

    @Nullable
    private UUID session;
    /** So em memoria: perder isto num restart custa, no maximo, uma frase antes da hora. */
    private final Map<UUID, Integer> lastUse = new HashMap<>();

    public FragmentEntity(EntityType<? extends FragmentEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.blocksBuilding = false;
    }

    public static void spawn(ServerLevel level, UUID session, Vec3 at, float yaw) {
        FragmentEntity fragment = new FragmentEntity(IntencaoEntities.FRAGMENT.get(), level);
        fragment.moveTo(at.x, at.y, at.z, yaw, 0);
        fragment.session = session;
        level.addFreshEntity(fragment);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (tickCount % 15 == 0) {
                level().addParticle(ParticleTypes.END_ROD, getX() + (random.nextDouble() - .5) * .5,
                        getY() + .3 + random.nextDouble() * .4, getZ() + (random.nextDouble() - .5) * .5, 0, .01, 0);
            }
            return;
        }
        if (tickCount % CHECK_INTERVAL != 0 || !(level() instanceof ServerLevel server)) return;
        if (session == null || IntencaoData.get(server.getServer()).session(session) == null) discard();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer server) || session == null) return InteractionResult.sidedSuccess(level().isClientSide);
        Integer last = lastUse.get(player.getUUID());
        if (last != null && tickCount - last < COOLDOWN && tickCount >= last) return InteractionResult.CONSUME;
        lastUse.put(player.getUUID(), tickCount);
        Plane.touchFragment(server, session, this);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        // Precisa ser "mirável" para o clique direito chegar; ataque continua sem efeito.
        return true;
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
    protected void readAdditionalSaveData(CompoundTag tag) {
        session = tag.hasUUID("Session") ? tag.getUUID("Session") : null;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (session != null) tag.putUUID("Session", session);
    }
}
