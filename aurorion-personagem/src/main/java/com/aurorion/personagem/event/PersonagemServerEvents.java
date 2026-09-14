package com.aurorion.personagem.event;

import com.aurorion.core.character.CharacterGate;
import com.aurorion.personagem.AurorionPersonagem;
import com.aurorion.personagem.creation.CreationManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * O portao, na pratica.
 *
 * <p>A tela e modal, mas ela e do <b>cliente</b> — e um cliente pode nao ter o mod, ou ter a tela
 * fechada na marra. Por isso a retencao real e feita aqui, com o jogador em espectador e um punhado
 * de vetos curtos. Nenhum deles roda por tick: todos sao eventos de acontecimento, e cada um sai na
 * primeira linha quando o jogador nao esta retido.
 */
@EventBusSubscriber(modid = AurorionPersonagem.MOD_ID)
public final class PersonagemServerEvents {
    /** Uma varredura por segundo, e ela so faz algo quando ha alguem esperando. */
    private static final int SWEEP_TICKS = 20;

    private static int counter;

    private PersonagemServerEvents() {
    }

    /**
     * {@code LOWEST}: o Limbo (que decide em {@code HIGHEST} se a pessoa vai assistir ao epilogo) e
     * o Vidas ja falaram quando chegamos aqui. Perguntar o nome e a ultima coisa de um login.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CreationManager.onJoin(player);
        }
    }

    /** O respawn troca a entidade do jogador: a retencao precisa ser reaplicada na nova. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CreationManager.onJoin(player);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CreationManager.onLogout(player);
        }
    }

    /**
     * Um comando so passa: o do proprio criador.
     *
     * <p>Sem isto, quem esta sem personagem nao teria como responder pelo chat — e o cliente sem o
     * mod nao tem outra forma de responder.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCommand(CommandEvent event) {
        if (!(event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer player)) return;
        if (!CreationManager.isHeld(player)) return;

        var nodes = event.getParseResults().getContext().getNodes();
        String root = nodes.isEmpty() ? "" : nodes.get(0).getNode().getName();
        if (!CharacterGate.allowsCommand(root)) event.setCanceled(true);
    }

    /** Falar antes de existir nao faz sentido — e repete a pergunta para quem nao viu. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        if (!CreationManager.isHeld(player)) return;

        event.setCanceled(true);
        CreationManager.prompt(player);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTravelToDimension(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && CreationManager.isHeld(player)) {
            event.setCanceled(true);
        }
    }

    /** Espectador e a retencao. Sair dele antes de responder e sair da retencao. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onGameMode(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && CreationManager.isHeld(player)
                && event.getNewGameMode() != GameType.SPECTATOR) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("Escolha um personagem antes de voltar ao jogo."));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (++counter < SWEEP_TICKS) return;

        counter = 0;
        CreationManager.sweep(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        CreationManager.reset();
        counter = 0;
    }
}
