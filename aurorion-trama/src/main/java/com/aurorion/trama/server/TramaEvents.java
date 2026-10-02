package com.aurorion.trama.server;

import com.aurorion.core.character.*;
import com.aurorion.core.house.HouseChangedEvent;
import com.aurorion.core.house.HouseGate;
import com.aurorion.trama.AurorionTrama;
import com.aurorion.trama.progression.ProgressData;
import com.aurorion.trama.skill.Rating;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.*;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.*;

@EventBusSubscriber(modid = AurorionTrama.MOD_ID)
public final class TramaEvents {
    private TramaEvents() {}
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!HouseGate.installed()) AurorionTrama.LOGGER.warn("Trama suspensa: nenhum provedor de Casas instalado. Instale Aurorion Ethereal.");
        if (net.puffish.skillsmod.api.SkillsAPI.getCategory(TramaRuntime.CATEGORY).isEmpty())
            AurorionTrama.LOGGER.error("Categoria da Trama ausente. Confira os erros de configuracao do Pufferfish's Skills no log.");
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("trama")
                .executes(c -> TramaRuntime.open(c.getSource().getPlayerOrException()))
                .then(Commands.literal("progresso").executes(c -> TramaRuntime.progress(c.getSource().getPlayerOrException())))
                .then(Commands.literal("respec").executes(c -> TramaRuntime.respec(c.getSource().getPlayerOrException()))));
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p) TramaRuntime.tick(p);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) TramaRuntime.login(p);
    }
    @SubscribeEvent public static void named(CharacterNamedEvent event) {
        TramaRuntime.sync(event.player()); TramaRuntime.backfill(event.player());
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { TramaRuntime.logout(event.getEntity().getUUID()); }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { TramaRuntime.clear(); }
    @SubscribeEvent public static void reset(CharacterResetEvent event) {
        // Reservation ID makes a retried offline reset idempotent. Pufferfish is erased on next login too.
        var data = ProgressData.get(event.server());
        var entry = data.find(event.account());
        if (entry == null || !entry.character.equals(event.transaction().next().id()))
            data.replace(event.account(),event.transaction().next().id());
        if (event.player() != null) TramaRuntime.reset(event.player());
        else TramaRuntime.forget(event.account());
        // eraseCategory forces a full category erase on the next login, including old point sources.
    }
    @SubscribeEvent public static void house(HouseChangedEvent event) {
        if (event.player() == null) {
            for (var p : event.server().getPlayerList().getPlayers()) refresh(p);
        } else {
            var p = event.server().getPlayerList().getPlayer(event.player()); if (p != null) refresh(p);
        }
    }
    private static void refresh(ServerPlayer p) {
        TramaRuntime.refresh(p);
    }
    @SubscribeEvent public static void reload(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) for (var p : event.getPlayerList().getPlayers()) refresh(p);
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) { refresh((ServerPlayer)event.getEntity()); }
    @SubscribeEvent public static void advancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) TramaRuntime.advancement(p,event.getAdvancement().id());
    }
    @SubscribeEvent public static void mine(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() && event.getPlayer() instanceof ServerPlayer p) TramaRuntime.interaction(p,1);
    }
    @SubscribeEvent public static void block(PlayerInteractEvent.RightClickBlock event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer p) TramaRuntime.interaction(p,2);
    }
    @SubscribeEvent public static void item(PlayerInteractEvent.RightClickItem event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer p) TramaRuntime.interaction(p,4);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void beforeDamage(LivingDamageEvent.Pre event) { TramaCombat.before(event); }
    @SubscribeEvent public static void afterDamage(LivingDamageEvent.Post event) { TramaCombat.after(event); }
    @SubscribeEvent public static void breakSpeed(PlayerEvent.BreakSpeed event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !TramaRuntime.eligible(p)) return;
        var s = TramaRuntime.state(p);
        if (s.build.has("NO4") && (!event.getState().requiresCorrectToolForDrops() || p.getMainHandItem().isCorrectToolForDrops(event.getState()))) {
            var attribute = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.BLOCK_BREAK_SPEED);
            var modifier = attribute == null ? null : attribute.getModifier(net.minecraft.resources.ResourceLocation.parse("aurorion_trama:bbr"));
            double already = modifier == null ? 0 : modifier.amount();
            double total = Math.min(.10,s.build.effects(TramaRuntime.context(p,s)).get(Rating.BBR)+.02);
            event.setNewSpeed((float)(event.getNewSpeed()*(1+total)/(1+already)));
        }
    }
    @SubscribeEvent public static void projectile(EntityTickEvent.Pre event) {
        var entity = event.getEntity();
        if (entity.level().isClientSide() || entity.tickCount > 1 || !(entity instanceof Projectile projectile)
                || !entity.getType().is(TramaTags.PROJECTILES) || entity.getPersistentData().getBoolean("aurorion_trama_speed")) return;
        if (!(projectile.getOwner() instanceof ServerPlayer owner) || !TramaRuntime.eligible(owner)) return;
        if (projectile.getDeltaMovement().lengthSqr() == 0) return;
        var s = TramaRuntime.state(owner);
        double speed = s.build.effects(TramaRuntime.context(owner,s)).get(Rating.PSPD);
        projectile.setDeltaMovement(projectile.getDeltaMovement().scale(1+speed));
        entity.getPersistentData().putBoolean("aurorion_trama_speed",true);
    }
}
