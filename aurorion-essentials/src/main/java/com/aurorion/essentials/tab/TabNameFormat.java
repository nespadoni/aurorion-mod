package com.aurorion.essentials.tab;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Pinta o nome de quem esta na tab com a cor da casa, sem desmontar o resto da linha.
 *
 * <p>A linha nem sempre e nossa. O servidor usa o Just Essentials para a tab, e ele pode montar a
 * entrada inteira ({@code [Admin] Fulano [Oculto]}) com prefixo de grupo e sufixo de vanish. Por isso
 * aqui nao se troca a linha: procura-se <b>o nome</b> dentro dela e so aquele trecho muda. O prefixo,
 * o sufixo e as cores deles ficam como o outro mod mandou.
 *
 * <p>Se a linha trouxer o nick da conta em vez do nome do personagem (um mod que leu o
 * {@code GameProfile}), o trecho vira o nome do personagem: a tab nao pode entregar quem esta por tras
 * da mascara.
 *
 * <p>Classe pura (so {@link Component}), coberta pelo {@code TabNameFormatTest}.
 */
public final class TabNameFormat {
    private TabNameFormat() {
    }

    /**
     * @param incoming  o que ja ia para a tab: montado por outro mod, pelo evento do NeoForge, ou
     *                  {@code null} (o cliente mostraria o nick da conta)
     * @param fakeName  o nome do personagem pronto para exibir, ou {@code null}
     * @param fakePlain o mesmo nome em texto puro, para achar o trecho dentro da linha
     * @param realName  o nick da conta, ou {@code null} se o pacote nao trouxe o perfil
     * @param color     a cor da casa em RGB, ou {@code null} quando nao ha casa (ou a config desligou)
     * @return a linha a mostrar; {@code incoming} intacto quando nao ha nada a mudar
     */
    @Nullable
    public static Component decorate(@Nullable Component incoming, @Nullable Component fakeName,
                                     @Nullable String fakePlain, @Nullable String realName,
                                     @Nullable Integer color) {
        if (fakeName == null && color == null) return incoming;

        if (incoming == null) {
            Component name = fakeName != null ? fakeName : realName == null ? null : Component.literal(realName);
            if (name == null) return null;
            return color == null ? name : recolor(name, color);
        }

        String text = incoming.getString();
        int at = fakePlain == null ? -1 : find(text, fakePlain);
        if (at >= 0) {
            // A linha ja mostra o personagem; sem casa, nao ha o que fazer.
            return color == null ? incoming : splice(incoming, at, fakePlain.length(), fakeName, color);
        }
        at = realName == null ? -1 : find(text, realName);
        if (at >= 0) {
            Component name = fakeName != null ? fakeName : Component.literal(realName);
            return splice(incoming, at, realName.length(), name, color);
        }
        // Linha montada sem o nome de ninguem (um placeholder que nao e o jogador): nada a pintar.
        return incoming;
    }

    /** Toda a extensao do nome na cor da casa; negrito, italico e afins do nome ficam. */
    static MutableComponent recolor(Component name, int color) {
        MutableComponent result = Component.empty();
        for (Component piece : name.toFlatList()) {
            result.append(Component.literal(piece.getString()).setStyle(piece.getStyle().withColor(color)));
        }
        return result;
    }

    /**
     * Primeira ocorrencia de {@code name} como palavra inteira: "Ana" nao e achada dentro de "Banana".
     * Diferencia maiuscula de proposito: e o mesmo texto que o outro mod copiou do jogador.
     */
    static int find(String text, String name) {
        if (name.isEmpty()) return -1;
        int from = 0;
        while (true) {
            int at = text.indexOf(name, from);
            if (at < 0) return -1;
            int end = at + name.length();
            if ((at == 0 || !wordChar(text.charAt(at - 1))) && (end == text.length() || !wordChar(text.charAt(end)))) {
                return at;
            }
            from = at + 1;
        }
    }

    private static boolean wordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /**
     * Troca o trecho {@code [at, at + length)} da linha por {@code name}. O nome herda o estilo do pedaco
     * onde ele comecava (a cor de grupo, o clique do outro mod) e, com casa, leva a cor dela por cima.
     */
    private static Component splice(Component line, int at, int length, Component name, @Nullable Integer color) {
        List<Component> pieces = line.toFlatList();
        MutableComponent result = Component.empty();
        int end = at + length;
        int offset = 0;
        boolean inserted = false;
        for (Component piece : pieces) {
            String text = piece.getString();
            Style style = piece.getStyle();
            int start = offset;
            int stop = offset + text.length();
            if (start < at) {
                result.append(Component.literal(text.substring(0, Math.min(text.length(), at - start))).setStyle(style));
            }
            if (!inserted && stop > at && start < end) {
                result.append(Component.empty().setStyle(style).append(color == null ? name : recolor(name, color)));
                inserted = true;
            }
            if (stop > end) {
                result.append(Component.literal(text.substring(Math.max(0, end - start))).setStyle(style));
            }
            offset = stop;
        }
        return result;
    }
}
