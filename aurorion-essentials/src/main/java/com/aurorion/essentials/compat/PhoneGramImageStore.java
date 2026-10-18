package com.aurorion.essentials.compat;

import com.aurorion.essentials.AurorionEssentials;
import com.aurorion.essentials.network.PhoneGramImagePayloads;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Fotos dos posts e avatares do Gram no servidor: em arquivo, fora da thread do jogo, e so para quem
 * pedir.
 *
 * <p>O telefone guarda tudo do Gram no {@code server_social.properties} (raiz do servidor), com cada
 * foto inteira em base64 dentro do arquivo, e regrava o arquivo inteiro na thread do servidor a cada
 * curtida, comentario, follow ou post — sem {@code .tmp}: um desligamento no meio da gravacao deixava
 * o arquivo truncado, a leitura seguinte falhava calada ({@code catch (Exception)}) e a gravacao
 * seguinte salvava por cima o Gram vazio. No login, o {@code syncToPlayer} mandava todas as fotos
 * para cada jogador (ate 2000 posts de ~30 KB, mais os avatares), e de novo a cada atualizacao
 * automatica do app.
 *
 * <p>O {@code PhoneGramServerStoreMixin} liga isto ao telefone:
 * <ul>
 *   <li><b>Gravar</b> ({@link #persist}): no lugar do base64, o arquivo do telefone recebe a
 *       referencia {@code aurorion:sha256:<hash>}, e a foto vai para
 *       {@code mattupolis_phone/aurorion_gram/<2 letras>/<hash>.img}, ao lado do arquivo que a cita.
 *       Mesma foto, mesmo arquivo: deduplicado pelo hash. Foto que nao e JPEG/PNG ou passa de
 *       {@link PhoneGramImageRef#MAX_IMAGE_BYTES} nao vai para o disco.</li>
 *   <li><b>Gravar o arquivo do telefone</b> ({@link #deferredStoreOutput}): o telefone escreve num
 *       buffer em memoria; o disco e com a thread {@code Aurorion Gram IO}, atomico ({@code .tmp} +
 *       move) e juntando rajadas (vale sempre a versao mais nova). As fotos citadas vao para o disco
 *       antes do arquivo que as cita.</li>
 *   <li><b>Ler</b> ({@link #restore}): referencia vira uma marca de 73 bytes — a foto so sai do disco
 *       quando alguem pedir. Base64 antigo continua sendo lido e migra sozinho na proxima gravacao.</li>
 *   <li><b>Login</b> ({@link #forSync}): quem tem o essentials recebe a marca no lugar da foto e pede
 *       pelo {@link PhoneGramImagePayloads.Request} so a que for desenhar. Quem nao tem recebe a foto
 *       inteira, como antes.</li>
 * </ul>
 *
 * <p>Os arquivos de foto que nenhum post ou avatar cita mais (post apagado, reset de personagem) sao
 * apagados no inicio do servidor, depois de 24 h sem uso.
 */
@EventBusSubscriber(modid = AurorionEssentials.MOD_ID)
public final class PhoneGramImageStore {
    private static final String PHONE_MOD = "mattupolis_phone";
    private static final String EXTENSION = ".img";
    /** Quanto o desligamento espera o disco antes de desistir. */
    private static final long DRAIN_TIMEOUT_SECONDS = 10;
    /** Fotos ja lidas que ficam em memoria para os proximos pedidos (30 jogadores abrindo o mesmo feed). */
    private static final int CACHE_LIMIT_BYTES = 16 * 1024 * 1024;
    /** Pedidos de foto por jogador por segundo. O app pede so o que esta na tela; isto e teto contra abuso. */
    private static final int REQUESTS_PER_SECOND = 40;
    private static final long ORPHAN_AGE_MILLIS = TimeUnit.HOURS.toMillis(24);
    private static final Pattern REF = Pattern.compile(Pattern.quote(PhoneGramImageRef.REF_PREFIX) + "([0-9a-f]{64})");

    private record StoreSnapshot(Path file, byte[] content) {
    }

    /** O hash de cada array ja visto, pela identidade do array: os records do telefone o mantem vivo. */
    private static final Map<byte[], String> HASHES = Collections.synchronizedMap(new WeakHashMap<>());
    /** Hashes cuja foto ja esta no disco. */
    private static final Set<String> ON_DISK = ConcurrentHashMap.newKeySet();
    /** Fotos esperando a thread de IO. Servem pedidos daqui mesmo ate chegarem no disco. */
    private static final Map<String, byte[]> PENDING_IMAGES = new ConcurrentHashMap<>();
    /** A versao mais nova do arquivo do telefone que ainda nao foi para o disco. */
    private static final AtomicReference<StoreSnapshot> PENDING_STORE = new AtomicReference<>();
    private static final LinkedHashMap<String, byte[]> CACHE = new LinkedHashMap<>(64, 0.75F, true);
    private static int cachedBytes;
    /** Ligado durante o {@code syncToPlayer} de um jogador que sabe pedir foto. */
    private static final ThreadLocal<Boolean> LAZY_SYNC = new ThreadLocal<>();
    /** Janela de um segundo de pedidos por jogador: {inicio, quantidade}. So na thread do servidor. */
    private static final Map<UUID, long[]> REQUESTS = new HashMap<>();
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
    private static volatile Method avatarAccessor;
    /** So a thread de IO mexe. */
    private static boolean backupChecked;
    private static ExecutorService io;

    private PhoneGramImageStore() {
    }

    // ---------------------------------------------------------------------------------------------
    // Gravacao (thread do servidor, dentro do synchronized do telefone)

    /** No lugar do {@code Base64.getEncoder().encodeToString(foto)} do {@code saveGramPosts}/{@code saveGramProfiles}. */
    public static String persist(Base64.Encoder encoder, byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        String marked = PhoneGramImageRef.hashOfMark(bytes);
        if (marked != null) return PhoneGramImageRef.ref(marked);
        String hash = hashOf(bytes);
        if (!PhoneGramImageRef.isStorableImage(bytes)) {
            reportOnce(hash, "Foto do Gram fora do formato ou acima de {} bytes ({} bytes); nao foi gravada.",
                    PhoneGramImageRef.MAX_IMAGE_BYTES, bytes.length);
            return "";
        }
        queueWrite(hash, bytes);
        return PhoneGramImageRef.ref(hash);
    }

    /** No lugar do {@code bytesFromB64} do {@code loadGramPosts}/{@code loadGramProfiles}. */
    public static byte[] restore(String value) {
        String hash = PhoneGramImageRef.hashOfRef(value);
        if (hash != null) return PhoneGramImageRef.mark(hash);
        // Formato antigo do telefone: a foto inteira em base64. A proxima gravacao a passa para arquivo.
        if (value == null || value.isEmpty()) return new byte[0];
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            return new byte[0];
        }
    }

    /**
     * No lugar do {@code Files.newOutputStream} do {@code savePersistentStore}: o telefone escreve em
     * memoria, e o fechamento entrega o conteudo a thread de IO.
     */
    public static OutputStream deferredStoreOutput(Path file) {
        return new ByteArrayOutputStream(64 * 1024) {
            private boolean closed;

            @Override
            public void close() {
                if (closed) return;
                closed = true;
                PENDING_STORE.set(new StoreSnapshot(file, toByteArray()));
                executor().execute(PhoneGramImageStore::flush);
            }
        };
    }

    // ---------------------------------------------------------------------------------------------
    // Login (syncToPlayer)

    public static void beginSync(ServerPlayer player) {
        LAZY_SYNC.set(player != null && canRequest(player));
    }

    public static void endSync() {
        LAZY_SYNC.remove();
    }

    /** A foto de cada post e avatar que o {@code syncToPlayer} manda. */
    public static byte[] forSync(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return bytes;
        String marked = PhoneGramImageRef.hashOfMark(bytes);
        if (Boolean.TRUE.equals(LAZY_SYNC.get())) {
            if (marked != null || !PhoneGramImageRef.isStorableImage(bytes)) return bytes;
            String hash = hashOf(bytes);
            queueWrite(hash, bytes);
            return PhoneGramImageRef.mark(hash);
        }
        // Cliente sem o essentials: nao sabe pedir, precisa da foto inteira, como no telefone original.
        if (marked == null) return bytes;
        byte[] real = fromMemory(marked);
        if (real == null) real = readFile(marked);
        return real == null ? new byte[0] : real;
    }

    // ---------------------------------------------------------------------------------------------
    // Pedidos de foto

    /** Thread do servidor. Responde com os bytes, ou vazio se o servidor nao tem a foto. */
    public static void request(ServerPlayer player, String hash) {
        if (!PhoneGramImageRef.isHash(hash) || !allow(player)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        byte[] ready = fromMemory(hash);
        if (ready != null) {
            send(player, hash, ready);
            return;
        }
        executor().execute(() -> {
            byte[] bytes = readFile(hash);
            server.execute(() -> send(player, hash, bytes == null ? new byte[0] : bytes));
        });
    }

    private static boolean allow(ServerPlayer player) {
        long now = System.currentTimeMillis();
        long[] window = REQUESTS.computeIfAbsent(player.getUUID(), id -> new long[2]);
        if (now - window[0] >= 1000) {
            window[0] = now;
            window[1] = 0;
        }
        return ++window[1] <= REQUESTS_PER_SECOND;
    }

    private static void send(ServerPlayer player, String hash, byte[] bytes) {
        if (player.hasDisconnected() || !canRequest(player)) return;
        PacketDistributor.sendToPlayer(player, new PhoneGramImagePayloads.Image(hash, bytes));
    }

    private static boolean canRequest(ServerPlayer player) {
        return player.connection != null && player.connection.hasChannel(PhoneGramImagePayloads.Image.TYPE);
    }

    // ---------------------------------------------------------------------------------------------
    // Avatar

    /**
     * O avatar guardado num {@code GramProfile} do telefone. O {@code PhoneGramServerStoreMixin} usa
     * para nao apagar o avatar quando o perfil e salvo sem foto (ver la).
     */
    public static byte[] avatarOf(Object profile) {
        if (profile == null) return null;
        try {
            Method accessor = avatarAccessor;
            if (accessor == null || accessor.getDeclaringClass() != profile.getClass()) {
                accessor = profile.getClass().getDeclaredMethod("avatarBytes");
                accessor.setAccessible(true);
                avatarAccessor = accessor;
            }
            return accessor.invoke(profile) instanceof byte[] bytes ? bytes : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            reportOnce("avatarBytes", "Nao consegui ler o avatar do Gram guardado; o perfil segue como o telefone mandou.", e);
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Eventos

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!ModList.get().isLoaded(PHONE_MOD)) return;
        executor().execute(PhoneGramImageStore::collectOrphans);
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        REQUESTS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ExecutorService executor;
        synchronized (PhoneGramImageStore.class) {
            executor = io;
            io = null;
        }
        if (executor != null) {
            try {
                // Uma thread so: quando esta tarefa termina, tudo o que foi pedido antes ja terminou.
                executor.submit(PhoneGramImageStore::flush).get(DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                AurorionEssentials.LOGGER.warn("O disco nao respondeu em {}s ao gravar o Gram do telefone; "
                        + "seguindo sem esperar.", DRAIN_TIMEOUT_SECONDS);
            } catch (Exception e) {
                AurorionEssentials.LOGGER.warn("Falha esperando a gravacao do Gram do telefone.", e);
            }
            executor.shutdown();
        }
        REQUESTS.clear();
        synchronized (CACHE) {
            CACHE.clear();
            cachedBytes = 0;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Disco (thread de IO)

    private static String hashOf(byte[] bytes) {
        String hash = HASHES.get(bytes);
        if (hash == null) {
            hash = PhoneGramImageRef.sha256(bytes);
            HASHES.put(bytes, hash);
        }
        return hash;
    }

    private static void queueWrite(String hash, byte[] bytes) {
        if (ON_DISK.contains(hash) || PENDING_IMAGES.putIfAbsent(hash, bytes) != null) return;
        executor().execute(PhoneGramImageStore::flush);
    }

    /**
     * Pega primeiro a versao do arquivo do telefone e so depois grava as fotos da fila: toda foto que
     * essa versao cita entrou na fila antes dela, entao chega no disco antes do arquivo que a cita.
     */
    private static void flush() {
        StoreSnapshot store = PENDING_STORE.getAndSet(null);
        for (String hash : new ArrayList<>(PENDING_IMAGES.keySet())) {
            byte[] bytes = PENDING_IMAGES.get(hash);
            if (bytes == null) continue;
            try {
                Path file = imageFile(hash);
                if (!Files.exists(file)) writeAtomic(file, bytes);
                ON_DISK.add(hash);
            } catch (IOException | RuntimeException e) {
                // Fica fora do ON_DISK: a proxima gravacao do telefone tenta de novo (os bytes seguem no record).
                reportOnce("write:" + hash, "Nao foi possivel gravar a foto do Gram {}", hash, e);
            } finally {
                PENDING_IMAGES.remove(hash, bytes);
            }
        }
        if (store == null) return;
        try {
            backupOnce(store.file());
            writeAtomic(store.file(), store.content());
        } catch (IOException | RuntimeException e) {
            AurorionEssentials.LOGGER.warn("Nao foi possivel gravar {}", store.file(), e);
        }
    }

    private static byte[] fromMemory(String hash) {
        byte[] pending = PENDING_IMAGES.get(hash);
        if (pending != null) return pending;
        synchronized (CACHE) {
            return CACHE.get(hash);
        }
    }

    private static byte[] readFile(String hash) {
        byte[] memory = fromMemory(hash);
        if (memory != null) return memory;
        Path file = imageFile(hash);
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > PhoneGramImageRef.MAX_IMAGE_BYTES) return null;
            byte[] bytes = Files.readAllBytes(file);
            ON_DISK.add(hash);
            remember(hash, bytes);
            return bytes;
        } catch (IOException | RuntimeException e) {
            reportOnce("read:" + hash, "Nao foi possivel ler a foto do Gram {}", file, e);
            return null;
        }
    }

    private static void remember(String hash, byte[] bytes) {
        synchronized (CACHE) {
            byte[] previous = CACHE.put(hash, bytes);
            if (previous != null) cachedBytes -= previous.length;
            cachedBytes += bytes.length;
            Iterator<Map.Entry<String, byte[]>> eldest = CACHE.entrySet().iterator();
            while (cachedBytes > CACHE_LIMIT_BYTES && eldest.hasNext()) {
                cachedBytes -= eldest.next().getValue().length;
                eldest.remove();
            }
        }
    }

    /**
     * Apaga as fotos que o arquivo do telefone nao cita mais. Le a versao que esta no disco (nesta
     * thread, entao nenhuma gravacao esta no meio). Sem nenhuma referencia no arquivo — arquivo
     * sumido, ilegivel, ou o essentials acabou de chegar — nao apaga nada.
     */
    private static void collectOrphans() {
        Path store = phoneDir().resolve("server_social.properties");
        Path dir = imageDir();
        if (!Files.isRegularFile(store) || !Files.isDirectory(dir)) return;
        Set<String> referenced = new HashSet<>();
        try {
            Matcher matcher = REF.matcher(Files.readString(store, StandardCharsets.ISO_8859_1));
            while (matcher.find()) referenced.add(matcher.group(1));
        } catch (IOException | RuntimeException e) {
            AurorionEssentials.LOGGER.warn("Nao consegui ler {} para limpar as fotos do Gram.", store, e);
            return;
        }
        if (referenced.isEmpty()) return;
        long cutoff = System.currentTimeMillis() - ORPHAN_AGE_MILLIS;
        int removed = 0;
        try (Stream<Path> files = Files.walk(dir, 2)) {
            for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                String name = file.getFileName().toString();
                String hash = name.endsWith(EXTENSION) ? name.substring(0, name.length() - EXTENSION.length()) : null;
                if (hash != null && referenced.contains(hash)) {
                    ON_DISK.add(hash);
                    continue;
                }
                if (hash != null && PENDING_IMAGES.containsKey(hash)) continue;
                if (Files.getLastModifiedTime(file).toMillis() > cutoff) continue;
                Files.deleteIfExists(file);
                if (hash != null) ON_DISK.remove(hash);
                removed++;
            }
        } catch (IOException | RuntimeException e) {
            AurorionEssentials.LOGGER.warn("Falha limpando as fotos do Gram sem uso em {}.", dir, e);
        }
        if (removed > 0) AurorionEssentials.LOGGER.info("Gram: {} foto(s) sem post nem perfil apagada(s).", removed);
    }

    /**
     * Antes da primeira gravacao com o essentials, uma copia do arquivo do telefone como ele estava
     * (fotos em base64): e o caminho de volta se for preciso tirar o essentials. Uma vez so — depois
     * dela o arquivo ja tem referencias, e a copia existente nunca e sobrescrita.
     */
    private static void backupOnce(Path file) {
        if (backupChecked) return;
        backupChecked = true;
        Path backup = file.resolveSibling(file.getFileName() + ".antes-aurorion");
        try {
            if (Files.isRegularFile(file) && !Files.exists(backup)) Files.copy(file, backup);
        } catch (IOException | RuntimeException e) {
            AurorionEssentials.LOGGER.warn("Nao consegui copiar {} antes da primeira gravacao; seguindo.", file, e);
        }
    }

    static void writeAtomic(Path file, byte[] content) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(temp, content);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** O mesmo diretorio do {@code persistentStorePath} do telefone: a raiz do servidor. */
    private static Path phoneDir() {
        return Path.of(System.getProperty("user.dir", "."), PHONE_MOD);
    }

    static Path imageDir() {
        return phoneDir().resolve("aurorion_gram");
    }

    static Path imageFile(String hash) {
        return imageDir().resolve(hash.substring(0, 2)).resolve(hash + EXTENSION);
    }

    private static synchronized ExecutorService executor() {
        if (io == null) {
            io = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "Aurorion Gram IO");
                // Daemon: um disco travado nao segura o processo aberto. A gravacao atomica garante que,
                // no pior caso, perde-se a ultima mudanca e nao o arquivo.
                thread.setDaemon(true);
                return thread;
            });
        }
        return io;
    }

    private static void reportOnce(String key, String message, Object... args) {
        if (REPORTED.add(key)) AurorionEssentials.LOGGER.warn(message, args);
    }
}
