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
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.*;
import net.neoforged.neoforge.event.entity.*;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

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
        if (source instanceof ServerPlayer attacker && victim instanceof ServerPlayer target && attacker != target
                && (!AreaApi.allowsFor(target, AreaRule.PVP) || !AreaApi.allowsFor(attacker, AreaRule.PVP))) {
            event.setCanceled(true);
        }
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
        if (event.getEntity() instanceof ServerPlayer p && blocked(p, event.getItemStack())) {
            event.setCancellationResult(InteractionResult.FAIL); event.setCanceled(true);
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
