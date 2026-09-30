package com.aurorion.essentials.compat;

import com.aurorion.essentials.AurorionEssentials;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Grava o {@code phone_numbers.properties} do telefone fora da thread do servidor.
 *
 * <p>O {@code PhoneNumberStoreMixin} troca o {@code save()} original por {@link #save}: a foto do
 * diretorio e tirada na hora (ainda dentro do {@code synchronized} do telefone, entao os mapas estao
 * consistentes), mas a escrita vai para uma thread propria. Se o disco travar, trava essa thread — o
 * tick continua, e o Watchdog nao tem o que matar.
 *
 * <p>Tres coisas a mais que o original nao fazia:
 * <ul>
 *   <li><b>Descarta foto repetida.</b> Mesmo com o atalho do mixin, o telefone ainda chama
 *       {@code save()} sem ter mudado nada (toda busca por numero, por exemplo).</li>
 *   <li><b>Junta rajadas.</b> Varias fotos seguidas viram uma gravacao so: a thread de IO pega sempre
 *       a mais recente.</li>
 *   <li><b>Grava atomico.</b> Escreve num {@code .tmp} e move por cima. Um crash no meio deixa o
 *       arquivo anterior inteiro, em vez de um diretorio de numeros truncado.</li>
 * </ul>
 */
@EventBusSubscriber(modid = AurorionEssentials.MOD_ID)
public final class PhoneNumberSaveQueue {
    private static final String COMMENT = "Mattupolis Phone Number Directory";
    /** Quanto o desligamento e o reset de personagem esperam o disco antes de desistir. */
    private static final long DRAIN_TIMEOUT_SECONDS = 10;

    private record Snapshot(Path file, List<String> entries) {
    }

    /** A foto mais nova que ainda nao foi para o disco. A thread de IO sempre pega a ultima. */
    private static final AtomicReference<Snapshot> PENDING = new AtomicReference<>();
    /** O que foi entregue por ultimo a thread de IO. {@code null} = nao sabemos o que esta no disco. */
    private static volatile Snapshot lastQueued;
    private static ExecutorService io;

    private PhoneNumberSaveQueue() {
    }

    /**
     * No lugar do {@code PhoneNumberServerStore.save()}. Roda com o monitor da classe do telefone na
     * mao — todo caminho que chega no {@code save()} original e {@code synchronized} — entao ler os
     * mapas aqui e seguro.
     */
    public static void save(MinecraftServer server, Map<UUID, String> uuidToNumber, Map<UUID, String> uuidToName) {
        Snapshot snapshot = new Snapshot(storeFile(server), PhoneNumberDirectory.entries(uuidToNumber, uuidToName));
        if (snapshot.equals(lastQueued)) return;
        lastQueued = snapshot;
        PENDING.set(snapshot);
        executor().execute(PhoneNumberSaveQueue::writePending);
    }

    /**
     * Espera toda gravacao pendente chegar no disco. Quem vai ler ou reescrever o arquivo por fora (o
     * reset de personagem) chama isto antes, senao uma gravacao atrasada desfaria a mudanca dele.
     */
    public static void awaitWrites() {
        ExecutorService executor;
        synchronized (PhoneNumberSaveQueue.class) {
            executor = io;
        }
        if (executor == null) return;
        try {
            // Um executor de uma thread so: quando esta tarefa vazia termina, tudo que foi pedido antes
            // dela ja terminou.
            executor.submit(() -> { }).get(DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            AurorionEssentials.LOGGER.warn("O disco nao respondeu em {}s ao gravar o diretorio de numeros do "
                    + "telefone; seguindo sem esperar.", DRAIN_TIMEOUT_SECONDS);
        } catch (Exception e) {
            AurorionEssentials.LOGGER.warn("Falha esperando a gravacao do diretorio de numeros do telefone.", e);
        }
    }

    /**
     * Esquece o que foi gravado por ultimo. Obrigatorio depois de alguem mexer no arquivo por fora:
     * sem isto, a proxima foto igual a anterior seria descartada mesmo com o disco diferente.
     */
    public static void forgetLastWrite() {
        lastQueued = null;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        awaitWrites();
        ExecutorService executor;
        synchronized (PhoneNumberSaveQueue.class) {
            executor = io;
            io = null;
        }
        if (executor != null) executor.shutdown();
        forgetLastWrite();
        // O proximo mundo (so acontece no singleplayer) tem de recarregar o diretorio do disco, e o
        // atalho do mixin so vale com o diretorio ja carregado.
        MattupolisPhoneCompat.forgetLoadedRuntimeState();
    }

    private static synchronized ExecutorService executor() {
        if (io == null) {
            io = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "Aurorion Phone IO");
                // Daemon: um disco travado nao pode segurar o processo aberto. A gravacao atomica
                // garante que, no pior caso, perde-se a ultima mudanca e nao o arquivo.
                thread.setDaemon(true);
                return thread;
            });
        }
        return io;
    }

    private static void writePending() {
        Snapshot snapshot = PENDING.getAndSet(null);
        if (snapshot == null) return;
        try {
            write(snapshot.file(), snapshot.entries());
        } catch (IOException | RuntimeException e) {
            // A proxima mudanca tenta de novo com o diretorio inteiro.
            if (lastQueued == snapshot) lastQueued = null;
            AurorionEssentials.LOGGER.warn("Nao foi possivel gravar {}", snapshot.file(), e);
        }
    }

    static void write(Path file, List<String> entries) throws IOException {
        Properties properties = new Properties();
        for (int i = 0; i < entries.size(); i++) properties.setProperty("entry." + i, entries.get(i));
        properties.setProperty("entry.count", String.valueOf(entries.size()));

        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            properties.store(writer, COMMENT);
        }
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** O mesmo caminho do {@code getStoreFile} do telefone. */
    private static Path storeFile(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("mattupolis_phone").resolve("phone_numbers.properties");
    }
}
