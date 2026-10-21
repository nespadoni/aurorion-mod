package com.aurorion.utils.freeze;

import com.aurorion.core.character.CharacterResetEvent;
import com.aurorion.utils.AurorionUtils;
import com.aurorion.utils.abduction.AbductionManager;
import com.aurorion.utils.entity.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * Tudo que o congelado nao pode fazer, recusado no servidor. O cliente dele ja nem tenta (ver
 * {@code FreezeClientEvents}); isto aqui e a garantia contra cliente modificado e o que vale para
 * mob. So reacao a evento: nenhuma varredura, nenhum tick.
 *
 * <p>O que continua liberado de proposito: olhar em volta, chat, voz e uns poucos comandos de
 * conversa ({@link #FROZEN_COMMANDS}) — a pessoa precisa poder falar com a staff.
 */
@EventBusSubscriber(modid = AurorionUtils.MOD_ID)
public final class FreezeEvents {
    /**
     * O que um congelado ainda pode digitar: so conversa. Qualquer outro comando de jogador
     * ({@code /spawn}, {@code /home}, {@code /tpa}, {@code /back}, {@code /rtp} de outros mods) era uma
     * saida pela porta da frente. Staff (nivel 2) nao passa por esta lista.
     */
    private static final Set<String> FROZEN_COMMANDS = Set.of("msg", "tell", "w", "r", "reply", "me", "teammsg", "tm", "list", "help");

    /**
     * Teleporte de comando da staff liberado para a viagem de dimensao que vem logo em seguida, no
     * mesmo tick. O {@code /tp} para outra dimensao dispara primeiro o {@code EntityTeleportEvent} e
     * depois o {@code EntityTravelToDimensionEvent}; sem esta marca o segundo recusaria o que o
     * primeiro deixou passar.
     */
    @Nullable
    private static UUID commandTeleport;
    private static int commandTeleportTick = -1;

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

    /**
     * {@code /unfreeze}, {@code /effect clear}: a pessoa sai do freeze de vez. Sem tirar da lista de
     * "congelados sem prazo", um {@code /effect clear} soltava agora e congelava de novo no proximo
     * respawn.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRemoved(MobEffectEvent.Remove event) {
        if (event.getEffect().value() != FreezeEffects.FROZEN.get() || event.getEntity().level().isClientSide) return;
        // Purificacao de outro mod, feitico de limpeza, /effect clear: nao soltam o freeze da staff.
        if (FreezeManager.removalProtected(event.getEntity(), event.getEffectInstance())) {
            event.setCanceled(true);
            return;
        }
        FreezeManager.release(event.getEntity());
        if (event.getEntity() instanceof ServerPlayer player) FreezeData.get(player.server).remove(player.getUUID());
    }

    private static boolean isFrozenLiving(Entity entity) {
        return entity instanceof LivingEntity living && !entity.level().isClientSide && FreezeManager.isFrozen(living);
    }

    // --- Nao sai do lugar por teleporte, portal ou comando ------------------------------------------

    /**
     * Perola lancada antes do freeze, fruta do coro, teleporte de magia (o {@code SpellTeleportEvent}
     * do Iron's estende este evento), enderman congelado: nada disso tira ninguem do lugar. So o
     * {@code /tp} e o {@code /spreadplayers} da staff passam.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onTeleport(EntityTeleportEvent event) {
        Entity entity = event.getEntity();
        if (!isFrozenLiving(entity)) return;
        if (event instanceof EntityTeleportEvent.TeleportCommand || event instanceof EntityTeleportEvent.SpreadPlayersCommand) {
            if (entity.getServer() != null) {
                commandTeleport = entity.getUUID();
                commandTeleportTick = entity.getServer().getTickCount();
            }
            return;
        }
        event.setCanceled(true);
    }

    /**
     * Portal (inclusive os do {@code aurorion-portais}/{@code aurorion-mundos}) nao leva congelado.
     * Passam: o {@code /tp} da staff para outra dimensao (marcado em {@link #onTeleport}) e o feixe de
     * abducao, que e outra ferramenta da staff.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onTravelDimension(EntityTravelToDimensionEvent event) {
        Entity entity = event.getEntity();
        if (!isFrozenLiving(entity) || AbductionManager.releasing) return;
        if (entity.getUUID().equals(commandTeleport) && entity.getServer() != null
                && entity.getServer().getTickCount() == commandTeleportTick) return;
        event.setCanceled(true);
    }

    /**
     * Comando de jogador congelado: so a conversa de {@link #FROZEN_COMMANDS}. A raiz digitada e o que
     * conta, como no {@code aurorion-talk}; {@code /execute} ja exige nivel 2, e a staff passa direto.
     * Cancelar sem responder pareceria queda do servidor, entao a recusa vai para o chat.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCommand(CommandEvent event) {
        if (!(event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer player)) return;
        if (!FreezeManager.isFrozen(player) || player.hasPermissions(2)) return;
        var nodes = event.getParseResults().getContext().getNodes();
        String root = nodes.isEmpty() ? "" : nodes.get(0).getNode().getName();
        if (FROZEN_COMMANDS.contains(root)) return;
        event.setCanceled(true);
        player.sendSystemMessage(Component.translatable("aurorion_utils.congelado.comando").withStyle(ChatFormatting.GRAY));
    }

    /**
     * Prende na hora ao entrar e ao trocar de dimensao. O tick do efeito ja recoloca a montaria, mas
     * so a cada segundo — e esse segundo livre era suficiente para dar uns passos depois de relogar.
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && FreezeManager.isFrozen(player)) FreezeManager.pin(player);
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && FreezeManager.isFrozen(player)) FreezeManager.pin(player);
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
        if (!event.isDismounting() || event.getLevel().isClientSide) return;
        Entity vehicle = event.getEntityBeingMounted();
        if (vehicle == null || !(event.getEntityMounting() instanceof Player player)
                || !player.isAlive() || player.isRemoved() || !player.isShiftKeyDown()) return;
        boolean held;
        if (vehicle.getType() == ModEntities.FREEZE_ANCHOR.get()) {
            held = !FreezeManager.releasing && FreezeManager.isFrozen(player);
        } else if (vehicle.getType() == ModEntities.ABDUCTION_BEAM.get()) {
            // Mesmo furo no feixe: um Shift durante HOLD/ASCEND soltava a pessoa la no alto e ela caia.
            // Aqui o cliente honesto tambem desmonta com Shift, entao a recusa vale para todo mundo.
            held = !AbductionManager.releasing && AbductionManager.isHeld(player.getUUID());
        } else {
            return;
        }
        if (held) {
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
     * inventario); por isso ele volta para o inventario antes.
     *
     * <p>{@code Inventory.add} pode aceitar so parte da pilha: o que coube sai da copia, e so o resto
     * continua no item jogado. Antes, uma devolucao parcial deixava o item cair inteiro — duplicando o
     * que ja tinha voltado para o inventario.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onToss(ItemTossEvent event) {
        Player player = event.getPlayer();
        if (!FreezeManager.isFrozen(player) || player.level().isClientSide) return;
        ItemStack stack = event.getEntity().getItem().copy();
        player.getInventory().add(stack);
        if (stack.isEmpty()) event.setCanceled(true);
        else event.getEntity().setItem(stack);
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
