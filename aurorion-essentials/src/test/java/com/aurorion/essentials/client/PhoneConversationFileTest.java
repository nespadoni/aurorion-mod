package com.aurorion.essentials.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PhoneConversationFileTest {
    @TempDir Path directory;

    public record Contact(String id, String title, String subtitle, int color, boolean playerThread,
                          boolean pinned, boolean archived, boolean muted) {}
    public record Message(String id, String text, boolean outgoing, String time, String status,
                          String attachmentType, Path attachmentPath, int locationX, int locationY, int locationZ) {}
    public record GramMessage(String threadId, String sender, String text, long timeMillis, boolean outgoing,
                              String sharedPostId, String sharedPhotoId) {}

    @Test
    void conversationRoundTripPreservesContactsOrderTextPhotosCoordinatesAndUnread() throws Exception {
        Contact contact = new Contact("player_other", "OtherAccount", "#123456", 0x123456, true, true, false, true);
        Message text = new Message("msg_100_1", "Olá: = \\ | 😀\nsegunda linha", false, "11:32", "", "text", null, 0, 0, 0);
        Message photo = new Message("msg_101_2", "Foto", true, "11:33", "enviada", "photo",
                directory.resolve("fotos com espaços").resolve("imagem.png"), 0, 0, 0);
        Message location = new Message("msg_102_3", "Localização", true, "11:34", "enviada", "location", null, -10, 70, 400);
        var state = new PhoneConversationFile.ThreadState(PhoneConversationFile.capture(contact), List.of(
                PhoneConversationFile.capture(text), PhoneConversationFile.capture(photo), PhoneConversationFile.capture(location)), 3);
        var snapshot = new PhoneConversationFile.Snapshot(List.of(state), Set.of());
        Path file = directory.resolve("messages.properties");

        PhoneConversationFile.write(file, snapshot);
        var restored = PhoneConversationFile.read(file);

        assertEquals(snapshot, restored);
        assertEquals(contact, PhoneConversationFile.restore(Contact.class, restored.threads().getFirst().thread()));
        assertEquals(text, PhoneConversationFile.restore(Message.class, restored.threads().getFirst().messages().get(0)));
        assertEquals(photo, PhoneConversationFile.restore(Message.class, restored.threads().getFirst().messages().get(1)));
        assertEquals(location, PhoneConversationFile.restore(Message.class, restored.threads().getFirst().messages().get(2)));
    }

    @Test
    void gramRoundTripKeepsNetworkDeduplicationAndSharedPhotoIds() throws Exception {
        GramMessage message = new GramMessage("dm_other", "OtherAccount", "mensagem", 1780000000100L, false, "post_12", "photo_13");
        var state = new PhoneConversationFile.ThreadState(Map.of("id", "dm_other", "title", "OtherAccount"),
                List.of(PhoneConversationFile.capture(message)), 1);
        var snapshot = new PhoneConversationFile.Snapshot(List.of(state), Set.of("network_1", "network_2"));
        Path file = directory.resolve("gram.properties");
        PhoneConversationFile.write(file, snapshot);
        var restored = PhoneConversationFile.read(file);
        assertEquals(snapshot.processedIds(), restored.processedIds());
        assertEquals(message, PhoneConversationFile.restore(GramMessage.class, restored.threads().getFirst().messages().getFirst()));
    }

    @Test
    void differentCharacterFilesStayIndependentAndClearingHistoryReallyPersists() throws Exception {
        Path main = directory.resolve("main").resolve("messages.properties");
        Path alt = directory.resolve("alt").resolve("messages.properties");
        var history = new PhoneConversationFile.Snapshot(List.of(new PhoneConversationFile.ThreadState(
                Map.of("id", "other", "title", "OtherAccount"), List.of(Map.of("text", "old message")), 1)), Set.of());
        var cleared = new PhoneConversationFile.Snapshot(List.of(), Set.of());
        PhoneConversationFile.write(main, history);
        PhoneConversationFile.write(alt, cleared);
        assertEquals(history, PhoneConversationFile.read(main));
        assertEquals(cleared, PhoneConversationFile.read(alt));
        PhoneConversationFile.write(main, cleared);
        assertEquals(cleared, PhoneConversationFile.read(main));
        try (var entries = Files.list(main.getParent())) {
            assertEquals(List.of(main), entries.toList());
        }
    }

    @Test
    void snapshotDoesNotChangeWhenLiveCollectionsAreClearedAtLogout() {
        Map<String, String> contact = new HashMap<>(Map.of("id", "other"));
        Map<String, String> message = new HashMap<>(Map.of("text", "keep me"));
        List<Map<String, String>> messages = new ArrayList<>(List.of(message));
        List<PhoneConversationFile.ThreadState> threads = new ArrayList<>(List.of(new PhoneConversationFile.ThreadState(contact, messages, 1)));
        var snapshot = new PhoneConversationFile.Snapshot(threads, Set.of());
        contact.clear();
        message.clear();
        messages.clear();
        threads.clear();
        assertEquals("other", snapshot.threads().getFirst().thread().get("id"));
        assertEquals("keep me", snapshot.threads().getFirst().messages().getFirst().get("text"));
    }

    @Test
    void incompleteInvalidOrUnsupportedFilesFailWithoutChangingTheirBytes() throws IOException {
        Path file = directory.resolve("broken.properties");
        for (String contents : List.of("version=2\nthread.count=0", "version=1\nthread.count=-1", "version=1\nthread.count=1",
                "version=1\nthread.count=0\nprocessed.count=0\ninvalid=\\uZZZZ")) {
            Files.writeString(file, contents);
            assertThrows(IOException.class, () -> PhoneConversationFile.read(file));
            assertEquals(contents, Files.readString(file));
        }
    }

    @Test
    void recordsRejectMalformedPrimitiveValuesBeforeTheyCanEnterThePhone() {
        assertThrows(IllegalArgumentException.class, () -> PhoneConversationFile.restore(Message.class,
                Map.of("outgoing", "sometimes", "locationX", "0", "locationY", "0", "locationZ", "0")));
        assertThrows(IllegalArgumentException.class, () -> PhoneConversationFile.restore(Contact.class, Map.of("id", "other")));
    }
}
