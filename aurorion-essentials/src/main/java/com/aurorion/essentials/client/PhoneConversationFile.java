package com.aurorion.essentials.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Formato local do historico. Nao depende de carregar Minecraft nem as classes do telefone. */
public final class PhoneConversationFile {
    public record ThreadState(Map<String, String> thread, List<Map<String, String>> messages, int unread) {
        public ThreadState {
            thread = Map.copyOf(thread);
            messages = messages.stream().map(Map::copyOf).toList();
            unread = Math.max(0, unread);
        }
    }

    public record Snapshot(List<ThreadState> threads, Set<String> processedIds) {
        public Snapshot {
            threads = List.copyOf(threads);
            processedIds = Set.copyOf(processedIds);
        }
    }

    private PhoneConversationFile() {}

    public static Snapshot read(Path file) throws IOException {
        if (!Files.exists(file)) return new Snapshot(List.of(), Set.of());
        Properties properties = new Properties();
        try (Reader input = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(input);
        } catch (IllegalArgumentException malformed) {
            throw new IOException("Historico de telefone invalido: " + file, malformed);
        }
        if (!"1".equals(properties.getProperty("version"))) throw new IOException("Versao de historico desconhecida");
        int count = count(properties, "thread.count", 10_000);
        List<ThreadState> threads = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String prefix = "thread." + i + ".";
            Map<String, String> thread = readRecord(properties, prefix + "data.");
            int unread = count(properties, prefix + "unread", Integer.MAX_VALUE);
            int messageCount = count(properties, prefix + "message.count", 1_000_000);
            List<Map<String, String>> messages = new ArrayList<>(messageCount);
            for (int j = 0; j < messageCount; j++) messages.add(readRecord(properties, prefix + "message." + j + "."));
            threads.add(new ThreadState(thread, messages, unread));
        }
        int processedCount = count(properties, "processed.count", 1_000_000);
        Set<String> processed = new LinkedHashSet<>();
        for (int i = 0; i < processedCount; i++) processed.add(required(properties, "processed." + i));
        return new Snapshot(threads, processed);
    }

    /** Fecha o arquivo temporario antes de substitui-lo: uma gravacao incompleta preserva o anterior. */
    public static void write(Path file, Snapshot snapshot) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("version", "1");
        properties.setProperty("thread.count", Integer.toString(snapshot.threads().size()));
        for (int i = 0; i < snapshot.threads().size(); i++) {
            ThreadState state = snapshot.threads().get(i);
            String prefix = "thread." + i + ".";
            writeRecord(properties, prefix + "data.", state.thread());
            properties.setProperty(prefix + "unread", Integer.toString(state.unread()));
            properties.setProperty(prefix + "message.count", Integer.toString(state.messages().size()));
            for (int j = 0; j < state.messages().size(); j++) writeRecord(properties, prefix + "message." + j + ".", state.messages().get(j));
        }
        properties.setProperty("processed.count", Integer.toString(snapshot.processedIds().size()));
        int index = 0;
        for (String id : snapshot.processedIds()) properties.setProperty("processed." + index++, id);
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
        try {
            try (Writer output = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                properties.store(output, "Aurorion - conversas do personagem");
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Registra os componentes do record real; Path e texto, nunca um objeto de filesystem serializado. */
    public static Map<String, String> capture(Object record) throws ReflectiveOperationException {
        if (!record.getClass().isRecord()) throw new IllegalArgumentException("O telefone mudou o formato das conversas");
        Map<String, String> values = new LinkedHashMap<>();
        for (RecordComponent component : record.getClass().getRecordComponents()) {
            Object value = component.getAccessor().invoke(record);
            if (value != null) values.put(component.getName(), value.toString());
        }
        return values;
    }

    /** Restaura sem chamar envio de pacote, notificacao, som, ou os metodos que criam mensagens. */
    public static Object restore(Class<?> type, Map<String, String> values) throws ReflectiveOperationException {
        RecordComponent[] components = type.getRecordComponents();
        if (components == null) throw new IllegalArgumentException("O telefone mudou o formato das conversas");
        Class<?>[] types = new Class<?>[components.length];
        Object[] arguments = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            RecordComponent component = components[i];
            Class<?> fieldType = types[i] = component.getType();
            String value = values.get(component.getName());
            if (value == null) {
                if (fieldType.isPrimitive()) throw new IllegalArgumentException("Campo ausente: " + component.getName());
                arguments[i] = null;
            } else if (fieldType == String.class) arguments[i] = value;
            else if (fieldType == boolean.class) {
                if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Booleano invalido");
                arguments[i] = Boolean.parseBoolean(value);
            } else if (fieldType == int.class) arguments[i] = Integer.parseInt(value);
            else if (fieldType == long.class) arguments[i] = Long.parseLong(value);
            else if (fieldType == Path.class) arguments[i] = Path.of(value);
            else throw new IllegalArgumentException("Componente desconhecido: " + fieldType.getName());
        }
        return type.getDeclaredConstructor(types).newInstance(arguments);
    }

    private static Map<String, String> readRecord(Properties properties, String prefix) throws IOException {
        int count = count(properties, prefix + "count", 64);
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            String key = required(properties, prefix + i + ".key");
            if (values.put(key, required(properties, prefix + i + ".value")) != null) throw new IOException("Campo duplicado no historico");
        }
        return values;
    }

    private static void writeRecord(Properties properties, String prefix, Map<String, String> values) {
        properties.setProperty(prefix + "count", Integer.toString(values.size()));
        int index = 0;
        for (var entry : values.entrySet()) {
            properties.setProperty(prefix + index + ".key", entry.getKey());
            properties.setProperty(prefix + index++ + ".value", entry.getValue());
        }
    }

    private static String required(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null) throw new IOException("Campo ausente no historico: " + key);
        return value;
    }

    private static int count(Properties properties, String key, int maximum) throws IOException {
        try {
            int value = Integer.parseInt(required(properties, key));
            if (value < 0 || value > maximum) throw new IOException("Quantidade invalida no historico: " + key);
            return value;
        } catch (NumberFormatException invalid) {
            throw new IOException("Quantidade invalida no historico: " + key, invalid);
        }
    }
}
