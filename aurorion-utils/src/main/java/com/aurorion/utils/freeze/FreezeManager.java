package com.aurorion.utils.freeze;

import com.aurorion.utils.entity.FreezeAnchorEntity;
import com.aurorion.utils.entity.ModEntities;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Congelar de verdade: o efeito {@code aurorion_utils:congelado} (o estado, que tudo consulta) mais
 * a montaria numa {@link FreezeAnchorEntity} parada (o que prende no lugar, inclusive no ar).
 *
 * <h2>Por que o freeze antigo "nao funcionava direito"</h2>
 *
 * <p>Ele so montava o jogador na ancora. No Minecraft, <b>agachar desmonta</b>: um Shift e o freeze
 * acabava. E nada impedia de bater, usar item, quebrar bloco, jogar item fora ou trocar de slot. Agora:
 *
 * <ul>
 *   <li>o cliente do congelado nao envia Shift (o teclado inteiro e zerado);</li>
 *   <li>o servidor recusa o desmonte mesmo assim (cliente modificado);</li>
 *   <li>toda acao com a mao e recusada no servidor ({@link FreezeEvents});</li>
 *   <li>mob congelado perde a IA enquanto durar — nao ataca, nao anda, nao olha.</li>
 * </ul>
 *
 * <p>Qualquer coisa que aplique o efeito congela por completo: a montaria e colocada no primeiro
 * evento do efeito, e o tick do efeito a recoloca se ela sumir.
 */
public final class FreezeManager {
    /** Sem prazo: so sai por {@code /unfreeze}. */
    public static final int FOREVER = MobEffectInstance.INFINITE_DURATION;

    private static final String KEY_NO_AI = "aurorion_utils:freeze_noai";

    /** Ligado so dentro de {@link #release}: o listener de desmonte deixa passar. */
    static boolean releasing;

    private FreezeManager() {
    }

    public static boolean isFrozen(LivingEntity entity) {
        return entity.hasEffect(FreezeEffects.FROZEN);
    }

    /**
     * @param ticks duracao, ou {@link #FOREVER}
     * @return {@code false} se ja estava congelado (seguro para chamar em lote)
     */
    public static boolean freeze(LivingEntity entity, int ticks, @Nullable Entity source) {
        boolean was = isFrozen(entity);
        entity.addEffect(new MobEffectInstance(FreezeEffects.FROZEN, ticks, 0, false, false, true), source);
        return !was;
    }

    /** @return {@code false} se nao estava congelado. */
    public static boolean unfreeze(LivingEntity entity) {
        if (!isFrozen(entity)) return false;
        entity.removeEffect(FreezeEffects.FROZEN);
        return true;
    }

    // --- Comando: freeze sem prazo sobrevive a morte --------------------------------------------

    public static boolean freezeByCommand(ServerPlayer player, int ticks) {
        if (ticks == FOREVER) FreezeData.get(player.server).add(player.getUUID());
        return freeze(player, ticks, null);
    }

    public static boolean unfreezeByCommand(ServerPlayer player) {
        FreezeData.get(player.server).remove(player.getUUID());
        return unfreeze(player);
    }

    // --- Montaria --------------------------------------------------------------------------------

    /**
     * Prende no lugar. Chamado quando o efeito entra e, uma vez por segundo, pelo tick dele.
     * No-op se ja esta preso, ou se esta no meio de uma abducao (o feixe ja segura a pessoa).
     */
    public static void pin(LivingEntity entity) {
        Entity vehicle = entity.getVehicle();
        if (vehicle != null && vehicle.getType() == ModEntities.FREEZE_ANCHOR.get()) return;
        if (vehicle != null && vehicle.getType() == ModEntities.ABDUCTION_BEAM.get()) return;
        if (!(entity.level() instanceof ServerLevel level) || !entity.isAlive()) return;

        if (vehicle != null) entity.stopRiding();
        FreezeAnchorEntity anchor = new FreezeAnchorEntity(ModEntities.FREEZE_ANCHOR.get(), level);
        anchor.setPos(entity.getX(), entity.getY(), entity.getZ());
        level.addFreshEntity(anchor);
        entity.startRiding(anchor, true);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.resetFallDistance();

        if (entity instanceof Mob mob && !mob.getPersistentData().contains(KEY_NO_AI)) {
            // Guarda como estava: mob de mapa que ja era NoAI continua NoAI depois.
            mob.getPersistentData().putBoolean(KEY_NO_AI, mob.isNoAi());
            mob.setNoAi(true);
            mob.setTarget(null);
        }
        if (entity instanceof ServerPlayer player) {
            player.stopUsingItem();
            player.setSprinting(false);
            player.setShiftKeyDown(false);
            player.stopFallFlying();
            player.closeContainer();
            player.displayClientMessage(Component.translatable("aurorion_utils.congelado"), true);
        } else {
            entity.stopUsingItem();
        }
    }

    /** Fim do efeito (prazo, {@code /unfreeze}, {@code /effect clear}): solta e devolve a IA. */
    static void release(LivingEntity entity) {
        Entity vehicle = entity.getVehicle();
        if (vehicle != null && vehicle.getType() == ModEntities.FREEZE_ANCHOR.get()) {
            releasing = true;
            try {
                entity.stopRiding();
            } finally {
                releasing = false;
            }
            vehicle.discard();
        }
        if (entity instanceof Mob mob && mob.getPersistentData().contains(KEY_NO_AI)) {
            mob.setNoAi(mob.getPersistentData().getBoolean(KEY_NO_AI));
            mob.getPersistentData().remove(KEY_NO_AI);
        }
        if (entity instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("aurorion_utils.descongelado"), true);
        }
    }
}
