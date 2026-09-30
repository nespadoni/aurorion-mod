package com.aurorion.servicos;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * O app mora dentro do celular por mixin e reflexao, com {@code require = 0}: se o telefone mudar um
 * destes nomes, o app some em silencio. Este teste le o jar instalado (sem carrega-lo) e avisa no build.
 *
 * <p>Aponte {@code AURORION_PHONE_JAR} para {@code mod-servidor-referencia/mattupolisphone112.jar}.
 */
class PhoneAppContractTest {
    private static final String GUI = "com.mattupolis.phone.client.gui.";

    @Test
    void mixinTargetsExist() throws IOException {
        try (ZipFile phone = phoneJar()) {
            assertStatic(read(phone, GUI + "PhoneHomeAppCatalog"), "getApps", "()Ljava/util/List;");
            assertMethod(read(phone, GUI + "PhoneHomeScreen"), "openHomeApp",
                    "(Lcom/mattupolis/phone/client/gui/PhoneHomeScreen$HomeApp;)V");
            assertMethod(read(phone, GUI + "PhoneHomeScreen"), "getNotificationAppIcon",
                    "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;");
            assertMethod(read(phone, GUI + "PhoneHomeScreen"), "resolveNotificationTargetScreen",
                    "(Lcom/mattupolis/phone/client/gui/PhoneNotificationStore$PhoneNotification;)Lnet/minecraft/client/gui/screens/Screen;");
            assertMethod(read(phone, GUI + "PhoneLockScreen"), "getNotificationAppIcon",
                    "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;");
            assertStatic(read(phone, GUI + "PhoneNotificationPanelOverlay"), "getNotificationAppIcon",
                    "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;");
            assertStatic(read(phone, GUI + "PhoneNotificationPanelOverlay"), "resolveNotificationTargetScreen",
                    "(Lcom/mattupolis/phone/client/gui/PhoneNotificationStore$PhoneNotification;)Lnet/minecraft/client/gui/screens/Screen;");
            assertStatic(read(phone, GUI + "PhoneGuiScaleManager"), "isPhoneScreen",
                    "(Lnet/minecraft/client/gui/screens/Screen;)Z");

            ClassNode library = read(phone, GUI + "PhoneAppLibraryScreen");
            assertMethod(library, "openApp", "(Ljava/lang/String;)V");
            assertTrue(library.fields.stream().anyMatch(f -> f.name.equals("apps") && f.desc.equals("Ljava/util/List;")),
                    "PhoneAppLibraryScreen.apps");
        }
    }

    @Test
    void bridgeTargetsExist() throws IOException {
        try (ZipFile phone = phoneJar()) {
            assertMethod(read(phone, GUI + "PhoneHomeAppCatalog$AppInfo"), "<init>",
                    "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;IIZZ)V");
            assertMethod(read(phone, GUI + "PhoneAppLibraryScreen$LibraryApp"), "<init>",
                    "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;I)V");
            assertMethod(read(phone, GUI + "PhoneHomeScreen$HomeApp"), "id", "()Ljava/lang/String;");
            assertMethod(read(phone, GUI + "PhoneNotificationStore$PhoneNotification"), "appName", "()Ljava/lang/String;");

            assertStatic(read(phone, GUI + "PhoneCaseStore"), "drawPhoneShell",
                    "(Lnet/minecraft/client/gui/GuiGraphics;IIIII)V");
            assertStatic(read(phone, GUI + "PhoneMessagesStore"), "createPlayerThread",
                    "(Ljava/lang/String;)Lcom/mattupolis/phone/client/gui/PhoneMessagesStore$PhoneThread;");
            assertMethod(read(phone, GUI + "PhoneMessagesStore$PhoneThread"), "id", "()Ljava/lang/String;");
            assertMethod(read(phone, GUI + "PhoneMessageChatScreen"), "<init>", "(Ljava/lang/String;Ljava/lang/String;)V");
            assertMethod(read(phone, GUI + "PhoneHomeScreen"), "<init>", "()V");
            assertStatic(read(phone, GUI + "PhoneNotificationStore"), "addPlayerNotification",
                    "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
            assertStatic(read(phone, "com.mattupolis.phone.client.PhoneClientInventoryState"), "hasPhoneInInventory", "()Z");

            ClassNode sounds = read(phone, GUI + "PhoneSoundManager");
            for (String sound : new String[]{"playClick", "playBack", "playError", "playMessageNotification"}) {
                assertStatic(sounds, sound, "()V");
            }
            ClassNode settings = read(phone, GUI + "PhoneSettingsStore");
            assertStatic(settings, "getBrightnessOverlayColor", "()I");
            assertStatic(settings, "isAirplaneModeEnabled", "()Z");
            assertStatic(settings, "isDarkModeEnabled", "()Z");
            ClassNode theme = read(phone, GUI + "PhoneTheme");
            for (String color : new String[]{"getAppBackgroundColor", "getCardColor", "getSecondaryCardColor", "getTextColor",
                    "getSubTextColor", "getMutedTextColor", "getAccentColor", "getBorderColor", "getSeparatorColor",
                    "getHomeIndicatorColor", "getSearchBoxColor", "getSwitchOffColor", "getSwitchKnobColor"}) {
                assertStatic(theme, color, "()I");
            }
        }
    }

    private static void assertMethod(ClassNode type, String name, String descriptor) {
        assertTrue(type.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(descriptor)),
                type.name + " sem " + name + descriptor);
    }

    private static void assertStatic(ClassNode type, String name, String descriptor) {
        assertTrue(type.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(descriptor)
                && (m.access & Opcodes.ACC_STATIC) != 0), type.name + " sem static " + name + descriptor);
    }

    private static ZipFile phoneJar() throws IOException {
        String path = System.getenv("AURORION_PHONE_JAR");
        assumeTrue(path != null && !path.isBlank(), "Defina AURORION_PHONE_JAR para validar a versao instalada do telefone.");
        return new ZipFile(path);
    }

    private static ClassNode read(ZipFile phone, String name) throws IOException {
        var entry = phone.getEntry(name.replace('.', '/') + ".class");
        assertNotNull(entry, name);
        try (var input = phone.getInputStream(entry)) {
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }
}
