package com.aurorion.menu.client;

import com.aurorion.core.client.menu.CharacterMenu;
import com.aurorion.menu.network.MenuPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Interações: o que a pessoa faz consigo (meditar) e com quem está perto (o que os outros mods registram no
 * {@code CharacterMenu}: desafiar para duelo...).
 */
final class InteractionsSection implements CharacterMenu.Section {
    private static final double NEAR = 8;

    @Override
    public String id() {
        return "interacoes";
    }

    @Override
    public String title() {
        return "Interações";
    }

    @Override
    public String icon() {
        return "people";
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public List<CharacterMenu.Entry> entries() {
        Minecraft mc = Minecraft.getInstance();
        List<CharacterMenu.Entry> out = new ArrayList<>();
        if (mc.player == null || mc.level == null) return out;
        boolean meditating = ClientPresence.meditating(mc.player.getUUID());
        out.add(CharacterMenu.Entry.action(meditating ? "Parar de meditar" : "Meditar", "heart", "", true, () -> {
            PacketDistributor.sendToServer(new MenuPayloads.Request(1, meditating ? 0 : 1));
            CharacterMenu.close();
        }));
        for (AbstractClientPlayer other : mc.level.players()) {
            if (other == mc.player || other.isSpectator() || other.distanceTo(mc.player) > NEAR) continue;
            String shown = other.getDisplayName().getString();
            out.add(CharacterMenu.Entry.branch(shown, "person", true, () -> actionsFor(other, shown)));
            if (out.size() >= 10) break;
        }
        return out;
    }

    private static List<CharacterMenu.Entry> actionsFor(AbstractClientPlayer target, String shown) {
        List<CharacterMenu.Entry> out = new ArrayList<>();
        for (CharacterMenu.Interaction interaction : CharacterMenu.interactions()) {
            try {
                CharacterMenu.Entry entry = interaction.entry().apply(target);
                if (entry != null) out.add(entry);
            } catch (RuntimeException ignored) {
                // uma interação quebrada não esconde as outras
            }
        }
        if (out.isEmpty()) out.add(CharacterMenu.Entry.disabled("Nada a fazer com " + shown, "no"));
        return out;
    }
}
