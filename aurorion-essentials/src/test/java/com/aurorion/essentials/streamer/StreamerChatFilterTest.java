package com.aurorion.essentials.streamer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamerChatFilterTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "commands.teleport.success.location.single", "commands.gamemode.success.self",
            "gameMode.changed", "commands.give.success.single", "commands.generic.permission",
            "command.unknown.command", "argument.entity.notfound.player", "chat.type.admin",
            "death.attack.player", "death.attack.fall", "multiplayer.player.joined",
            "multiplayer.player.joined.renamed", "multiplayer.player.left", "chat.type.advancement.task",
            "commands.aurorion_essentials.realname.found", "commands.aurorion_essentials.ajuda.notify.header",
            "aurorion_ethereal.mural.protector.admin_alert"
    })
    void operationalTranslationKeysAreHiddenInEveryLanguage(String key) {
        assertTrue(hide(Component.translatable(key), StreamerMode.OPERATIONS));
        assertTrue(hide(Component.translatable(key), StreamerMode.ALL_SYSTEM));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "chat.type.text", "chat.type.announcement", "chat.type.emote",
            "chat.type.team.text", "chat.type.team.sent",
            "commands.message.display.incoming", "commands.message.display.outgoing"
    })
    void playerChatAndWhispersSentAsSystemArePreservedEvenInTotalMode(String key) {
        Component message = Component.translatable(key, Component.literal("Bella"),
                Component.literal("Eu morri, me teleporta?"));
        assertFalse(hide(message, StreamerMode.OPERATIONS));
        assertFalse(hide(message, StreamerMode.ALL_SYSTEM));
    }

    @Test void theBodyOfPlayerChatIsNeverInterpretedAsAnOperationalNotice() {
        Component chat = Component.translatable("chat.type.text", Component.literal("Bella"),
                Component.translatable("death.attack.fall", Component.literal("Uma historia")));
        assertFalse(hide(chat, StreamerMode.OPERATIONS));
        assertFalse(hide(chat, StreamerMode.ALL_SYSTEM));

        Component quotedAdmin = Component.translatable("chat.type.text", Component.literal("Bella"),
                Component.literal("[admin] esta fala faz parte da cena"));
        assertFalse(hide(quotedAdmin, StreamerMode.ALL_SYSTEM));
    }

    @Test void adminEnvelopeCannotBeKeptByDisguisingItsArgumentAsChat() {
        Component admin = Component.translatable("chat.type.admin", Component.literal("Servidor"),
                Component.translatable("chat.type.text", Component.literal("Staff"), Component.literal("log")));
        assertTrue(hide(admin, StreamerMode.OPERATIONS));
    }

    @Test void privateDeathHistoryNoticeHidesEvenWithAPlainLiteralCause() {
        Component admin = Component.literal("[admin] ").withStyle(ChatFormatting.DARK_RED)
                .append(Component.literal("Bella morreu (nick_real) [TP] [Inventario]"));
        assertTrue(hide(admin, StreamerMode.OPERATIONS));
    }

    @Test void taggedAreasStaffAlertsHideTheRealAccountAndCoordinates() {
        Component admin = Component.literal("[Áreas] ").append("nick_real entrou em Floresta — 123, 45, 67");
        assertTrue(hide(admin, StreamerMode.OPERATIONS));
    }

    @Test void prefixedTranslatedDeathAndChatAreRecognizedThroughSiblings() {
        Component death = Component.literal("Aviso: ")
                .append(Component.translatable("death.attack.fall", Component.literal("Bella")));
        assertTrue(hide(death, StreamerMode.OPERATIONS));

        Component chat = Component.literal("[RP] ")
                .append(Component.translatable("chat.type.text", Component.literal("Bella"), Component.literal("Oi")));
        assertFalse(hide(chat, StreamerMode.ALL_SYSTEM));
    }

    @Test void conservativeModePreservesLiteralNpcRoleplayAndOtherModMessages() {
        Component npc = Component.literal("Guarda: teleporte e morte nao existem nesta vila.");
        Component roleplay = Component.translatable("aurorion_limbo.fall");
        assertFalse(hide(npc, StreamerMode.OPERATIONS));
        assertFalse(hide(roleplay, StreamerMode.OPERATIONS));
        assertTrue(hide(npc, StreamerMode.ALL_SYSTEM));
        assertTrue(hide(roleplay, StreamerMode.ALL_SYSTEM));
    }

    @Test void turningOffAndActionbarAlwaysBypassFiltering() {
        Component death = Component.translatable("death.attack.player");
        assertFalse(hide(death, StreamerMode.OFF));
        assertFalse(StreamerChatFilter.shouldHide(death, true, StreamerMode.OPERATIONS));
        assertFalse(StreamerChatFilter.shouldHide(death, true, StreamerMode.ALL_SYSTEM));
        assertFalse(hide(null, StreamerMode.ALL_SYSTEM));
    }

    private static boolean hide(Component message, StreamerMode mode) {
        return StreamerChatFilter.shouldHide(message, false, mode);
    }
}
