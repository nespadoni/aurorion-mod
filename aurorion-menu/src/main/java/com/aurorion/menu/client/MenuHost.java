package com.aurorion.menu.client;

import com.aurorion.core.client.menu.CharacterMenu;
import com.aurorion.menu.network.MenuPayloads;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * A resposta do {@code CharacterMenu} do core: abrir o painel na frente da pessoa, mostrar uma janela ○/× e
 * redesenhar quando os dados de uma seção chegam. Avisa o servidor quando o menu abre e fecha (a presença
 * que os outros veem).
 */
final class MenuHost implements CharacterMenu.Host {
    static final MenuHost INSTANCE = new MenuHost();
    private static boolean announced;

    private MenuHost() {
    }

    @Override
    public void open(@Nullable String section) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return;
        MenuView.open(section);
        announce(true);
        // Uma janela solta na frente da pessoa passa para dentro do menu.
        long until = MenuPrompt.until();
        CharacterMenu.Modal pending = MenuPrompt.take();
        MenuView view = MenuView.current();
        if (pending != null && view != null) view.prompt(pending, until);
    }

    @Override
    public void prompt(CharacterMenu.Modal modal) {
        MenuView view = MenuView.current();
        // Menu aberto: a janela vai nele. Fechado: um cartão sozinho no meio da visão.
        if (view != null) view.prompt(modal);
        else if (Minecraft.getInstance().player != null) MenuPrompt.show(modal);
    }

    @Override
    public void refresh() {
        MenuView view = MenuView.current();
        if (view != null) view.markDirty();
    }

    @Override
    public boolean isOpen() {
        return MenuView.isOpen();
    }

    @Override
    public void close() {
        MenuView.close(true);
    }

    @Override
    public void dismissPrompt() {
        MenuPrompt.clear();
        MenuView view = MenuView.current();
        if (view != null) view.dismissModal();
    }

    @Override
    public boolean notice(CharacterMenu.Notice notice) {
        return MenuNotices.offer(notice);
    }

    /** O menu fechou (por qualquer caminho). */
    static void closed() {
        announce(false);
    }

    private static void announce(boolean open) {
        if (announced == open) return;
        announced = open;
        if (Minecraft.getInstance().getConnection() != null) PacketDistributor.sendToServer(new MenuPayloads.Request(0, open ? 1 : 0));
    }

    static void reset() {
        // Saindo do servidor: nada vai pela rede.
        announced = false;
        MenuView.close(false);
        MenuNotices.clear();
        MenuPrompt.clear();
    }
}
