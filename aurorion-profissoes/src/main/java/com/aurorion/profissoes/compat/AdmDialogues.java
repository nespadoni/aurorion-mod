package com.aurorion.profissoes.compat;

import com.aurorion.profissoes.AurorionProfissoes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;
import java.lang.reflect.Method;

/**
 * "Conversar" com arvore de dialogo do Aviel's Dialogue Mod, quando o NPC define {@code adm_dialogue}.
 * Reflexao pelo mesmo motivo do {@code AdmCompat} do Limbo: o ADM e opcional e nao entra no build.
 * Sem ele, a tela mostra as falas de {@code dialogue} do JSON.
 */
public final class AdmDialogues {
    private static Method open;
    private static boolean initialized;
    private AdmDialogues() {}

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;
        if (!ModList.get().isLoaded("adm")) return;
        try {
            open = Class.forName("net.aviel.dialogue.api.AdmDialogueApi")
                    .getMethod("openDialogue", ServerPlayer.class, Entity.class, String.class);
        } catch (ReflectiveOperationException | LinkageError error) {
            AurorionProfissoes.LOGGER.warn("NPCs: ADM presente, mas a API de dialogo nao foi encontrada ({}).", error.toString());
        }
    }

    public static boolean available() { init(); return open != null; }

    /** @return {@code false} quando o dialogo nao abriu; quem chama mostra as falas do JSON. */
    public static boolean open(ServerPlayer player, Entity npc, String dialogue) {
        if (dialogue == null || dialogue.isBlank() || !available()) return false;
        try {
            open.invoke(null, player, npc, dialogue);
            return true;
        } catch (ReflectiveOperationException | RuntimeException error) {
            AurorionProfissoes.LOGGER.warn("NPCs: nao consegui abrir o dialogo '{}' ({}).", dialogue, error.toString());
            return false;
        }
    }
}
