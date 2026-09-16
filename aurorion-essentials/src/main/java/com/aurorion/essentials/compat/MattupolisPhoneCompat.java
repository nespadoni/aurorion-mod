package com.aurorion.essentials.compat;

import com.aurorion.essentials.AurorionEssentials;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/**
 * Integra o telefone MikasRevs/Mattupolis sem torna-lo dependencia obrigatoria.
 *
 * <p>O mod grava estado por UUID fora de {@code playerdata}, entao o reset generico do personagem
 * nao consegue limpar telefone, contatos e historico bancario sozinho. Esta classe mexe apenas nas
 * entradas da conta afetada e deixa o resto do servidor intacto.</p>
 */
public final class MattupolisPhoneCompat {
    private static final String PHONE_NUMBER_STORE = "phone_numbers.properties";
    private static final String BANK_HISTORY_STORE = "bank_history.properties";

    private MattupolisPhoneCompat() {
    }

    public static void resetCharacterData(MinecraftServer server, UUID account) throws IOException {
        Path dir = phoneDir(server);
        removePropertiesForAccount(dir.resolve(PHONE_NUMBER_STORE), account);
        removePropertiesForAccount(dir.resolve(BANK_HISTORY_STORE), account);
        removeAuditLinesForAccount(dir.resolve("bank_audit.log"), account);
        forgetLoadedRuntimeState();
    }

    public static void applyCharacterName(ServerPlayer player, String fullName) {
        String display = trim(fullName, 16);
        updateRuntimePhoneName(player, display);

        try {
            updateStoredPhoneName(player.getServer(), player.getUUID(), display);
        } catch (IOException e) {
            AurorionEssentials.LOGGER.warn("Nao foi possivel atualizar o nome do telefone de {}.",
                    player.getGameProfile().getName(), e);
        }
    }

    private static void updateRuntimePhoneName(ServerPlayer player, String display) {
        try {
            Class<?> store = Class.forName("com.mattupolis.phone.server.contacts.PhoneNumberServerStore");
            Method ensureNumberFor = store.getMethod("ensureNumberFor", ServerPlayer.class);
            ensureNumberFor.invoke(null, player);

            Field uuidToName = store.getDeclaredField("UUID_TO_NAME");
            uuidToName.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<UUID, String> names = (Map<UUID, String>) uuidToName.get(null);
            names.put(player.getUUID(), display);

            Method syncToPlayer = store.getMethod("syncToPlayer", ServerPlayer.class);
            syncToPlayer.invoke(null, player);
        } catch (ClassNotFoundException ignored) {
            // O telefone e opcional.
        } catch (ReflectiveOperationException e) {
            AurorionEssentials.LOGGER.warn("Nao foi possivel sincronizar nome do telefone para {}.",
                    player.getGameProfile().getName(), e);
        }
    }

    private static void updateStoredPhoneName(MinecraftServer server, UUID account, String display) throws IOException {
        Path file = phoneDir(server).resolve(PHONE_NUMBER_STORE);
        if (!Files.exists(file)) return;

        Properties props = load(file);
        boolean changed = false;
        int count = parseInt(props.getProperty("entry.count"), 0);
        for (int i = 0; i < count; i++) {
            String key = "entry." + i;
            String value = props.getProperty(key);
            if (value == null) continue;

            String[] parts = value.split("\\|", -1);
            if (parts.length < 3 || !account.toString().equals(parts[0])) continue;

            props.setProperty(key, parts[0] + "|" + encode(display) + "|" + parts[2]);
            changed = true;
        }

        if (changed) store(file, props, "Mattupolis Phone Number Directory");
    }

    private static void removePropertiesForAccount(Path file, UUID account) throws IOException {
        if (!Files.exists(file)) return;

        String needle = account.toString();
        Properties original = load(file);
        Properties kept = new Properties();
        int keptIndex = 0;
        int count = parseInt(original.getProperty("entry.count"), 0);

        for (int i = 0; i < count; i++) {
            String value = original.getProperty("entry." + i);
            if (value == null || value.contains(needle)) continue;
            kept.setProperty("entry." + keptIndex++, value);
        }

        for (String key : original.stringPropertyNames()) {
            if (key.equals("entry.count") || key.startsWith("entry.")) continue;
            String value = original.getProperty(key);
            if (!key.contains(needle) && (value == null || !value.contains(needle))) {
                kept.setProperty(key, value);
            }
        }

        kept.setProperty("entry.count", String.valueOf(keptIndex));
        store(file, kept, "Aurorion character reset");
    }

    private static void removeAuditLinesForAccount(Path file, UUID account) throws IOException {
        if (!Files.exists(file)) return;

        String needle = account.toString();
        List<String> kept = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.contains(needle)) kept.add(line);
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            for (String line : kept) {
                writer.write(line);
                writer.newLine();
            }
        }
    }

    private static void forgetLoadedRuntimeState() {
        resetLoadedWorldKey("com.mattupolis.phone.server.contacts.PhoneNumberServerStore");
        resetLoadedWorldKey("com.mattupolis.phone.server.bank.PhoneBankServerStore");
    }

    private static void resetLoadedWorldKey(String className) {
        try {
            Class<?> store = Class.forName(className);
            Field loadedWorldKey = store.getDeclaredField("loadedWorldKey");
            loadedWorldKey.setAccessible(true);
            loadedWorldKey.set(null, "");
        } catch (ClassNotFoundException ignored) {
            // O telefone e opcional.
        } catch (ReflectiveOperationException e) {
            AurorionEssentials.LOGGER.warn("Nao foi possivel invalidar cache de {}.", className, e);
        }
    }

    private static Properties load(Path file) throws IOException {
        Properties props = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            props.load(reader);
        }
        return props;
    }

    private static void store(Path file, Properties props, String comment) throws IOException {
        Files.createDirectories(file.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            props.store(writer, comment);
        }
    }

    private static Path phoneDir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("mattupolis_phone");
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
