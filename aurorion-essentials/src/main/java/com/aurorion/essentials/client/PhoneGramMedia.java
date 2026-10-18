package com.aurorion.essentials.client;

import com.aurorion.essentials.AurorionEssentials;
import com.aurorion.essentials.compat.PhoneGramImageRef;
import com.aurorion.essentials.network.PhoneGramImagePayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * O lado do cliente do Gram vindo do servidor: fotos sob demanda e o perfil proprio.
 *
 * <p><b>Fotos sob demanda.</b> No login o servidor manda, no lugar de cada foto, a marca de
 * {@link PhoneGramImageRef} (ver {@code PhoneGramImageStore}). O {@code PhoneGramStoreMixin} anota
 * aqui qual post/perfil tem qual foto, e quando o app vai desenhar um post ou avatar sem foto, ela e
 * pedida ao servidor uma vez. A resposta entra nos mesmos mapas que o telefone usa para foto de outro
 * jogador, e o proprio telefone decodifica fora da thread do jogo como sempre fez. Feed rolando nao
 * pede nada que nao apareceu na tela.
 *
 * <p><b>Perfil proprio.</b> O app desenha o avatar proprio so por um {@code Path} local. O avatar que
 * o servidor manda vira um PNG na pasta do personagem ({@code aurorion_gram_avatar-<hash>.png}), e e
 * esse caminho que o app passa a ver quando nao ha foto escolhida nesta sessao. PNG porque a galeria
 * do telefone so le PNG.
 *
 * <p>Na entrada e na saida de um servidor o perfil proprio volta a vazio, como o {@code PhoneSession}
 * faz com o resto do celular: o alt nao herda nome, bio e avatar do principal, e o servidor manda o
 * de cada um logo em seguida.
 *
 * <p>Tudo aqui roda na thread do cliente, menos a conversao do avatar para PNG.
 */
@EventBusSubscriber(modid = AurorionEssentials.MOD_ID, value = Dist.CLIENT)
public final class PhoneGramMedia {
    private static final String STORE = "com.mattupolis.phone.client.gui.PhoneInstagramStore";
    private static final String POST = STORE + "$GramPost";
    /** Pedido sem resposta pode ser repetido depois disto (pacote perdido, limite do servidor). */
    private static final long RETRY_MILLIS = 15_000;
    private static final String AVATAR_PREFIX = "aurorion_gram_avatar-";

    private record LazyAvatar(String playerName, String hash) {
    }

    /** Post -> hash da foto que o servidor tem. */
    private static final Map<String, String> LAZY_POSTS = new HashMap<>();
    /** Perfil (chave do telefone) -> foto ainda nao recebida. */
    private static final Map<String, LazyAvatar> LAZY_AVATARS = new HashMap<>();
    /** Perfil -> hash do avatar ja aplicado: a ressincronizacao periodica do app nao pede de novo. */
    private static final Map<String, String> APPLIED_AVATARS = new HashMap<>();
    private static final Map<String, Long> REQUESTED = new HashMap<>();
    /** Hashes que o servidor respondeu sem foto: nao adianta pedir de novo nesta sessao. */
    private static final Set<String> MISSING = new HashSet<>();

    @Nullable
    private static Path ownAvatar;
    @Nullable
    private static String ownAvatarHash;
    private static ExecutorService avatarWriter;
    private static boolean reportedFailure;

    private static Constructor<?> postConstructor;
    private static Field remoteBytesField;
    private static Method receiveProfile;

    private PhoneGramMedia() {
    }

    // ---------------------------------------------------------------------------------------------
    // Posts

    public static void lazyPost(String postId, String hash) {
        if (postId != null && !postId.isBlank()) LAZY_POSTS.put(postId, hash);
    }

    /** O app vai desenhar este post e nao tem a foto. */
    public static void wantPost(String postId) {
        if (LAZY_POSTS.isEmpty()) return;
        String hash = LAZY_POSTS.get(postId);
        if (hash != null) request(hash);
    }

