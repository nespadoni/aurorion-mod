package com.aurorion.essentials.compat;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Os ganchos da persistencia do Gram ({@code PhoneGramServerStoreMixin}, {@code PhoneGramStoreMixin}),
 * conferidos contra o jar real do telefone. Todos tem {@code require = 0}: sem este teste, uma versao
 * nova do telefone desligaria a correcao em silencio — e as fotos voltariam a sumir.
 */
class PhoneGramPersistenceContractTest {
    private static final String SERVER_STORE = "com.mattupolis.phone.server.social.PhoneSocialServerStore";
    private static final String CLIENT_STORE = "com.mattupolis.phone.client.gui.PhoneInstagramStore";
    private static final String PACKETS = "com/mattupolis/phone/network/packet/";

    @Test
    void serverStoreStillWritesAndReadsEachPhotoInOnePlace() throws IOException {
        try (ZipFile phone = phoneJar()) {
            ClassNode store = readPhoneClass(phone, SERVER_STORE);
            for (String save : List.of("saveGramPosts", "saveGramProfiles")) {
                assertEquals(1, countCalls(method(store, save, "(Ljava/util/Properties;)V"),
                        "java/util/Base64$Encoder", "encodeToString", "([B)Ljava/lang/String;"), save);
            }
            for (String load : List.of("loadGramPosts", "loadGramProfiles")) {
                assertEquals(1, countCalls(method(store, load, "(Ljava/util/Properties;)V"),
                        store.name, "bytesFromB64", "(Ljava/lang/String;)[B"), load);
            }
            assertEquals(1, countCalls(method(store, "savePersistentStore", "()V"), "java/nio/file/Files", "newOutputStream",
                    "(Ljava/nio/file/Path;[Ljava/nio/file/OpenOption;)Ljava/io/OutputStream;"));

            MethodNode sync = method(store, "syncToPlayer", "(Lnet/minecraft/server/level/ServerPlayer;)V");
            assertEquals(1, countCalls(sync, PACKETS + "ClientboundGramPhotoPostPacket", "<init>",
                    "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[BJJ)V"));
            assertEquals(1, countCalls(sync, PACKETS + "ClientboundGramProfileSyncPacket", "<init>",
                    "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[B)V"));

            assertStatic(method(store, "rememberGramProfile", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[B)V"));
            assertStatic(method(store, "ensurePersistentLoaded", "()V"));
            assertStaticField(store, "GRAM_PROFILES", "Ljava/util/LinkedHashMap;");
            // O avatar guardado, lido por reflexao para nao ser apagado num perfil salvo sem foto.
            ClassNode profile = readPhoneClass(phone, SERVER_STORE + "$GramProfile");
            assertNotNull(method(profile, "avatarBytes", "()[B"));
        }
    }

    @Test
    void clientStoreHooksExist() throws IOException {
        try (ZipFile phone = phoneJar()) {
            ClassNode store = readPhoneClass(phone, CLIENT_STORE);
            String post = "Lcom/mattupolis/phone/client/gui/PhoneInstagramStore$GramPost;";
            assertStatic(method(store, "receiveMultiplayerPhotoPost", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[BJJ)V"));
            assertStatic(method(store, "receiveMultiplayerPost", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IJJ)V"));
            assertStatic(method(store, "receiveRemoteProfile", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[B)V"));
            assertStatic(method(store, "getProfileAvatarPhoto", "()Ljava/nio/file/Path;"));
            assertStatic(method(store, "getProfileAvatarTextureForUser", "(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;"));
            assertStatic(method(store, "getPlayerName", "()Ljava/lang/String;"));
            assertStatic(method(store, "normalizeProfileKey", "(Ljava/lang/String;)Ljava/lang/String;"));
            assertStatic(method(store, "sanitizeCaption", "(Ljava/lang/String;)Ljava/lang/String;"));

            // Os redirects pegam a PRIMEIRA leitura de mapa de cada metodo: tem de ser o mapa certo.
            assertFirstCallReads(method(store, "getTextureForPost", "(" + post + ")Lnet/minecraft/resources/ResourceLocation;"),
                    "get", "REMOTE_POST_TEXTURES");
            assertFirstCallReads(method(store, "hasImageForPost", "(" + post + ")Z"),
                    "containsKey", "REMOTE_POST_IMAGE_BYTES");

            assertStaticField(store, "PLAYER_POSTS", "Ljava/util/List;");
            assertStaticField(store, "REMOTE_POST_IMAGE_BYTES", "Ljava/util/Map;");
            assertStaticField(store, "profileDisplayName", "Ljava/lang/String;");
            assertStaticField(store, "profileBio", "Ljava/lang/String;");
            assertStaticField(store, "profileAvatarPhoto", "Ljava/nio/file/Path;");

            // O post proprio e recriado pelo construtor publico do record, e achado pelo campo id.
            ClassNode gramPost = readPhoneClass(phone, CLIENT_STORE + "$GramPost");
            MethodNode constructor = method(gramPost, "<init>",
                    "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IIZLjava/nio/file/Path;)V");
            assertTrue((constructor.access & Opcodes.ACC_PUBLIC) != 0 && (gramPost.access & Opcodes.ACC_PUBLIC) != 0,
                    "GramPost precisa ser publico");
            assertTrue(gramPost.fields.stream().anyMatch(f -> f.name.equals("id") && f.desc.equals("Ljava/lang/String;")),
                    "GramPost.id");
        }
    }

    /** O mesmo cuidado do {@code PhoneMixinContractTest}: {@code @Shadow} de campo que o alvo nao declara derruba o mixin. */
    @Test
    void shadowedFieldsAreDeclaredByTheTargets() throws IOException {
        try (ZipFile phone = phoneJar()) {
            for (String name : List.of("PhoneGramServerStoreMixin", "PhoneGramStoreMixin")) {
                ClassNode mixin = readMixin(name);
                ClassNode type = readPhoneClass(phone, targets(mixin).getFirst());
                for (var field : mixin.fields) {
                    if (!hasShadow(field.visibleAnnotations) && !hasShadow(field.invisibleAnnotations)) continue;
                    assertTrue(type.fields.stream().anyMatch(candidate -> candidate.name.equals(field.name)
                                    && candidate.desc.equals(field.desc)),
                            name + ": @Shadow " + field.name + field.desc + " nao e declarado por " + type.name);
                }
            }
        }
    }

    private static void assertFirstCallReads(MethodNode method, String mapMethod, String field) {
        String lastStatic = null;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode read && read.getOpcode() == Opcodes.GETSTATIC) lastStatic = read.name;
            if (instruction instanceof MethodInsnNode call && call.owner.equals("java/util/Map") && call.name.equals(mapMethod)) {
                assertEquals(field, lastStatic, method.name + ": primeiro Map." + mapMethod);
                return;
            }
        }
        throw new AssertionError(method.name + " nao chama Map." + mapMethod);
    }

    private static int countCalls(MethodNode method, String owner, String name, String desc) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)
                    && call.desc.equals(desc)) count++;
        }
        return count;
    }

    private static MethodNode method(ClassNode type, String name, String desc) {
        return type.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst()
                .orElseThrow(() -> new AssertionError(type.name + " sem " + name + desc));
    }

    private static void assertStatic(MethodNode method) {
        assertTrue((method.access & Opcodes.ACC_STATIC) != 0, method.name + " deixou de ser estatico");
    }

    private static void assertStaticField(ClassNode type, String name, String desc) {
        assertTrue(type.fields.stream().anyMatch(f -> f.name.equals(name) && f.desc.equals(desc)
                && (f.access & Opcodes.ACC_STATIC) != 0), type.name + "." + name + " " + desc);
    }

    private static boolean hasShadow(List<AnnotationNode> annotations) {
        return annotations != null && annotations.stream()
                .anyMatch(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/Shadow;"));
    }

    private static ZipFile phoneJar() throws IOException {
        String path = System.getenv("AURORION_PHONE_JAR");
        assumeTrue(path != null && !path.isBlank(), "Defina AURORION_PHONE_JAR para validar a versao instalada do telefone.");
        return new ZipFile(path);
    }

    private static ClassNode readPhoneClass(ZipFile phone, String name) throws IOException {
        var entry = phone.getEntry(name.replace('.', '/') + ".class");
        assertNotNull(entry, name);
        try (var input = phone.getInputStream(entry)) {
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static ClassNode readMixin(String name) throws IOException {
        try (var input = PhoneGramPersistenceContractTest.class.getResourceAsStream("/com/aurorion/essentials/mixin/" + name + ".class")) {
            assertNotNull(input, name);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> targets(ClassNode mixin) {
        var annotation = mixin.invisibleAnnotations.stream().filter(a -> a.desc.endsWith("/Mixin;")).findFirst().orElseThrow();
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if ("targets".equals(annotation.values.get(i))) return (List<String>) annotation.values.get(i + 1);
        }
        throw new AssertionError(mixin.name + " sem targets");
    }
}
