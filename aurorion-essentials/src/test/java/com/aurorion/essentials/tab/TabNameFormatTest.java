package com.aurorion.essentials.tab;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * O que estes testes protegem: a tab do servidor e montada pelo Just Essentials, com prefixo de grupo
 * e sufixo de vanish. A cor da casa tem de cair <b>so no nome</b>, e o nick da conta nunca pode vazar.
 */
class TabNameFormatTest {
    private static final int VENTHRA = 0x3FA9F5;
    private static final Component BELLA = Component.literal("Bella Noob");

    @Test
    void semNomeDePersonagemNemCasaNadaMuda() {
        Component line = Component.literal("[Admin] Fulano");
        assertSame(line, TabNameFormat.decorate(line, null, null, "Fulano", null));
        assertNull(TabNameFormat.decorate(null, null, null, "Fulano", null));
    }

    @Test
    void semLinhaDeOutroModOPersonagemApareceNaCorDaCasa() {
        Component result = TabNameFormat.decorate(null, BELLA, "Bella Noob", "bella123", VENTHRA);
        assertEquals("Bella Noob", result.getString());
        assertTrue(colors(result).stream().allMatch(c -> c != null && c.getValue() == VENTHRA));
    }

    @Test
    void soONomeMudaDeCorPrefixoESufixoFicam() {
        Component line = Component.literal("[Admin] ").withStyle(ChatFormatting.RED)
                .append(Component.literal("Bella Noob").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" [Oculto]").withStyle(ChatFormatting.DARK_GRAY));
        Component result = TabNameFormat.decorate(line, BELLA, "Bella Noob", "bella123", VENTHRA);

        assertEquals("[Admin] Bella Noob [Oculto]", result.getString());
        List<Component> pieces = result.toFlatList();
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.RED), pieces.get(0).getStyle().getColor());
        assertEquals(VENTHRA, pieces.get(1).getStyle().getColor().getValue());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.DARK_GRAY), pieces.get(2).getStyle().getColor());
    }

    @Test
    void oNickDaContaDentroDaLinhaViraOPersonagem() {
        Component line = Component.literal("[Mod] bella123");
        Component result = TabNameFormat.decorate(line, BELLA, "Bella Noob", "bella123", null);
        assertEquals("[Mod] Bella Noob", result.getString());
    }

    @Test
    void semCasaUmaLinhaQueJaMostraOPersonagemFicaIntacta() {
        Component line = Component.literal("[Mod] Bella Noob");
        assertSame(line, TabNameFormat.decorate(line, BELLA, "Bella Noob", "bella123", null));
    }

    @Test
    void oNomeQuebradoEmVariosPedacosEPintadoInteiro() {
        Component line = Component.literal("Bel").append(Component.literal("la No")).append(Component.literal("ob!"));
        Component result = TabNameFormat.decorate(line, BELLA, "Bella Noob", null, VENTHRA);
        assertEquals("Bella Noob!", result.getString());
        List<Component> pieces = result.toFlatList();
        assertEquals(VENTHRA, pieces.get(0).getStyle().getColor().getValue());
        assertNull(pieces.get(pieces.size() - 1).getStyle().getColor());
    }

    @Test
    void negritoDoNomeSobreviveACorDaCasa() {
        Component bold = Component.literal("Bella").withStyle(Style.EMPTY.withBold(true).withColor(ChatFormatting.GOLD));
        Component result = TabNameFormat.decorate(null, bold, "Bella", null, VENTHRA);
        Style style = result.toFlatList().get(0).getStyle();
        assertTrue(style.isBold());
        assertEquals(VENTHRA, style.getColor().getValue());
    }

    @Test
    void aplicarDuasVezesDaOMesmoResultado() {
        Component line = Component.literal("[Admin] Bella Noob");
        Component once = TabNameFormat.decorate(line, BELLA, "Bella Noob", "bella123", VENTHRA);
        Component twice = TabNameFormat.decorate(once, BELLA, "Bella Noob", "bella123", VENTHRA);
        assertEquals(once.getString(), twice.getString());
        assertEquals(colors(once), colors(twice));
    }

    @Test
    void nomeSoEAchadoComoPalavraInteira() {
        assertEquals(-1, TabNameFormat.find("Banana", "ana"));
        assertEquals(4, TabNameFormat.find("Oi, Ana!", "Ana"));
        assertEquals(10, TabNameFormat.find("Anabela e Ana", "Ana"));
    }

    private static List<TextColor> colors(Component component) {
        return component.toFlatList().stream().map(piece -> piece.getStyle().getColor()).toList();
    }
}
