package com.aurorion.essentials.compat;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Anexos de foto (Mensagens, novo post do Gram, importar na galeria): confere no jar real do telefone
 * cada metodo, campo e chamada que os mixins e a reflexao alcancam. Todos usam {@code require = 0};
 * sem este teste uma versao nova do telefone desligaria os botoes em silencio.
 */
class PhoneAttachmentContractTest {
    private static final String GUI = "com/mattupolis/phone/client/gui/";
    private static final String GRAPHICS = "Lnet/minecraft/client/gui/GuiGraphics;";

    @Test
    void messagesInputRowStillDrawsTheTwoAttachmentButtonsThroughTheRedirectedCalls() throws IOException {
        try (ZipFile phone = phoneJar()) {
            ClassNode chat = read(phone, GUI + "PhoneMessageChatScreen");
            MethodNode draw = method(chat, "drawInputBackground", "(" + GRAPHICS + "IIIIII)V");
            assertEquals(2, calls(draw, GUI + "PhoneTheme", "getAttachmentButtonColor"));
            assertEquals(2, calls(draw, GUI + "PhoneTheme", "getAttachmentButtonHoverColor"));
            assertEquals(2, calls(draw, GUI + "PhoneLanguageStore", "t"),
                    "drawInputBackground passou a traduzir outro texto alem de Phot/Loc — o redirect o apagaria");
            method(chat, "mouseClicked", "(DDI)Z");
            method(chat, "openCamera", "()V");
            method(chat, "sendLocation", "()V");
            assertField(chat, "threadId", "Ljava/lang/String;");
            assertField(chat, "toastMessage", "Ljava/lang/String;");
            assertField(chat, "toastTicks", "I");
            method(chat, "<init>", "(Ljava/lang/String;)V");
        }
    }

    @Test
    void messagePhotoSendingPathExists() throws IOException {
        try (ZipFile phone = phoneJar()) {
            ClassNode session = read(phone, GUI + "PhoneLiveCameraSession");
            assertField(session, "messageThreadId", "Ljava/lang/String;");
            MethodNode send = method(session, "sendMessagePhoto", "(Ljava/nio/file/Path;)V");
            assertEquals(1, calls(send, GUI + "PhoneMessagesStore", "addPhotoMessage"));
            assertEquals(1, calls(send, "net/minecraft/client/Minecraft", "setScreen"),
                    "sendMessagePhoto deixou de voltar para a conversa");
        }
    }

    @Test
    void dmPhotoPickerStillReadsTheChosenPhotoAndHasTheBackButtonWhereWeExpect() throws IOException {
        try (ZipFile phone = phoneJar()) {
            ClassNode picker = read(phone, GUI + "PhoneInstagramDmPhotoPickerScreen");
            method(picker, "<init>", "(Ljava/lang/String;)V");
            MethodNode click = method(picker, "mouseClicked", "(DDI)Z");
            assertEquals(1, calls(click, GUI + "PhoneInstagramStore", "readImageBytesForNetwork"));
            assertTrue(calls(click, GUI + "PhoneSoundManager", "playError") >= 1);
            assertTrue(calls(click, GUI + "PhoneGalleryStore", "getLatestPhotos") >= 1);
            assertHasStaticMethod(read(phone, GUI + "PhoneInstagramStore"), "readImageBytesForNetwork",
                    "(Ljava/nio/file/Path;)[B");
        }
    }

    /**
     * O redirect do seletor para o fluxo do telefone devolvendo um array VAZIO. Aqui fica provado o
     * porque: logo depois da leitura o telefone faz {@code bytes.length == 0} sem conferir null —
     * devolver {@code null} derrubou o cliente (crash de 04/10/2026, 21:56).
     */
    @Test
    void dmPhotoPickerTreatsAnEmptyArrayAsTheStopSignalAndNeverChecksNull() throws IOException {
        try (ZipFile phone = phoneJar()) {
            MethodNode click = method(read(phone, GUI + "PhoneInstagramDmPhotoPickerScreen"), "mouseClicked", "(DDI)Z");
            AbstractInsnNode read = null;
            for (var insn : click.instructions) {
                if (insn instanceof MethodInsnNode call && call.name.equals("readImageBytesForNetwork")) read = insn;
            }
            assertNotNull(read);
            List<Integer> next = new ArrayList<>();
            for (AbstractInsnNode insn = read.getNext(); insn != null && next.size() < 4; insn = insn.getNext()) {
                if (insn.getOpcode() >= 0) next.add(insn.getOpcode());
            }
            assertEquals(List.of(Opcodes.ASTORE, Opcodes.ALOAD, Opcodes.ARRAYLENGTH, Opcodes.IFNE), next,
                    "o seletor mudou a checagem dos bytes; rever o retorno do PhonePhotoPickerMixin");
        }
    }

