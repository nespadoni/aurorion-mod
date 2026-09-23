package com.aurorion.utils.freeze;

import com.aurorion.core.character.CharacterResetEvent;
import com.aurorion.utils.AurorionUtils;
import com.aurorion.utils.entity.ModEntities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Tudo que o congelado nao pode fazer, recusado no servidor. O cliente dele ja nem tenta (ver
 * {@code FreezeClientEvents}); isto aqui e a garantia contra cliente modificado e o que vale para
 * mob. So reacao a evento: nenhuma varredura, nenhum tick.
 *
 * <p>O que continua liberado de proposito: olhar em volta, chat e comandos (a pessoa precisa poder
 * falar com a staff), e voz.
 */
@EventBusSubscriber(modid = AurorionUtils.MOD_ID)
public final class FreezeEvents {
    private FreezeEvents() {
    }

    // --- Entrar e sair do estado ----------------------------------------------------------------

    /** Qualquer origem do efeito (comando, magia, /effect) prende na hora. */
    @SubscribeEvent
    public static void onAdded(MobEffectEvent.Added event) {
        if (isFrozenEffect(event.getEffectInstance()) && !event.getEntity().level().isClientSide) {
            FreezeManager.pin(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onExpired(MobEffectEvent.Expired event) {
        if (isFrozenEffect(event.getEffectInstance()) && !event.getEntity().level().isClientSide) {
            FreezeManager.release(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onRemoved(MobEffectEvent.Remove event) {
        if (event.getEffect().value() == FreezeEffects.FROZEN.get() && !event.getEntity().level().isClientSide) {
            FreezeManager.release(event.getEntity());
        }
    }

    /** Congelado por comando sem prazo continua congelado depois de morrer. */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && FreezeData.get(player.server).contains(player.getUUID())) {
            FreezeManager.freeze(player, FreezeManager.FOREVER, null);
        }
    }

    /** Personagem novo nao herda o freeze do anterior. */
    @SubscribeEvent
    public static void onCharacterReset(CharacterResetEvent event) {
        FreezeData.get(event.server()).remove(event.account());
        ServerPlayer player = event.player();
        if (player != null) FreezeManager.unfreeze(player);
    }

    // --- Nao sai da ancora ----------------------------------------------------------------------

    /**
     * O desmonte que o freeze antigo deixava passar: agachar. O cliente honesto nem manda o Shift;
     * este e o cancelamento para quem manda mesmo assim. So o desmonte "por Shift" e barrado —
     * desconexao, morte e teleporte da staff continuam soltando normalmente, e o tick do efeito
     * prende de novo onde a pessoa estiver.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDismount(EntityMountEvent event) {
        if (!event.isDismounting() || FreezeManager.releasing || event.getLevel().isClientSide) return;
        Entity vehicle = event.getEntityBeingMounted();
        if (vehicle == null || vehicle.getType() != ModEntities.FREEZE_ANCHOR.get()) return;
        if (event.getEntityMounting() instanceof Player player && player.isAlive() && !player.isRemoved()
                && player.isShiftKeyDown() && FreezeManager.isFrozen(player)) {
            player.setShiftKeyDown(false);
            event.setCanceled(true);
        }
    }

    // --- Nenhuma acao com as maos -----------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        deny(event, event.getEntity(), event);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        deny(event, event.getEntity(), event);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        deny(event, event.getEntity(), event);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteractEntitySpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        deny(event, event.getEntity(), event);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (FreezeManager.isFrozen(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(AttackEntityEvent event) {
        if (FreezeManager.isFrozen(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (FreezeManager.isFrozen(event.getPlayer())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof LivingEntity living && FreezeManager.isFrozen(living)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onStartUsing(LivingEntityUseItemEvent.Start event) {
        if (FreezeManager.isFrozen(event.getEntity())) event.setCanceled(true);
    }

    /**
     * Jogar item fora. Cancelar o evento sozinho apagaria o item (o NeoForge ja o tirou do
     * inventario); por isso ele volta para o inventario antes. Se nao couber, cai normalmente.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onToss(ItemTossEvent event) {
        Player player = event.getPlayer();
        if (!FreezeManager.isFrozen(player) || player.level().isClientSide) return;
        ItemStack stack = event.getEntity().getItem().copy();
        if (player.getInventory().add(stack)) event.setCanceled(true);
    }

    /** Mob congelado nao tem IA; isto cobre o resto (projetil ja lancado por um congelado, etc.). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getDirectEntity() instanceof LivingEntity attacker && FreezeManager.isFrozen(attacker)) {
            event.setCanceled(true);
        }
    }

    private static void deny(ICancellableEvent cancellable, Player player, PlayerInteractEvent event) {
        if (!FreezeManager.isFrozen(player)) return;
        cancellable.setCanceled(true);
        if (event instanceof PlayerInteractEvent.RightClickBlock block) block.setCancellationResult(InteractionResult.FAIL);
        if (event instanceof PlayerInteractEvent.RightClickItem item) item.setCancellationResult(InteractionResult.FAIL);
        if (event instanceof PlayerInteractEvent.EntityInteract entity) entity.setCancellationResult(InteractionResult.FAIL);
        if (event instanceof PlayerInteractEvent.EntityInteractSpecific specific) specific.setCancellationResult(InteractionResult.FAIL);
    }

    private static boolean isFrozenEffect(@Nullable MobEffectInstance instance) {
        if (instance == null) return false;
        MobEffect effect = instance.getEffect().value();
        return effect == FreezeEffects.FROZEN.get();
    }
}
