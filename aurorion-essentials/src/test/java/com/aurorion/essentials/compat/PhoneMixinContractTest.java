package com.aurorion.essentials.compat;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Verifica os pontos de integracao contra o jar real, sem carrega-lo nem exigir um cliente grafico. */
class PhoneMixinContractTest {
    @Test
    void nameFormattingHooksExistInTheInstalledPhoneVersion() throws IOException {
        ClassNode mixin = readMixin("PhoneDisplayNameMixin");
        try (ZipFile phone = phoneJar()) {
            for (String target : targets(mixin)) {
                ClassNode type = readPhoneClass(phone, target);
                assertTrue(type.methods.stream().anyMatch(method -> method.name.equals("trimText")
                        && method.desc.equals("(Ljava/lang/String;I)Ljava/lang/String;")), target);
            }
        }
    }

    @Test
    void contactAndCallHooksReadTheNameOnlyInsideRenderingMethods() throws IOException {
        try (ZipFile phone = phoneJar()) {
            for (String name : List.of("PhoneContactNameMixin", "PhoneCallingNameMixin", "PhoneIncomingNameMixin")) {
                ClassNode mixin = readMixin(name);
                ClassNode type = readPhoneClass(phone, targets(mixin).getFirst());
                for (var handler : mixin.methods) {
                    if (handler.visibleAnnotations == null) continue;
                    for (var annotation : handler.visibleAnnotations) {
                        if (!annotation.desc.endsWith("/Redirect;")) continue;
                        @SuppressWarnings("unchecked")
                        List<String> methods = (List<String>) value(annotation, "method");
                        AnnotationNode at = (AnnotationNode) value(annotation, "at");
                        String target = (String) value(at, "target");
                        for (String methodName : methods) {
                            var method = type.methods.stream().filter(m -> m.name.equals(methodName)).findFirst().orElseThrow();
                            boolean found = false;
                            for (var instruction : method.instructions) {
                                if (instruction instanceof FieldInsnNode field
                                        && target.equals("L" + field.owner + ";" + field.name + ":" + field.desc)) {
                                    found = true;
                                }
                            }
                            assertTrue(found, name + ": " + methodName + " -> " + target);
                        }
                    }
                }
            }
        }
    }