    public static boolean isPhotoComing(String postId) {
        if (postId == null || LAZY_POSTS.isEmpty()) return false;
        String hash = LAZY_POSTS.get(postId);
        return hash != null && !MISSING.contains(hash);
    }

    /** Um {@code GramPost} do telefone, igual ao que ele cria para o post de outro autor. */
    @Nullable
    public static Object newPost(String postId, String username, String caption, int color) {
        try {
            if (postConstructor == null) {
                postConstructor = Class.forName(POST).getConstructor(String.class, String.class, String.class,
                        int.class, int.class, boolean.class, Path.class);
            }
            return postConstructor.newInstance(postId, username, caption == null ? "" : caption, color, 0, true, null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            report("GramPost", e);
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Avatares

    public static void lazyAvatar(String key, String playerName, String hash, boolean self) {
        if (hash.equals(APPLIED_AVATARS.get(key))) return;
        LAZY_AVATARS.put(key, new LazyAvatar(playerName, hash));
        if (!self) return;
        // O perfil proprio desenha o avatar por arquivo, entao ele e pedido ja — a menos que a copia
        // desta mesma foto tenha ficado de uma sessao anterior.
        Path dir = PhoneCharacterStorage.activeDir();
        Path cached = dir == null ? null : avatarFile(dir, hash);
        if (cached != null && Files.isRegularFile(cached)) {
            ownAvatar = cached;
            ownAvatarHash = hash;
            return;
        }
        request(hash);
    }

    /** O avatar deste perfil chegou inteiro (mudou ao vivo, ou e a resposta de um pedido). */
    public static void avatarArrived(String key) {
        if (!LAZY_AVATARS.isEmpty()) LAZY_AVATARS.remove(key);
    }

    /** O app vai desenhar o avatar deste perfil. */
    public static void wantAvatar(String key) {
        if (LAZY_AVATARS.isEmpty()) return;
        LazyAvatar avatar = LAZY_AVATARS.get(key);
        if (avatar != null) request(avatar.hash());
    }

    @Nullable
    public static Path ownAvatar() {
        return ownAvatar;
    }

    /** Copia em PNG, na pasta do personagem, do avatar proprio que o servidor tem. */
    public static void cacheOwnAvatar(byte[] bytes) {
        Path dir = PhoneCharacterStorage.activeDir();
        if (dir == null) return;
        byte[] copy = bytes.clone();
        String hash = PhoneGramImageRef.sha256(copy);
        if (hash.equals(ownAvatarHash) && ownAvatar != null) return;
        Path target = avatarFile(dir, hash);
        writer().execute(() -> {
            try {
                if (!Files.isRegularFile(target)) writePng(target, copy);
                forgetOtherAvatars(dir, target);
            } catch (IOException | RuntimeException e) {
                report("avatar " + target, e);
                return;
            }
            Minecraft.getInstance().execute(() -> {
                // A pasta mudou no meio (troca de personagem): a copia e do anterior.
                if (!dir.equals(PhoneCharacterStorage.activeDir())) return;
                ownAvatar = target;
                ownAvatarHash = hash;
            });
        });
    }

    private static Path avatarFile(Path dir, String hash) {
        return dir.resolve(AVATAR_PREFIX + hash.substring(0, 16) + ".png");
    }

    private static void writePng(Path target, byte[] bytes) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) throw new IOException("Formato de imagem nao reconhecido.");
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            if (!ImageIO.write(image, "png", temp.toFile())) throw new IOException("Sem escritor PNG.");
        } finally {
            image.flush();
        }
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** O nome muda com a foto; a galeria do telefone guarda textura por caminho, e nunca reabre a antiga. */
    private static void forgetOtherAvatars(Path dir, Path keep) throws IOException {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, AVATAR_PREFIX + "*.png")) {
            for (Path file : files) {
                if (!file.equals(keep)) Files.deleteIfExists(file);
            }
        }
    }

    private static synchronized ExecutorService writer() {
        if (avatarWriter == null) {
            avatarWriter = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "Aurorion Gram avatar");
                thread.setDaemon(true);
                return thread;
            });
        }
        return avatarWriter;
    }

    // ---------------------------------------------------------------------------------------------
    // Rede

    private static void request(String hash) {
        if (MISSING.contains(hash)) return;
        long now = System.currentTimeMillis();
        Long last = REQUESTED.get(hash);
        if (last != null && now - last < RETRY_MILLIS) return;
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null || !connection.hasChannel(PhoneGramImagePayloads.Request.TYPE)) return;
        REQUESTED.put(hash, now);
        PacketDistributor.sendToServer(new PhoneGramImagePayloads.Request(hash));
    }

    /** A foto pedida chegou (thread do cliente). Vazio: o servidor nao a tem. */
    public static void receive(String hash, byte[] bytes) {
        REQUESTED.remove(hash);
        if (bytes == null || bytes.length == 0) {
            MISSING.add(hash);
            return;
        }
        Map<String, byte[]> remote = remoteBytes();
        if (remote != null) {
            for (Map.Entry<String, String> entry : LAZY_POSTS.entrySet()) {
                if (hash.equals(entry.getValue())) remote.put(entry.getKey(), bytes);
            }
        }
        List<LazyAvatar> avatars = new ArrayList<>();
        LAZY_AVATARS.entrySet().removeIf(entry -> {
            if (!hash.equals(entry.getValue().hash())) return false;
            APPLIED_AVATARS.put(entry.getKey(), hash);
            avatars.add(entry.getValue());
            return true;
        });
        for (LazyAvatar avatar : avatars) applyAvatar(avatar.playerName(), bytes);
    }

    /** Pelo proprio {@code receiveRemoteProfile}: so a foto, sem mexer em nome e bio (nulos). */
    private static void applyAvatar(String playerName, byte[] bytes) {
        try {
            if (receiveProfile == null) {
                receiveProfile = Class.forName(STORE).getMethod("receiveRemoteProfile",
                        String.class, String.class, String.class, byte[].class);
            }
            receiveProfile.invoke(null, playerName, null, null, bytes);
        } catch (ReflectiveOperationException | RuntimeException e) {
            report("receiveRemoteProfile", e);
        }
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static Map<String, byte[]> remoteBytes() {
        try {
            if (remoteBytesField == null) {
                Field field = Class.forName(STORE).getDeclaredField("REMOTE_POST_IMAGE_BYTES");
                field.setAccessible(true);
                remoteBytesField = field;
            }
            return (Map<String, byte[]>) remoteBytesField.get(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            report("REMOTE_POST_IMAGE_BYTES", e);
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Sessao

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        resetSession();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        resetSession();
    }

    private static void resetSession() {
        LAZY_POSTS.clear();
        LAZY_AVATARS.clear();
        APPLIED_AVATARS.clear();
        REQUESTED.clear();
        MISSING.clear();
        ownAvatar = null;
        ownAvatarHash = null;
        if (!ModList.get().isLoaded("mattupolis_phone")) return;
        // Perfil proprio de celular recem-ligado: bio vazia mostra o texto padrao do proprio telefone.
        set("profileAvatarPhoto", null);
        set("profileDisplayName", "");
        set("profileBio", "");
    }

    private static void set(String name, @Nullable Object value) {
        try {
            Field field = Class.forName(STORE).getDeclaredField(name);
            field.setAccessible(true);
            field.set(null, value);
        } catch (ReflectiveOperationException | RuntimeException e) {
            report(STORE + "." + name, e);
        }
    }

    private static void report(String what, Exception e) {
        if (reportedFailure) return;
        reportedFailure = true;
        AurorionEssentials.LOGGER.warn("Gram: nao consegui usar {} do telefone; o perfil e as fotos seguem "
                + "como o telefone faz sozinho.", what, e);
    }
}
