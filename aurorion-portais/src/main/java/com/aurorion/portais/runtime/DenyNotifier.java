package com.aurorion.portais.runtime;

import com.aurorion.portais.AurorionPortais;
import com.aurorion.portais.config.TransitConfig;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * O "portal fechado" que o jogador ve, com limite de repeticao.
 *
 * <p>O limite nao e cosmetico: quem fica parado dentro de um portal do Nether trancado recebe a
 * negativa <b>a cada tick</b>, porque e a cada tick que o vanilla pergunta se pode atravessar. Sem
 * o intervalo minimo isso seriam 20 mensagens por segundo por jogador.
 *
 * <p>Vai para a actionbar, nunca para o chat: e uma resposta a uma acao que o jogador acabou de
 * fazer, some sozinha e nao empilha no historico de 90 pessoas (SDD §5.4).
 */
public final class DenyNotifier {
    private static final Map<UUID, Long> lastNotified = new HashMap<>();

    private DenyNotifier() {
    }

    public static void notify(ServerPlayer player, ResourceKey<Level> dimension, boolean leaving) {
        long now = System.currentTimeMillis();
        UUID uuid = player.getUUID();

        Long last = lastNotified.get(uuid);
        if (last != null && now - last < TransitConfig.DENY_MESSAGE_COOLDOWN_SECONDS.get() * 1000L) {
            return;
        }
        lastNotified.put(uuid, now);

        player.displayClientMessage(TransitAnnouncer.deniedMessage(dimension, leaving), true);
        player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.MASTER, 0.4F, 0.6F);

        if (TransitConfig.LOG_DENIALS.get()) {
            AurorionPortais.LOGGER.info("Viagem barrada: {} tentou {} '{}' (origem: '{}')",
                    player.getGameProfile().getName(),
                    leaving ? "sair de" : "entrar em",
                    dimension.location(),
                    player.level().dimension().location());
        }
    }

    /** O jogador saiu: a entrada dele nao pode continuar segurando memoria. */
    public static void forget(UUID player) {
        lastNotified.remove(player);
    }

    public static void clear() {
        lastNotified.clear();
    }
}