    /**
     * O mesmo erro que derrubou o cliente no banco: {@code @Shadow} de um campo que o alvo apenas
     * herda. O Mixin exige o campo declarado na propria classe, e aqui {@code defaultRequire} e 1,
     * entao a falha e fatal em vez de silenciosa.
     */
    @Test
    void shadowedFieldsAreDeclaredByTheTargetItselfAndNotInherited() throws IOException {
        try (ZipFile phone = phoneJar()) {
            for (String name : List.of("PhoneContactNameMixin", "PhoneCallingNameMixin", "PhoneIncomingNameMixin")) {
                ClassNode mixin = readMixin(name);
                ClassNode type = readPhoneClass(phone, targets(mixin).getFirst());
                for (var field : mixin.fields) {
                    if (!hasAnnotation(field.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Shadow;")) continue;
                    assertTrue(type.fields.stream().anyMatch(candidate -> candidate.name.equals(field.name)
                                    && candidate.desc.equals(field.desc)),
                            name + ": @Shadow " + field.name + field.desc + " nao e declarado por " + type.name);
                }
            }
        }
    }

    /**
     * Os mixins do lado servidor ficam num config nao obrigatorio com {@code require = 0}: se o alvo
     * sumir, o servidor sobe sem a correcao e sem aviso alto. E este teste que avisa — em especial o
     * {@code save}, cuja ausencia traria de volta o crash do Watchdog no login.
     */
    @Test
    void serverSideHooksExistInTheInstalledPhoneVersion() throws IOException {
        try (ZipFile phone = phoneJar()) {
            ClassNode store = readPhoneClass(phone, "com.mattupolis.phone.server.contacts.PhoneNumberServerStore");
            assertHasStaticMethod(store, "ensureNumberFor", "(Lnet/minecraft/server/level/ServerPlayer;)Ljava/lang/String;");
            assertHasStaticMethod(store, "save", "(Lnet/minecraft/server/MinecraftServer;)V");

            ClassNode calls = readPhoneClass(phone, "com.mattupolis.phone.voice.MattupolisVoiceCallManager");
            assertHasStaticMethod(calls, "buildPhoneCallGroup", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;");

            for (String name : List.of("PhoneNumberStoreMixin", "PhoneCallGroupMixin")) {
                ClassNode mixin = readMixin(name);
                ClassNode type = readPhoneClass(phone, targets(mixin).getFirst());
                for (var field : mixin.fields) {
                    if (!hasAnnotation(field.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Shadow;")
                            && !hasAnnotation(field.visibleAnnotations, "Lorg/spongepowered/asm/mixin/Shadow;")) continue;
                    assertTrue(type.fields.stream().anyMatch(candidate -> candidate.name.equals(field.name)
                                    && candidate.desc.equals(field.desc)),
                            name + ": @Shadow " + field.name + field.desc + " nao e declarado por " + type.name);
                }
            }
        }
    }

    /**
     * O {@code PhoneContactCleanup} mexe na agenda do telefone por reflexao, com nomes de campo e
     * metodo privados. Se o telefone os renomear, a limpeza para (e avisa no log do cliente); este
     * teste avisa antes, no build.
     */
    @Test
    void contactCleanupReflectionTargetsExist() throws IOException {
        try (ZipFile phone = phoneJar()) {
            ClassNode messages = readPhoneClass(phone, "com.mattupolis.phone.client.gui.PhoneMessagesStore");
            for (String field : List.of("THREADS", "MESSAGES", "UNREAD_COUNTS", "PHONE_ID_TO_PLAYER", "PLAYER_TO_PHONE_ID")) {
                assertTrue(messages.fields.stream().anyMatch(candidate -> candidate.name.equals(field)
                        && (candidate.access & org.objectweb.asm.Opcodes.ACC_STATIC) != 0), "PhoneMessagesStore." + field);
            }
            assertHasStaticMethod(messages, "loadPlayerContactsIfNeeded", "()V");
            assertHasStaticMethod(messages, "savePlayerContacts", "()V");

            ClassNode thread = readPhoneClass(phone, "com.mattupolis.phone.client.gui.PhoneMessagesStore$PhoneThread");
            for (String accessor : List.of("id", "title", "playerThread")) {
                assertTrue(thread.methods.stream().anyMatch(method -> method.name.equals(accessor)
                        && method.desc.startsWith("()")), "PhoneThread." + accessor);
            }

            ClassNode bank = readPhoneClass(phone, "com.mattupolis.phone.client.gui.PhoneBankStore");
            assertHasStaticMethod(bank, "getFavoriteAccounts", "()Ljava/util/List;");
            assertHasStaticMethod(bank, "toggleFavoriteAccount", "(Ljava/lang/String;)Z");
        }
    }

    /**
     * A pasta por personagem depende de cada store pedir o caminho por um metodo estatico sem
     * argumentos que devolve {@code Path}. Um store que passe a montar o caminho de outro jeito
     * voltaria a gravar na pasta compartilhada sem erro nenhum — este teste pega no build.
     */
    @Test
    void everyClientStoreStillAsksForItsFileThroughTheRedirectedMethod() throws IOException {
        List<List<String>> stores = List.of(
                List.of("PhoneBankStore", "getFavoritesFile"),
                List.of("PhoneCalendarStore", "getStoreFile"),
                List.of("PhoneCaseStore", "getStoreFile"),
                List.of("PhoneHealthStore", "getStoreFile"),
                List.of("PhoneHomeLayoutStore", "getPath"),
                List.of("PhoneMarketplaceStore", "getStoreFile"),
                List.of("PhoneMarketplaceStore", "getExtrasFile"),
                List.of("PhoneMessagesStore", "getContactsStoreFile"),
                List.of("PhoneMineStoreState", "getStoreFile"),
                List.of("PhoneNotesStore", "getStoreFile"),
                List.of("PhoneSettingsStore", "getPinStoreFile"),
                List.of("PhoneWallpaperStore", "getStoreFile"));
        try (ZipFile phone = phoneJar()) {
            for (List<String> store : stores) {
                ClassNode type = readPhoneClass(phone, "com.mattupolis.phone.client.gui." + store.get(0));
                assertHasStaticMethod(type, store.get(1), "()Ljava/nio/file/Path;");
            }
            assertCalls(readPhoneClass(phone, "com.mattupolis.phone.client.gui.PhoneGalleryStore"), "getLatestPhotos",
                    "java/nio/file/Path", "resolve");
            assertCalls(readPhoneClass(phone, "com.mattupolis.phone.client.PhoneClientPacketHandler"), "saveIncomingPhoto",
                    "java/nio/file/Path", "resolve");
        }
    }

    /**
     * O {@code PhoneSession} zera o estado em memoria do telefone na troca de personagem pelos nomes
     * dos campos. Um campo renomeado deixaria so aquele pedaco sem limpar (e um aviso no log).
     */
    @Test
    void sessionResetFieldsExist() throws IOException {
        List<List<String>> fields = List.of(
                List.of("PhoneMessagesStore", "playerContactsLoaded", "loadingPlayerContacts", "PHONE_ID_TO_PLAYER", "PLAYER_TO_PHONE_ID",
                        "CURRENT_OPEN_THREAD_ID", "CURRENT_PLAYER_PHONE_ID", "THREADS", "MESSAGES", "UNREAD_COUNTS"),
                List.of("PhoneMailStore", "MAILS"),
                List.of("PhoneInstagramDmStore", "THREADS", "MESSAGES", "PROCESSED_NETWORK_IDS", "currentOpenThreadId", "DM_PHOTO_TEXTURES"),
                List.of("PhoneNotesStore", "loaded", "NOTES"),
                List.of("PhoneCalendarStore", "loaded", "REMINDERS"),
                List.of("PhoneCaseStore", "loaded", "currentCaseId"),
                List.of("PhoneWallpaperStore", "loaded", "homeWallpaperId", "lockWallpaperId"),
                List.of("PhoneHomeLayoutStore", "loaded", "pages", "hiddenApps"),
                List.of("PhoneMineStoreState", "loaded", "jumpInstalled", "calculatorInstalled", "colorTapInstalled"),
                List.of("PhoneHealthStore", "loaded", "storedDate", "todaySteps", "distanceMeters", "activeTicks",
                        "foodEaten", "dailyGoal", "saveCooldownTicks", "lastFoodLevel"),
                List.of("PhoneMarketplaceStore", "loaded", "extrasLoaded", "LISTINGS", "COMMENTS", "FAVORITE_IDS", "VIEW_COUNTS"),
                List.of("PhoneSettingsStore", "pinLoaded", "pinContextKey", "lockPinCode"),
                List.of("PhoneBankStore", "favoritesLoaded", "favoriteAccounts", "unlocked", "available", "balanceText",
                        "coreBalance", "history", "accounts", "seenInitialSync", "newestIncomingTransferAt"));
        try (ZipFile phone = phoneJar()) {
            for (List<String> store : fields) {
                ClassNode type = readPhoneClass(phone, "com.mattupolis.phone.client.gui." + store.getFirst());
                for (String field : store.subList(1, store.size())) {
                    assertTrue(type.fields.stream().anyMatch(candidate -> candidate.name.equals(field)
                            && (candidate.access & org.objectweb.asm.Opcodes.ACC_STATIC) != 0), store.getFirst() + "." + field);
                }
            }
            assertHasStaticMethod(readPhoneClass(phone, "com.mattupolis.phone.client.gui.PhoneHealthStore"), "saveNow", "()V");
            assertHasStaticMethod(readPhoneClass(phone, "com.mattupolis.phone.client.gui.PhoneNotificationStore"),
                    "clearPlayerNotifications", "()V");
            assertHasStaticMethod(readPhoneClass(phone, "com.mattupolis.phone.client.gui.PhoneCallHistoryStore"), "clear", "()V");
            assertHasStaticMethod(readPhoneClass(phone, "com.mattupolis.phone.client.gui.PhoneGpsScreen"), "clearGpsTarget", "()V");
            assertHasStaticMethod(readPhoneClass(phone, "com.mattupolis.phone.client.gui.PhoneInstagramStore"), "clearNotifications", "()V");
            assertHasStaticMethod(readPhoneClass(phone, "com.mattupolis.phone.client.gui.PhoneTwitterStore"), "clearNotifications", "()V");
        }
    }

    /** Nenhum require=0 pode esconder uma atualizacao do telefone que deixe o historico sem gravar. */
    @Test
    void conversationPersistenceHooksExistWithTheExactDescriptors() throws IOException {
        try (ZipFile phone = phoneJar()) {
            for (String mixinName : List.of("PhoneMessagesPersistenceMixin", "PhoneGramPersistenceMixin")) {
                ClassNode mixin = readMixin(mixinName);
                for (String target : targets(mixin)) {
                    ClassNode owner = readPhoneClass(phone, target);
                    for (var handler : mixin.methods) {
                        if (handler.visibleAnnotations == null) continue;
                        for (var annotation : handler.visibleAnnotations) {
                            if (!annotation.desc.endsWith("/Inject;")) continue;
                            @SuppressWarnings("unchecked")
                            List<String> selectors = (List<String>) value(annotation, "method");
                            for (String selector : selectors) {
                                assertTrue(owner.methods.stream().anyMatch(method -> (method.name + method.desc).equals(selector)
                                                && (method.access & org.objectweb.asm.Opcodes.ACC_STATIC) != 0),
                                        mixinName + ": " + target + "." + selector);
                            }
                        }
                    }
                }
            }
        }
    }

    /** Os records sao restaurados diretamente, sem chamar metodos que enviam mensagens ou notificacoes. */
    @Test
    void persistedConversationRecordsRemainPublicAndUseSupportedComponentTypes() throws IOException {
        Set<String> supported = Set.of("Ljava/lang/String;", "Ljava/nio/file/Path;", "I", "J", "Z");
        try (ZipFile phone = phoneJar()) {
            for (String recordName : List.of("PhoneMessagesStore$PhoneThread", "PhoneMessagesStore$PhoneMessage",
                    "PhoneInstagramDmStore$InstaThread", "PhoneInstagramDmStore$InstaMessage")) {
                ClassNode record = readPhoneClass(phone, "com.mattupolis.phone.client.gui." + recordName);
                assertNotNull(record.recordComponents, recordName);
                assertTrue((record.access & org.objectweb.asm.Opcodes.ACC_PUBLIC) != 0, recordName);
                StringBuilder constructor = new StringBuilder("(");
                for (var component : record.recordComponents) {
                    assertTrue(supported.contains(component.descriptor), recordName + "." + component.name);
                    constructor.append(component.descriptor);
                    assertTrue(record.methods.stream().anyMatch(method -> method.name.equals(component.name)
                                    && method.desc.equals("()" + component.descriptor)
                                    && (method.access & org.objectweb.asm.Opcodes.ACC_PUBLIC) != 0),
                            recordName + "." + component.name);
                }
                constructor.append(")V");
                assertTrue(record.methods.stream().anyMatch(method -> method.name.equals("<init>")
                                && method.desc.contentEquals(constructor)
                                && (method.access & org.objectweb.asm.Opcodes.ACC_PUBLIC) != 0), recordName);
            }
        }
    }

    /** Os ganchos de nome fora do {@code trimText} das telas: overlays, HUD de ligacao, avatar, e-mail, PIX, chat. */
    @Test
    void characterNameHooksExist() throws IOException {
        try (ZipFile phone = phoneJar()) {
            String gui = "com.mattupolis.phone.client.gui.";
            assertHasStaticMethod(readPhoneClass(phone, gui + "PhoneNotificationPanelOverlay"), "trimText",
                    "(Lnet/minecraft/client/gui/Font;Ljava/lang/String;I)Ljava/lang/String;");
            assertHasStaticMethod(readPhoneClass(phone, gui + "PhoneDynamicIslandHelper"), "trimSimple",
                    "(Ljava/lang/String;I)Ljava/lang/String;");
            assertHasStaticMethod(readPhoneClass(phone, gui + "PhoneGpsCompactOverlay"), "trim",
                    "(Ljava/lang/String;I)Ljava/lang/String;");
            ClassNode call = readPhoneClass(phone, gui + "PhoneCallCompactOverlay");
            assertHasStaticMethod(call, "getDisplayName", "()Ljava/lang/String;");
            assertHasStaticMethod(call, "getIncomingDisplayName", "()Ljava/lang/String;");

            for (List<String> avatar : List.of(
                    List.of("PhoneMessagesScreen", "drawThreadRow"),
                    List.of("PhoneMessageChatScreen", "drawTopBar"),
                    List.of("PhoneContactsScreen", "drawContactRow"),
                    List.of("PhoneCallHistoryScreen", "drawHistoryRow"),
                    List.of("PhoneTwitterScreen", "drawAvatar"))) {
                assertCalls(readPhoneClass(phone, gui + avatar.get(0)), avatar.get(1), "java/lang/String", "substring");
            }

            assertHasStaticMethod(readPhoneClass(phone, gui + "PhoneMailStore"), "sendMail",
                    "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
            ClassNode bankScreen = readPhoneClass(phone, gui + "PhoneBankScreen");
            assertTrue(bankScreen.methods.stream().anyMatch(m -> m.name.equals("confirmTransfer") && m.desc.equals("()V")),
                    "PhoneBankScreen.confirmTransfer");
            assertTrue(bankScreen.fields.stream().anyMatch(f -> f.name.equals("pendingTarget") && f.desc.equals("Ljava/lang/String;")),
                    "PhoneBankScreen.pendingTarget");

            assertCalls(readPhoneClass(phone, "com.mattupolis.phone.voice.MattupolisVoiceCallManager"),
                    "sendIncomingCallChatIfAllowed", "net/minecraft/server/level/ServerPlayer", "sendSystemMessage");
        }
    }

    /** A limpeza do Gram/Twitter/marketplace no reset chama estes metodos privados dos stores do servidor. */
    @Test
    void socialResetReflectionTargetsExist() throws IOException {
        try (ZipFile phone = phoneJar()) {
            ClassNode social = readPhoneClass(phone, "com.mattupolis.phone.server.social.PhoneSocialServerStore");
            assertHasStaticMethod(social, "ensurePersistentLoaded", "()V");
            assertHasStaticMethod(social, "savePersistentStore", "()V");
            ClassNode market = readPhoneClass(phone, "com.mattupolis.phone.server.marketplace.PhoneMarketplaceServerStore");
            assertHasStaticMethod(market, "ensureLoaded", "(Lnet/minecraft/server/MinecraftServer;)V");
            assertHasStaticMethod(market, "save", "(Lnet/minecraft/server/MinecraftServer;)V");
        }
    }

    /** Sem estas chamadas o {@code PhonePhotoFormatMixin} nao entra e fotos recebidas voltam a falhar em silencio. */
    @Test
    void receivedPhotosAreStillDecodedThroughNativeImageRead() throws IOException {
        List<List<String>> loaders = List.of(
                List.of("PhoneMessagesStore", "getTextureForMessage"),
                List.of("PhoneInstagramDmStore", "registerPhotoTexture"),
                List.of("PhoneTwitterStore", "registerProfileTexture"));
        try (ZipFile phone = phoneJar()) {
            for (List<String> loader : loaders) {
                ClassNode type = readPhoneClass(phone, "com.mattupolis.phone.client.gui." + loader.get(0));
                assertTrue(type.methods.stream().anyMatch(method -> method.name.equals(loader.get(1))
                        && (method.access & org.objectweb.asm.Opcodes.ACC_STATIC) != 0), type.name + "." + loader.get(1));
                assertCalls(type, loader.get(1), "com/mojang/blaze3d/platform/NativeImage", "read");
            }
        }
    }

    private static void assertCalls(ClassNode type, String methodName, String owner, String called) {
        boolean found = false;
        for (var method : type.methods) {
            if (!method.name.equals(methodName)) continue;
            for (var instruction : method.instructions) {
                if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode call
                        && call.owner.equals(owner) && call.name.equals(called)) {
                    found = true;
                }
            }
        }
        assertTrue(found, type.name + "." + methodName + " nao chama " + owner + "." + called);
    }

    private static void assertHasStaticMethod(ClassNode type, String name, String descriptor) {
        assertTrue(type.methods.stream().anyMatch(method -> method.name.equals(name) && method.desc.equals(descriptor)
                        && (method.access & org.objectweb.asm.Opcodes.ACC_STATIC) != 0),
                type.name + " sem " + name + descriptor);
    }

    private static boolean hasAnnotation(List<AnnotationNode> annotations, String descriptor) {
        return annotations != null && annotations.stream().anyMatch(annotation -> annotation.desc.equals(descriptor));
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
        try (var input = PhoneMixinContractTest.class.getResourceAsStream("/com/aurorion/essentials/mixin/" + name + ".class")) {
            assertNotNull(input, name);
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> targets(ClassNode mixin) {
        var annotation = mixin.invisibleAnnotations.stream().filter(a -> a.desc.endsWith("/Mixin;")).findFirst().orElseThrow();
        return (List<String>) value(annotation, "targets");
    }

    private static Object value(AnnotationNode annotation, String key) {
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        }
        throw new AssertionError(annotation.desc + " sem " + key);
    }
}
