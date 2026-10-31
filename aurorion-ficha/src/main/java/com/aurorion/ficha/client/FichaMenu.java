package com.aurorion.ficha.client;

import com.aurorion.core.client.menu.CharacterMenu;
import com.aurorion.ficha.network.FichaPayloads;
import com.aurorion.ficha.sheet.Catalog;
import com.aurorion.ficha.sheet.FichaSheet;
import com.aurorion.ficha.sheet.Json;
import com.aurorion.ficha.sheet.Texts;
import com.google.gson.JsonObject;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A ficha dentro do menu do personagem ({@code CharacterMenu} do core, desenhado pelo {@code aurorion-menu}).
 *
 * <p>O servidor não muda: o Perfil pede a ficha com o mesmo {@code Action("abrir")} da tecla, e a View que
 * volta é guardada aqui em vez de abrir a tela antiga ({@link #accept}, chamado pelo {@code FichaClient}).
 * Qualquer View própria que chegar depois (o site mudou a ficha) também atualiza o modelo, e o menu aberto
 * redesenha. Sem ficha (conta não vinculada, escolher personagem) a tela antiga ainda abre: o Vínculo entra
 * no menu numa fase seguinte.
 */
final class FichaMenu {
    static final String PROFILE = "perfil";
    /** A resposta que chega muito depois do pedido não é mais "do menu". */
    private static final long WANT_MS = 15_000L;
    /** Reabrir o Perfil logo depois não pede de novo ao site. */
    private static final long FRESH_MS = 5_000L;

    @Nullable
    static FichaSheet sheet;
    static String code = "";
    static String message = "";
    static boolean loading;
    private static long wantedAt;
    private static long updatedAt;

    private FichaMenu() {
    }

    static void register() {
        CharacterMenu.register(new ProfileSection());
    }

    /** Toque na tecla da ficha: com o menu instalado, abre direto no Perfil. */
    static boolean openProfile() {
        return CharacterMenu.open(PROFILE);
    }

    /** O Perfil foi aberto: pede a ficha, a não ser que a guardada seja recente. */
    static void request(boolean force) {
        long now = Util.getMillis();
        if (loading && now - wantedAt < WANT_MS) return;
        if (!force && sheet != null && now - updatedAt < FRESH_MS) return;
        wantedAt = now;
        loading = true;
        if (!FichaClient.send(new FichaPayloads.Action("abrir", "", ""))) {
            loading = false;
            wantedAt = 0;
            message = "Este servidor não tem a ficha do Aurorion.";
        }
        CharacterMenu.refresh();
    }

    /**
     * Uma View própria chegou. Guarda e devolve se era a resposta que o menu pediu e o menu dá conta dela
     * (com ficha): aí o {@code FichaClient} não abre a tela antiga.
     */
    static boolean accept(FichaPayloads.View payload, boolean test) {
        if (test || (payload.flags() & FichaPayloads.PEEK) != 0) return false;
        long now = Util.getMillis();
        boolean wanted = wantedAt > 0 && now - wantedAt < WANT_MS && (payload.flags() & FichaPayloads.OPEN) != 0;
        JsonObject json = Json.parseObject(payload.json());
        sheet = FichaSheet.parse(Json.object(json, "sheet"));
        code = payload.code();
        message = payload.message();
        updatedAt = now;
        FichaClient.rememberCharacter(sheet == null ? "" : sheet.name());
        if (wanted) {
            wantedAt = 0;
            loading = false;
        }
        CharacterMenu.refresh();
        return wanted && sheet != null;
    }

    /** O pedido do menu falhou (site fora, sem vínculo): o Perfil mostra o motivo. Devolve se era do menu. */
    static boolean failed(String reason) {
        if (wantedAt == 0) return false;
        wantedAt = 0;
        loading = false;
        message = reason;
        CharacterMenu.refresh();
        return CharacterMenu.isOpen();
    }

    /** Teste de atributo pelo menu: sem CD, aberto. O cartão com o d20 sai no HUD. */
    static void rollAttribute(String id) {
        if (FichaClient.send(new FichaPayloads.Roll("attribute", id, 0, false, ""))) CharacterMenu.close();
    }

    static void clear() {
        sheet = null;
        code = "";
        message = "";
        loading = false;
        wantedAt = 0;
        updatedAt = 0;
    }

    /** Por que não há ficha para mostrar, no texto do jogador. */
    static String emptyReason() {
        if (!message.isEmpty()) return message;
        return switch (code) {
            case "not_linked" -> "Sua conta do Minecraft não está vinculada ao site.";
            case "foreign" -> "Este perfil Minecraft está vinculado a outra conta do site.";
            case "needs_binding", "no_sheet" -> "Escolha qual ficha este perfil usa.";
            case "unavailable", "busy" -> "O site não respondeu agora. Tente Atualizar em instantes.";
            default -> "A ficha ainda não chegou.";
        };
    }

    private static final class ProfileSection implements CharacterMenu.Section {
        @Override
        public String id() {
            return PROFILE;
        }

        @Override
        public String title() {
            return "Perfil";
        }

        @Override
        public String icon() {
            return "person";
        }

        @Override
        public int order() {
            return 10;
        }

        @Override
        public void opened() {
            request(false);
        }

        @Override
        public CharacterMenu.Panel panel() {
            return ProfilePanel.INSTANCE;
        }

        @Override
        public List<CharacterMenu.Entry> entries() {
            List<CharacterMenu.Entry> out = new ArrayList<>();
            FichaSheet s = sheet;
            if (s != null && s.hasBuild() && !s.attributes().isEmpty()) {
                out.add(CharacterMenu.Entry.branch("Testar atributo", "target", true, () -> {
                    List<CharacterMenu.Entry> list = new ArrayList<>();
                    for (FichaSheet.Attribute a : s.attributes()) {
                        String label = a.label().isEmpty() ? Catalog.attributeLabel(a.id()) : a.label();
                        list.add(CharacterMenu.Entry.action(label, "star", "1d20 " + Texts.signed(a.effective()), true,
                                () -> rollAttribute(a.id())));
                    }
                    return list;
                }));
            }
            if (s == null && !loading) {
                // Sem ficha: a tela antiga ainda cuida do vínculo e da escolha de personagem.
                out.add(CharacterMenu.Entry.action("Vincular / escolher ficha", "people", "", true, () -> {
                    CharacterMenu.close();
                    FichaClient.requestOpen();
                }));
            }
            out.add(CharacterMenu.Entry.action(loading ? "Atualizando…" : "Atualizar ficha", "back", "", !loading, () -> request(true)));
            return out;
        }
    }
}
