package com.aurorion.integracao.death;

import com.aurorion.core.death.DeathId;
import com.aurorion.core.integration.GameFacts;
import com.aurorion.core.lives.LivesGate;
import com.aurorion.integracao.AurorionIntegracao;
import com.aurorion.integracao.bridge.FactBridge;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Cada morte confirmada de um jogador vira um fato para a linha do tempo do site.
 *
 * <h2>Sem tick</h2>
 *
 * <p>Nada aqui roda "por tick" nem "por jogador". O custo existe so quando alguem morre:
 * <ol>
 *   <li>Em {@code HIGHEST}, enquanto o {@code LivingDeathEvent} esta sendo despachado, guardamos so
 *       dados pequenos e imutaveis — id da morte, causa, quem matou, dimensao. O matador pode nem
 *       existir mais depois.</li>
 *   <li>Agendamos uma unica tarefa com {@code server.tell(new TickTask(...))}. Ela so roda depois que
 *       o despacho do evento terminou, quando todos os mods ja decidiram se a morte vale: totem,
 *       PlayerRevive e o fim definitivo do Limbo cancelam dentro do proprio despacho.</li>
 *   <li>Na tarefa, morte cancelada e descartada; morte confirmada vira fato, ja com as vidas que
 *       sobraram (o {@code aurorion-vidas} desconta em {@code LOW}, no mesmo despacho).</li>
 * </ol>
 *
 * <p>O id do fato e o {@link DeathId} — o mesmo do historico de mortes e do espolio do Limbo. Um
 * reenvio da mesma morte nunca vira duas entradas no site.
 *
 * <p>Criativo, espectador e jogadores falsos (maquinas de mod) nao geram registro: e a staff
 * testando, nao a historia acontecendo. A regra e a mesma que o {@code aurorion-vidas} usa para nao
 * cobrar vida.
 */
@EventBusSubscriber(modid = AurorionIntegracao.MOD_ID)
public final class DeathFacts {
    private record Capture(UUID deathId, Instant at, String dimension, String damageType,
                           String killerType, String killerName) {
    }

    private DeathFacts() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!FactBridge.active()) return;
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        if (player.isCreative() || player.isSpectator()) return;
        try {
            MinecraftServer server = player.server;
            Capture capture = capture(player, event.getSource());
            server.tell(new TickTask(server.getTickCount(), () -> confirm(server, event, player.getUUID(), capture)));
        } catch (RuntimeException | LinkageError e) {
            // Registrar a morte no site nunca pode atrapalhar a morte em si.
            AurorionIntegracao.LOGGER.warn("Nao consegui capturar a morte de {} para o site", player.getUUID(), e);
        }
    }

    private static Capture capture(ServerPlayer player, DamageSource source) {
        String damageType = source.typeHolder().unwrapKey()
                .map(key -> key.location().toString())
                .orElse("");
        Entity killer = source.getEntity();
        String killerType = "";
        String killerName = "";
        if (killer != null && killer != player) {
            killerType = EntityType.getKey(killer.getType()).toString();
            if (killer instanceof ServerPlayer other) {
                // O personagem de quem matou, nunca o nick da conta.
                killerName = GameFacts.subject(player.server, other.getUUID()).characterName();
            } else {
                Component custom = killer.getCustomName();
                if (custom != null) killerName = custom.getString();
            }
        }
        return new Capture(DeathId.of(player), Instant.now(),
                player.level().dimension().location().toString(), damageType, killerType, killerName);
    }

    private static void confirm(MinecraftServer server, LivingDeathEvent event, UUID player, Capture capture) {
        if (event.isCanceled() || !GameFacts.installed()) return;
        try {
            GameFacts.Subject subject = GameFacts.subject(server, player);
            JsonObject payload = new JsonObject();
            subject.writeTo(payload);
            payload.addProperty("dimension", capture.dimension());
            if (!capture.damageType().isEmpty()) payload.addProperty("damage_type", capture.damageType());
            if (!capture.killerType().isEmpty()) payload.addProperty("killer_type", capture.killerType());
            if (!capture.killerName().isEmpty()) payload.addProperty("killer_name", capture.killerName());
            int lives = LivesGate.of(server, player);
            if (lives != LivesGate.UNKNOWN) payload.addProperty("lives_remaining", lives);

            String id = capture.deathId().toString();
            GameFacts.publish(new GameFacts.Fact(id, GameFacts.PLAYER_DEATH, capture.at(),
                    subject.profile(), subject.character(), "death", id, payload));
        } catch (RuntimeException e) {
            AurorionIntegracao.LOGGER.warn("Nao consegui publicar a morte {} para o site", capture.deathId(), e);
        }
    }
}
