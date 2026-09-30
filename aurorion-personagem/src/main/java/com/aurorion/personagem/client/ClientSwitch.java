package com.aurorion.personagem.client;

import com.aurorion.personagem.network.SwitchingCharacterPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.jetbrains.annotations.Nullable;

/**
 * A reconexao automatica da troca de personagem.
 *
 * <p>O servidor avisa ({@link SwitchingCharacterPayload}) e desconecta. Guardamos o servidor atual, e
 * quando a tela de desconectado aparecer, esperamos um instante — o servidor precisa terminar de
 * salvar o personagem que saiu — e conectamos de novo no mesmo endereco. Do lado de la, o login ja
 * entra como o outro personagem.
 *
 * <p>So cortesia: sem o mod, a pessoa ve a mesma mensagem e entra de novo na mao.
 */
public final class ClientSwitch {
    /** Espera antes de reconectar: da tempo de o servidor gravar o jogador que acabou de sair. */
    private static final int DELAY_TICKS = 40;
    /** Se a tela de desconectado nao aparecer neste prazo, esquece — algo diferente aconteceu. */
    private static final int GIVE_UP_TICKS = 20 * 30;

    @Nullable
    private static ServerData server;
    private static int waited;
    private static int onDisconnectedScreen;

    private ClientSwitch() {
    }

    public static void expect(SwitchingCharacterPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        server = minecraft.getCurrentServer();
        waited = 0;
        onDisconnectedScreen = 0;
    }

    public static void tick() {
        ServerData target = server;
        if (target == null) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (++waited > GIVE_UP_TICKS) {
            server = null;
            return;
        }
        if (minecraft.level != null || !(minecraft.screen instanceof DisconnectedScreen)) return;
        if (++onDisconnectedScreen < DELAY_TICKS) return;

        server = null;
        ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), minecraft,
                ServerAddress.parseString(target.ip), target, false, null);
    }
}
