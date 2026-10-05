package com.aurorion.essentials.compat;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Check the installed phone's render hooks without loading its GUI or Minecraft. */
class PhoneSocialNameContractTest {
    private static final String GUI = "com/mattupolis/phone/client/gui/";

    @Test
    void socialRecordAccessorsStillReadTheOriginalImmutableAccountFields() throws IOException {
        try (ZipFile phone = phoneJar()) {
            for (String record : List.of("PhoneTwitterStore$Tweet", "PhoneTwitterStore$TweetComment",
                    "PhoneInstagramStore$GramComment")) {
                ClassNode type = read(phone, record);
                assertTrue(type.fields.stream().anyMatch(field -> field.name.equals("username")
                        && field.desc.equals("Ljava/lang/String;")
                        && (field.access & org.objectweb.asm.Opcodes.ACC_FINAL) != 0), record);
                assertNotNull(method(type, "username"));
            }
            ClassNode tweet = read(phone, "PhoneTwitterStore$Tweet");
            assertTrue(tweet.fields.stream().anyMatch(field -> field.name.equals("displayName")
                    && field.desc.equals("Ljava/lang/String;")));
        }
    }

    @Test
    void twitterHeaderOrdinalsDoNotReplaceTheAvatarLookupKey() throws IOException {
        try (ZipFile phone = phoneJar()) {
            for (List<String> hook : List.of(
                    List.of("PhoneTwitterScreen", "drawTweet"),
                    List.of("PhoneTwitterPostDetailScreen", "drawTweetDetail"))) {
                MethodNode render = method(read(phone, hook.getFirst()), hook.get(1));
                List<MethodInsnNode> names = calls(render, GUI + "PhoneTwitterStore$Tweet", "displayName");
                assertTrue(names.size() >= 2, hook.toString());
                // The ordinal-zero value remains the account key supplied to avatar lookup.
                assertEquals("drawAvatar", nextCall(names.getFirst()).name, hook.toString());
                // Only ordinal one is replaced in the visible header.
                assertEquals("trimText", nextCall(names.get(1)).name, hook.toString());
            }
            MethodNode detail = method(read(phone, "PhoneTwitterPostDetailScreen"), "drawTweetDetail");
            assertEquals(3, calls(detail, GUI + "PhoneTwitterStore$Tweet", "displayName").size(),
                    "Ordinal two must remain the quoted author, not an avatar/profile key.");
            for (String draw : List.of("drawComposer", "drawQuoteCard")) {
                assertFalse(calls(method(read(phone, "PhoneTwitterScreen"), draw),
                        GUI + "PhoneTwitterStore$Tweet", "displayName").isEmpty(), draw);
            }
        }
    }

    @Test
    void commentAuthorRenderAndHeightCalculationBothReadTheSameRawUsername() throws IOException {
        try (ZipFile phone = phoneJar()) {
            for (List<String> hook : List.of(
                    List.of("PhoneInstagramPostDetailScreen", "PhoneInstagramStore$GramComment", "drawWrappedCommentRow"),
                    List.of("PhoneTwitterPostDetailScreen", "PhoneTwitterStore$TweetComment", "drawCommentRow"))) {
                ClassNode screen = read(phone, hook.getFirst());
                for (String draw : List.of(hook.get(2), "getCommentRowHeight")) {
                    assertFalse(calls(method(screen, draw), GUI + hook.get(1), "username").isEmpty(),
                            hook.getFirst() + "." + draw);
                }
            }
        }
    }

    @Test
    void socialAvatarInitialHooksStillExistOutsideTextTrimming() throws IOException {
        List<List<String>> hooks = List.of(
                List.of("PhoneTwitterScreen", "drawAvatar", "drawProfileAvatar"),
                List.of("PhoneTwitterPostDetailScreen", "drawAvatar"),
                List.of("PhoneInstagramScreen", "safeFirstLetter"),
                List.of("PhoneInstagramPostDetailScreen", "drawPost", "drawWrappedCommentRow"),
                List.of("PhoneInstagramProfileScreen", "drawProfile"),
                List.of("PhoneInstagramDmChatScreen", "safeFirstLetter"),
                List.of("PhoneInstagramBlockedUsersScreen", "drawPlayerHead"),
                List.of("PhoneInstagramFollowListScreen", "drawPlayerHead"),
                List.of("PhoneInstagramFollowRequestsScreen", "drawPlayerHead"),
                List.of("PhoneInstagramPostShareScreen", "drawPlayerHead"),
                List.of("PhoneInstagramStoryScreen", "drawPlayerHead"),
                List.of("PhoneInstagramStoryViewersScreen", "drawPlayerHead"));
        try (ZipFile phone = phoneJar()) {
            for (List<String> hook : hooks) {
                ClassNode screen = read(phone, hook.getFirst());
                for (String draw : hook.subList(1, hook.size())) {
                    assertTrue(calls(method(screen, draw), "java/lang/String", "substring").stream()
                            .anyMatch(call -> call.desc.equals("(II)Ljava/lang/String;")),
                            hook.getFirst() + "." + draw);
                }
            }
        }
    }

    private static MethodInsnNode nextCall(MethodInsnNode from) {
        for (var node = from.getNext(); node != null; node = node.getNext()) {
            if (node instanceof MethodInsnNode call) return call;
        }
        throw new AssertionError("No call after " + from.owner + "." + from.name);
    }

    private static List<MethodInsnNode> calls(MethodNode method, String owner, String name) {
        List<MethodInsnNode> found = new ArrayList<>();
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) {
                found.add(call);
            }
        }
        return found;
    }

    private static MethodNode method(ClassNode type, String name) {
        return type.methods.stream().filter(method -> method.name.equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError(type.name + "." + name));
    }

    private static ClassNode read(ZipFile phone, String name) throws IOException {
        var entry = phone.getEntry(GUI + name + ".class");
        assertNotNull(entry, name);
        try (var input = phone.getInputStream(entry)) {
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static ZipFile phoneJar() throws IOException {
        String path = System.getenv("AURORION_PHONE_JAR");
        assumeTrue(path != null && !path.isBlank(), "Defina AURORION_PHONE_JAR para validar a versao do telefone.");
        return new ZipFile(path);
    }
}