    @Test
    void gramPlusButtonOpensTheCameraScreenAndTheCaptionScreenTakesAPath() throws IOException {
        try (ZipFile phone = phoneJar()) {
            MethodNode click = method(read(phone, GUI + "PhoneInstagramScreen"), "mouseClicked", "(DDI)Z");
            assertTrue(news(click, GUI + "PhoneInstagramCameraScreen") >= 1, "o + do Gram nao abre mais a camera");
            assertTrue(calls(click, "net/minecraft/client/Minecraft", "setScreen") >= 1);
            method(read(phone, GUI + "PhoneInstagramCaptionScreen"), "<init>", "(Ljava/nio/file/Path;Ljava/lang/String;)V");
            method(read(phone, GUI + "PhoneInstagramScreen"), "<init>", "()V");
        }
    }

    @Test
    void galleryTopBarAndToastExist() throws IOException {
        try (ZipFile phone = phoneJar()) {
            ClassNode gallery = read(phone, GUI + "PhoneGalleryScreen");
            method(gallery, "drawTopBar", "(" + GRAPHICS + "IIIII)V");
            method(gallery, "mouseClicked", "(DDI)Z");
            assertField(gallery, "toastMessage", "Ljava/lang/String;");
            assertField(gallery, "toastTicks", "I");
            assertTrue(gallery.methods.stream().noneMatch(m -> m.name.equals("onFilesDrop")),
                    "a galeria passou a tratar arquivos soltos; o mixin sobrescreveria esse comportamento");
        }
    }

    @Test
    void themeSoundAndSettingsHelpersExist() throws IOException {
        try (ZipFile phone = phoneJar()) {
            ClassNode theme = read(phone, GUI + "PhoneTheme");
            for (String getter : List.of("getAttachmentButtonColor", "getAttachmentButtonHoverColor",
                    "getAttachmentButtonTextColor", "getCardColor", "getSubTextColor", "getTextColor",
                    "getAccentColor", "getSecondaryCardColor", "getReadableInputHoverColor")) {
                assertHasStaticMethod(theme, getter, "()I");
            }
            ClassNode sound = read(phone, GUI + "PhoneSoundManager");
            for (String name : List.of("playClick", "playBack", "playError")) assertHasStaticMethod(sound, name, "()V");
            assertHasStaticMethod(read(phone, GUI + "PhoneSettingsStore"), "isAirplaneModeEnabled", "()Z");
            assertHasStaticMethod(read(phone, GUI + "PhoneLanguageStore"), "t", "(Ljava/lang/String;)Ljava/lang/String;");
        }
    }

    // --- Leitura do jar --------------------------------------------------------------------------

    private static long calls(MethodNode method, String owner, String name) {
        long count = 0;
        for (var insn : method.instructions) {
            if (insn instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) count++;
        }
        return count;
    }

    private static long news(MethodNode method, String type) {
        long count = 0;
        for (var insn : method.instructions) {
            if (insn instanceof TypeInsnNode node && node.desc.equals(type)) count++;
        }
        return count;
    }

    private static MethodNode method(ClassNode type, String name, String descriptor) {
        return type.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(descriptor)).findFirst()
                .orElseThrow(() -> new AssertionError(type.name + "." + name + descriptor + " sumiu"));
    }

    private static void assertHasStaticMethod(ClassNode type, String name, String descriptor) {
        MethodNode found = method(type, name, descriptor);
        assertTrue((found.access & Opcodes.ACC_STATIC) != 0, type.name + "." + name + " deixou de ser static");
    }

    private static void assertField(ClassNode type, String name, String descriptor) {
        assertTrue(type.fields.stream().anyMatch(f -> f.name.equals(name) && f.desc.equals(descriptor)),
                type.name + "." + name + " sumiu");
    }

    private static ClassNode read(ZipFile jar, String name) throws IOException {
        var entry = jar.getEntry(name + ".class");
        assertNotNull(entry, name + " nao esta no jar do telefone");
        try (var input = jar.getInputStream(entry)) {
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static ZipFile phoneJar() throws IOException {
        String path = System.getenv("AURORION_PHONE_JAR");
        assumeTrue(path != null && !path.isBlank(), "Defina AURORION_PHONE_JAR para validar os anexos de foto.");
        return new ZipFile(path);
    }
}
