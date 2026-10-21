package com.aurorion.areas.server;

import com.aurorion.areas.AurorionAreas;
import com.aurorion.areas.api.AreaApi;
import com.aurorion.areas.command.AreaCommands;
import com.aurorion.areas.compat.LsoThirstCompat;
import com.aurorion.areas.config.AreasConfig;
import com.aurorion.areas.data.AreaData;
import com.aurorion.areas.profile.AreaProfiles;
import com.aurorion.areas.rules.*;
import com.aurorion.core.character.CharacterResetEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.*;
import net.neoforged.neoforge.event.entity.*;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

@EventBusSubscriber(modid = AurorionAreas.MOD_ID)
public final class AreaEvents {
    private static final ResourceLocation HEALTH = ResourceLocation.parse("aurorion_areas:spawn_health");
    private static final String STRENGTH = "aurorion_areas:spawn_damage";
    private AreaEvents() {}
    @SubscribeEvent public static void reload(AddReloadListenerEvent event) {
        event.addListener(AreaProfiles.RULES.listener());
        event.addListener(AreaProfiles.AMBIENCE.listener());
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) { AreaCommands.register(event.getDispatcher()); }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { AreaRuntime.clear(); AreaCommands.clear(); }
    @SubscribeEvent public static void reset(CharacterResetEvent event) {
        AreaData.get(event.server()).clearCharacter(event.account());
        AreaRuntime.forget(event.account());
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) AreaRuntime.logout(player);
        else AreaRuntime.forget(event.getEntity().getUUID());
        AreaCommands.forget(event.getEntity().getUUID());
        LsoThirstCompat.forget(event.getEntity());
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) { AreaRuntime.forget(event.getEntity().getUUID()); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) { AreaRuntime.forget(event.getEntity().getUUID()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        AreaRuntime.forget(event.getEntity().getUUID()); AreaCommands.forget(event.getEntity().getUUID());
    }
    // Pre ensures an in-progress cast is interrupted before its next player tick.
    @SubscribeEvent public static void tick(PlayerTickEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AreaRuntime.tick(player); AreaCommands.tickPreview(player);
        }
    }
    public static boolean hostile(Entity entity) {
        return !entity.getType().is(AreaTags.EXEMPT_ENTITIES)
                && !entity.getTags().contains("aurorion_areas_npc")
                && (entity instanceof Enemy || entity.getType().getCategory() == MobCategory.MONSTER
                    || entity.getType().is(AreaTags.HOSTILES));
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void spawn(MobSpawnEvent.PositionCheck event) {
        var level = event.getLevel().getLevel();
        // World generation may call this off-thread. EntityJoin performs the main-thread check.
        if (!level.getServer().isSameThread() || !hostile(event.getEntity())) return;
        if (!AreaApi.allowsAt(level, event.getX(), event.getY(), event.getZ(), null, AreaRule.HOSTILE_SPAWN.key()))
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void join(EntityJoinLevelEvent event) {
        if (!AreasConfig.ENABLED.get() || event.loadedFromDisk() || !(event.getLevel() instanceof ServerLevel level)
                || !level.getServer().isSameThread() || !(event.getEntity() instanceof Mob mob) || !hostile(mob)) return;
        if (mob.getPersistentData().contains(STRENGTH)) return; // Previously admitted entity, not a new spawn.
        if (!AreaApi.allowsAt(level, mob.getX(), mob.getY(), mob.getZ(), null, AreaRule.HOSTILE_SPAWN.key())) {
            event.setCanceled(true); return;
        }
        // Spawn-only allocation. Persistent marker avoids stacking on transfer/reload.
        var rules = new ResolvedRules();
        AreaData.get(level.getServer()).resolve(level.dimension().location(), mob.getX(), mob.getY(), mob.getZ(), null, rules);
        var health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health != null && rules.mobHealth() != 1) {
            float fraction = mob.getHealth() / mob.getMaxHealth();
            health.removeModifier(HEALTH);
            health.addPermanentModifier(new AttributeModifier(HEALTH, rules.mobHealth() - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            mob.setHealth(mob.getMaxHealth() * fraction);
        }
        mob.getPersistentData().putDouble(STRENGTH, rules.mobDamage());
    }
    @SubscribeEvent public static void travel(EntityTravelToDimensionEvent event) {
        // Saved mobs from before this module may have no marker. A transfer is not a spawn.
        if (AreasConfig.ENABLED.get() && event.getEntity() instanceof Mob mob
                && mob.level() instanceof ServerLevel && hostile(mob) && !mob.getPersistentData().contains(STRENGTH))
            mob.getPersistentData().putDouble(STRENGTH, 1);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damage(LivingIncomingDamageEvent event) {
        if (!AreasConfig.ENABLED.get() || !(event.getEntity().level() instanceof ServerLevel)) return;
        var source = event.getSource().getEntity();
        var victim = event.getEntity();
        if (source != null && hostile(source)) {
            if (!AreaApi.allowsFor(victim, AreaRule.HOSTILE_DAMAGE)) {
                event.setCanceled(true); return;
            }
            double factor = source.getPersistentData().getDouble(STRENGTH);
            if (factor >= .1 && factor <= 20) event.setAmount((float) (event.getAmount() * factor));
        }
        if (victim instanceof ServerPlayer target && pvpDenied(target, source != null ? source : event.getSource().getDirectEntity())) {
            event.setCanceled(true);
        }
    }
    /**
     * PvP indireto conta como PvP: lobo, flecha, TNT, raio do tridente, area de pocao. Antes so o soco
     * de jogador era barrado, e qualquer pet atravessava uma area segura.
     *
     * <p>Dono offline (o lobo de quem deslogou) ainda e um jogador: a posicao da vitima basta para
     * negar. Atacante online tambem precisa estar onde PvP vale, como no golpe direto. Machucar a si
     * mesmo (a propria TNT) continua sendo problema de quem acendeu.
     */
    private static boolean pvpDenied(ServerPlayer target, @Nullable Entity cause) {
        if (cause == null) return false;
        UUID responsible = AreaProtection.playerBehind(cause);
        if (responsible == null || responsible.equals(target.getUUID())) return false;
        if (!AreaApi.allowsFor(target, AreaRule.PVP)) return true;
        ServerPlayer attacker = cause instanceof ServerPlayer direct && direct.getUUID().equals(responsible)
                ? direct : target.server.getPlayerList().getPlayer(responsible);
        return attacker != null && !AreaApi.allowsFor(attacker, AreaRule.PVP);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void nonlethal(LivingDamageEvent.Pre event) {
        if (event.getSource().is(AreaRuntime.PHANTOM))
            event.setNewDamage(Math.max(0, Math.min(event.getNewDamage(), event.getEntity().getHealth() - 1)));
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void target(LivingChangeTargetEvent event) {
        var target = event.getNewAboutToBeSetTarget();
        // Dispara a cada reavaliacao de alvo de cada mob: a config vem antes das consultas de tag.
        if (target == null || !AreasConfig.ENABLED.get() || !(target.level() instanceof ServerLevel)
                || !hostile(event.getEntity())) return;
        if (!AreaApi.allowsFor(target, AreaRule.HOSTILE_DAMAGE)) event.setNewAboutToBeSetTarget(null);
    }
    private static boolean blocked(ServerPlayer player, ItemStack item) {
        AreaRule rule = item.is(AreaTags.FLIGHT_ITEMS) ? AreaRule.FLIGHT
                : item.is(AreaTags.MAGIC_ITEMS) ? AreaRule.MAGIC : null;
        if (rule == null) return false;
        // An item may belong to both tags; both rules must permit it.
        if (rule == AreaRule.FLIGHT && AreaApi.allows(player, rule) && item.is(AreaTags.MAGIC_ITEMS)) rule = AreaRule.MAGIC;
        if (AreaApi.allows(player, rule)) return false;
        AreaRuntime.denyNotice(player, rule); return true;
    }
    // Clique direito e um dos eventos mais frequentes de um servidor cheio, entao o modulo usa UM
    // listener por evento. A ponte de agua do LSO entra aqui em vez de num listener proprio: dois
    // listeners a mais seriam duas chamadas extras por clique de cada um dos 80 jogadores, para
    // fazer o que cabe numa linha no listener que ja existia.
    @SubscribeEvent public static void item(PlayerInteractEvent.RightClickItem event) {
        LsoThirstCompat.beginInteraction(event.getEntity(), null);
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (blocked(p, event.getItemStack())
                || event.getItemStack().getItem() instanceof BucketItem && event.getLevel() instanceof ServerLevel level
                && AreaProtection.deniesBucket(level, p) && protectionNotice(p)) {
            event.setCancellationResult(InteractionResult.FAIL); event.setCanceled(true);
        }
    }
    /** Sempre verdadeiro: so existe para o aviso caber na mesma condicao que cancela. */
    private static boolean protectionNotice(ServerPlayer player) {
        AreaRuntime.protectionNotice(player); return true;
    }
    // --- Protecao de blocos (ver AreaProtection) --------------------------------------------------
    /**
     * Separado do listener {@link #block} de proposito, e em {@code HIGHEST}: o Carry On pega bau do
     * chao neste mesmo evento em {@code HIGH} (o motivo do {@code aurorion-magia} usar {@code HIGHEST}
     * para o lacre). Num listener {@code NORMAL}, sair carregando o bau da casa alheia seria a brecha
     * obvia. A saida antecipada e a mesma de todo o resto: dimensao sem protecao nao consulta nada.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void protectUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer p && event.getLevel() instanceof ServerLevel level
                && AreaProtection.deniesUse(level, p, event.getPos())) {
            event.setCancellationResult(InteractionResult.FAIL); event.setCanceled(true);
            AreaRuntime.protectionNotice(p);
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void protectBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getPlayer() instanceof ServerPlayer p
                && !AreaProtection.mayAct(level, p, event.getPos(), ProtectionRule.BUILD)) {
            event.setCanceled(true); AreaRuntime.protectionNotice(p);
        }
    }
    /**
     * Pega tambem o que o NeoForge captura como "colocar": isqueiro, enxada, machado descascando,
     * farinha de osso — todo {@code useOn} de item passa pelo snapshot de blocos e cai aqui.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void protectPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer p)) return;
        boolean denied;
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            denied = false;
            for (var snapshot : multi.getReplacedBlockSnapshots()) {
                if (!AreaProtection.mayAct(level, p, snapshot.getPos(), ProtectionRule.BUILD)) { denied = true; break; }
            }
        } else {
            denied = !AreaProtection.mayAct(level, p, event.getPos(), ProtectionRule.BUILD);
        }
        if (denied) { event.setCanceled(true); AreaRuntime.protectionNotice(p); }
    }
    /** Quebrar moldura, quadro ou suporte de armadura e "construir". */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void protectDecorationAttack(AttackEntityEvent event) {
        if (AreaProtection.decoration(event.getTarget()) && event.getEntity() instanceof ServerPlayer p
                && event.getTarget().level() instanceof ServerLevel level
                && !AreaProtection.mayAct(level, p, event.getTarget().blockPosition(), ProtectionRule.BUILD)) {
            event.setCanceled(true); AreaRuntime.protectionNotice(p);
        }
    }
    /** Girar/tirar item da moldura. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void protectDecorationUse(PlayerInteractEvent.EntityInteract event) {
        if (AreaProtection.decoration(event.getTarget()) && event.getEntity() instanceof ServerPlayer p
                && event.getLevel() instanceof ServerLevel level
                && !AreaProtection.mayAct(level, p, event.getTarget().blockPosition(), ProtectionRule.INTERACT)) {
            event.setCancellationResult(InteractionResult.FAIL); event.setCanceled(true);
            AreaRuntime.protectionNotice(p);
        }
    }
    /** Tirar/por armadura no suporte (o suporte usa {@code interactAt}, que e este evento). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void protectDecorationUseAt(PlayerInteractEvent.EntityInteractSpecific event) {
        if (AreaProtection.decoration(event.getTarget()) && event.getEntity() instanceof ServerPlayer p
                && event.getLevel() instanceof ServerLevel level
                && !AreaProtection.mayAct(level, p, event.getTarget().blockPosition(), ProtectionRule.INTERACT)) {
            event.setCancellationResult(InteractionResult.FAIL); event.setCanceled(true);
            AreaRuntime.protectionNotice(p);
        }
    }
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void protectFromExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level && AreaProtection.guarded(level)) {
            AreaProtection.filterExplosion(level, event.getExplosion(), event.getAffectedBlocks(), event.getAffectedEntities());
        }
    }
    /**
     * Lava acendendo fogo e lava/agua virando pedra. Trocar o resultado pelo estado atual e o mesmo
     * que cancelar, e funciona igual se outro mod ja tiver mexido no evento. O escoamento em si (agua
     * e lava andando) e barrado no mixin {@code FlowingFluidSpreadMixin}.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void protectFromFluid(BlockEvent.FluidPlaceBlockEvent event) {
        if (event.getLevel() instanceof ServerLevel level && AreaProtection.fluidCrosses(level, event.getLiquidPos(), event.getPos())) {
            event.setNewState(event.getState());
        }
    }
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void protectFromPiston(PistonEvent.Pre event) {
        if (event.getLevel() instanceof ServerLevel level && AreaProtection.pistonCrosses(level, event)) {
            event.setCanceled(true);
        }
    }
    @SubscribeEvent public static void block(PlayerInteractEvent.RightClickBlock event) {
        LsoThirstCompat.beginInteraction(event.getEntity(), event.getPos());
        if (event.getEntity() instanceof ServerPlayer p && blocked(p, event.getItemStack())) {
            event.setCancellationResult(InteractionResult.FAIL); event.setCanceled(true);
        }
    }
    @SubscribeEvent public static void use(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof ServerPlayer p && blocked(p, event.getItem())) event.setCanceled(true);
    }
    @SubscribeEvent public static void using(LivingEntityUseItemEvent.Tick event) {
        if (event.getEntity() instanceof ServerPlayer p && blocked(p, event.getItem())) event.setCanceled(true);
    }
    @SubscribeEvent public static void mount(EntityMountEvent event) {
        if (event.isMounting() && event.getEntityMounting() instanceof ServerPlayer player
                && AreaTags.isFlightMount(event.getEntityBeingMounted())
                && !AreaApi.allows(player, AreaRule.FLIGHT)) {
            event.setCanceled(true); AreaRuntime.denyNotice(player, AreaRule.FLIGHT);
        }
    }
}
