package com.aurorion.magia.spell;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * O cooldown das magias que ficam <b>ligadas ate a segunda conjuracao</b> (Vinculum Carnificis,
 * Tempus Sistere).
 *
 * <p>O Iron's poe o cooldown logo depois do {@code onCast}, em toda conjuracao. Numa magia de
 * ligar e desligar isso trava o lado errado: o Tempus tem 120 s de cooldown, e quem acabou de parar
 * o tempo nao conseguiria solta-lo por dois minutos. Aqui a conjuracao que <b>liga</b> sai sem
 * cooldown, e a que desliga paga o cooldown inteiro — ninguem liga, desliga e liga de novo em
 * sequencia, e ninguem fica preso a propria magia.
 *
 * <p>O {@code onCast} marca o conjurador com o tick do jogo; o {@code SpellCooldownAddedEvent.Pre}
 * que o Iron's dispara na mesma chamada le a marca e cancela. A marca so vale no mesmo tick: quando
 * o cooldown nao chega a ser posto (criativo com o cooldown desligado no config do Iron's), ela nao
 * sobra para apagar o cooldown de uma conjuracao futura.
 *
 * <p>So a thread do servidor mexe aqui.
 */
public final class ToggleCooldown {
    private static final Map<UUID, Long> SKIP = new HashMap<>();

    private ToggleCooldown() {
    }

    /** A conjuracao que esta terminando ligou a magia: ela nao abre cooldown. */
    public static void skipNext(LivingEntity caster) {
        if (caster instanceof ServerPlayer player) SKIP.put(player.getUUID(), player.level().getGameTime());
    }

    /** Chamado pelo evento de cooldown do Iron's. {@code true} = cancelar este cooldown. */
    public static boolean consume(Player player) {
        Long at = SKIP.remove(player.getUUID());
        return at != null && at == player.level().getGameTime();
    }

    public static void clear() {
        SKIP.clear();
    }
}
