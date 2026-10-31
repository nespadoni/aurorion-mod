package com.aurorion.menu.server;

import com.aurorion.core.compat.EmotecraftBridge;
import com.aurorion.menu.AurorionMenu;
import com.aurorion.menu.config.MenuConfig;
import com.aurorion.menu.network.MenuPayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Meditar: a pessoa senta (o emote "Meditar" do Emotecraft, para todos verem; sem o Emotecraft, a pose
 * sentada que o cliente desenha) e recupera vida devagar. Só no chão, fora de combate e sem monstro hostil
 * por perto. Para ao andar, levar dano, atacar ou quebrar bloco.
 *
 * <p>Custo: só quem medita é percorrido, a cada 10 ticks; o resto do servidor não paga nada.
 */
@EventBusSubscriber(modid = AurorionMenu.MOD_ID)
public final class Meditation {
    private static final class Session {
        final Vec3 anchor;
        long nextHeal;

        Session(Vec3 anchor, long nextHeal) {
            this.anchor = anchor;
            this.nextHeal = nextHeal;
        }
    }

    private static final double MOVE_LIMIT_SQR = 0.35 * 0.35;
    private static final Map<UUID, Session> MEDITATING = new HashMap<>();
    /** Último dano levado (tick do servidor), para o "fora de combate". */
    private static final Map<UUID, Long> LAST_HURT = new HashMap<>();

    private Meditation() {
    }

    public static boolean meditating(UUID player) {
        return MEDITATING.containsKey(player);
    }

    static void start(ServerPlayer player) {
        if (!MenuConfig.MEDITATION.get() || meditating(player.getUUID())) return;
        String refusal = refusal(player);
        if (refusal != null) {
            player.displayClientMessage(Component.literal(refusal).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        long now = player.server.getTickCount();
        MEDITATING.put(player.getUUID(), new Session(player.position(), now + MenuConfig.HEAL_SECONDS.get() * 20L));
        EmotecraftBridge.play(player, AurorionMenu.class, AurorionMenu.MEDITATION_EMOTE);
        // Meditar fecha o menu: o painel não fica no ar mesmo que o aviso de fechar chegue depois.
        MenuPresence.set(player, MenuPayloads.MENU, false);
        MenuPresence.set(player, MenuPayloads.MEDITATING, true);
        player.displayClientMessage(Component.literal("Você fecha os olhos e respira fundo.").withStyle(ChatFormatting.AQUA), true);
    }

    static void stop(ServerPlayer player, @Nullable String why) {
        if (MEDITATING.remove(player.getUUID()) == null) return;
        EmotecraftBridge.stop(player);
        MenuPresence.set(player, MenuPayloads.MEDITATING, false);
        if (why != null) player.displayClientMessage(Component.literal(why).withStyle(ChatFormatting.GRAY), true);
    }

    static void clear() {
        MEDITATING.clear();
        LAST_HURT.clear();
    }

    @Nullable
    private static String refusal(ServerPlayer player) {
        if (!player.onGround() || player.isInWater() || player.isPassenger()) return "Para meditar, fique parado no chão.";
        if (player.isSpectator() || !player.isAlive()) return "Agora não dá para meditar.";
        Long hurt = LAST_HURT.get(player.getUUID());
        if (hurt != null && player.server.getTickCount() - hurt < MenuConfig.COMBAT_SECONDS.get() * 20L)
            return "Você ainda está no calor do combate.";
        int radius = MenuConfig.HOSTILE_RADIUS.get();
        if (radius > 0) {
            AABB box = player.getBoundingBox().inflate(radius);
            if (!player.level().getEntitiesOfClass(Mob.class, box, mob -> mob instanceof Enemy && mob.isAlive()).isEmpty())
                return "Há perigo por perto; não dá para meditar.";
        }
        return null;
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (MEDITATING.isEmpty()) return;
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        if (now % 10 != 0) return;
        for (UUID id : new ArrayList<>(MEDITATING.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            Session session = MEDITATING.get(id);
            if (player == null || session == null) {
                MEDITATING.remove(id);
                continue;
            }
            if (player.position().distanceToSqr(session.anchor) > MOVE_LIMIT_SQR || !player.onGround() || !player.isAlive()) {
                stop(player, "Você se levanta.");
                continue;
            }
            if (now >= session.nextHeal) {
                session.nextHeal = now + MenuConfig.HEAL_SECONDS.get() * 20L;
                if (player.getHealth() < player.getMaxHealth()) player.heal((float) (double) MenuConfig.HEAL_AMOUNT.get());
                player.serverLevel().sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.0, player.getZ(),
                        6, .4, .4, .4, .4);
            }
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingDamageEvent.Post event) {
        // Só dor de verdade: golpe cancelado (o duelo, por exemplo) ou bola de neve não quebra a meditação.
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getNewDamage() <= 0) return;
        LAST_HURT.put(player.getUUID(), (long) player.server.getTickCount());
        if (LAST_HURT.size() > 512) LAST_HURT.values().removeIf(t -> player.server.getTickCount() - t > 6000);
        stop(player, "A dor quebra a sua concentração.");
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) stop(player, null);
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) stop(player, null);
    }
}
