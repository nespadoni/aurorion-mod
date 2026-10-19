package com.aurorion.magia.passive;

import com.aurorion.magia.network.PassiveMenuPayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * A tecla de passiva ({@code PassiveActionPayload}), a roda de passivas ({@code PassiveMenuPayloads}) e o
 * {@code /aurorion passivas usar}.
 *
 * <ul>
 *   <li><b>Toque rapido</b> na tecla: primeiro termina o que esta acontecendo (voltar ao corpo, sair do
 *       bolso); depois usa a passiva de representante; sem nenhuma, liga ou desliga a de interruptor.</li>
 *   <li><b>Segurar</b> a tecla abre a roda com todas as passivas de usar e de ligar que a pessoa tem.
 *       E por ela que quem tem mais de uma escolhe, sem comando.</li>
 * </ul>
 */
public final class PassiveActions {
    private PassiveActions() {
    }

    public static boolean usesKey(Passive passive) {
        return passive == Passive.QUANTUM || passive == Passive.ANACHRONIC || passive == Passive.VOID_POCKET
                || passive == Passive.WANING_MOON;
    }

    public static void activate(ServerPlayer player, boolean alternate) {
        if (!player.isAlive() || player.isSpectator()) return;
        if (AnachronicSight.isActive(player)) {
            AnachronicSight.end(player);
            return;
        }
        if (VoidPocket.isInside(player) && Passives.has(player, Passive.VOID_POCKET)) {
            VoidPocket.use(player, alternate);
            return;
        }
        for (Passive passive : Passive.values()) {
            if (usesKey(passive) && Passives.has(player, passive)) {
                use(player, passive, alternate);
                return;
            }
        }
        // Sem passiva de usar, o toque rapido liga e desliga a de interruptor (a Presenca Aterradora).
        for (Passive passive : Passive.values()) {
            if (passive.isToggleable() && Passives.has(player, passive)) {
                toggle(player, passive);
                return;
            }
        }
        player.displayClientMessage(Component.translatable("aurorion_magia.passiva_sem_tecla")
                .withStyle(ChatFormatting.DARK_GRAY), true);
    }

    /** O que entra na roda: as passivas da pessoa que respondem a tecla ou tem interruptor. */
    public static boolean inWheel(Passive passive) {
        return usesKey(passive) || passive.isToggleable();
    }

    /** Responde a roda com as passivas que a pessoa tem agora. Sem nenhuma, so o aviso de sempre. */
    public static void sendMenu(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) return;
        List<PassiveMenuPayloads.Entry> entries = new ArrayList<>();
        for (Passive passive : Passive.values()) {
            if (!inWheel(passive) || !Passives.has(player, passive)) continue;
            entries.add(new PassiveMenuPayloads.Entry(passive.key(), passive.isToggleable(), inUse(player, passive)));
        }
        if (entries.isEmpty()) {
            player.displayClientMessage(Component.translatable("aurorion_magia.passiva_sem_tecla")
                    .withStyle(ChatFormatting.DARK_GRAY), true);
            return;
        }
        PacketDistributor.sendToPlayer(player, new PassiveMenuPayloads.Menu(entries));
    }

    /** A escolha da roda. O id vem do cliente: so vale passiva que existe, esta na roda e a pessoa tem. */
    public static void select(ServerPlayer player, String key, boolean alternate) {
        if (!player.isAlive() || player.isSpectator()) return;
        for (Passive passive : Passive.values()) {
            if (!passive.key().equals(key)) continue;
            if (!inWheel(passive) || !Passives.has(player, passive)) return;
            if (passive.isToggleable()) toggle(player, passive);
            else use(player, passive, alternate);
            return;
        }
    }

    private static boolean inUse(ServerPlayer player, Passive passive) {
        if (passive.isToggleable()) return Passives.isActive(player, passive);
        return switch (passive) {
            case ANACHRONIC -> AnachronicSight.isActive(player);
            case VOID_POCKET -> VoidPocket.isInside(player);
            default -> false;
        };
    }

    private static void toggle(ServerPlayer player, Passive passive) {
        boolean on = !Passives.isActive(player, passive);
        Passives.setActive(player, passive, on);
        player.displayClientMessage(Component.translatable(on
                        ? "commands.aurorion_magia.passiva_ligada"
                        : "commands.aurorion_magia.passiva_desligada", passive.displayName())
                .withStyle(on ? ChatFormatting.GOLD : ChatFormatting.GRAY), true);
    }

    /** @return se a passiva responde a tecla e a pessoa a tem */
    public static boolean use(ServerPlayer player, Passive passive, boolean alternate) {
        if (!usesKey(passive) || !Passives.has(player, passive)) return false;
        switch (passive) {
            case QUANTUM -> QuantumLeap.use(player, alternate);
            case ANACHRONIC -> {
                if (AnachronicSight.isActive(player)) AnachronicSight.end(player);
                else AnachronicSight.start(player);
            }
            case VOID_POCKET -> VoidPocket.use(player, alternate);
            case WANING_MOON -> WaningMoon.report(player);
            default -> {
                return false;
            }
        }
        return true;
    }
}
