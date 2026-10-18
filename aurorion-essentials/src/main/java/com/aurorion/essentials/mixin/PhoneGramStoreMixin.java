package com.aurorion.essentials.mixin;

import com.aurorion.essentials.client.PhoneGramMedia;
import com.aurorion.essentials.compat.PhoneGramImageRef;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * O perfil do Gram no cliente passa a vir do servidor — inclusive o do proprio jogador.
 *
 * <p>O telefone so guarda o Gram em memoria no cliente, e o servidor manda tudo de novo no login. Mas
 * o {@code PhoneInstagramStore} jogava fora o que era do proprio jogador:
 * <ul>
 *   <li>{@code receiveMultiplayerPhotoPost}/{@code receiveMultiplayerPost} com o autor igual ao
 *       jogador so anotam a hora — o post proprio existia apenas como o caminho local da foto, criado
 *       ao postar. Reiniciou o jogo, o perfil proprio ficava vazio (os outros ainda viam os posts);</li>
 *   <li>o perfil proprio desenha so {@code profileAvatarPhoto} (um {@code Path} em memoria), nunca o
 *       avatar que o servidor mandou, e bio e nome tambem eram so memoria. Reiniciou, sumiam — e a tela
 *       de editar, aberta com avatar nulo e bio padrao, gravava isso no servidor para todo mundo.</li>
 * </ul>
 *
 * <p>Aqui o post proprio que vem do servidor entra na lista como o de qualquer autor, e o perfil
 * proprio (nome, bio, avatar) e preenchido pelo que o servidor mandou ({@link PhoneGramMedia}).
 *
 * <p>Fotos sob demanda: quando o servidor manda a marca de {@link PhoneGramImageRef} no lugar da foto,
 * o post entra sem bytes e a foto e pedida so quando o app for desenha-la ({@code getTextureForPost},
 * {@code getProfileAvatarTextureForUser}). Enquanto isso o app mostra o "carregando" dele.
 */
@Pseudo
@Mixin(targets = "com.mattupolis.phone.client.gui.PhoneInstagramStore", remap = false)
public abstract class PhoneGramStoreMixin {
    @Unique
    private static final byte[] aurorion_essentials$NO_BYTES = new byte[0];
    /** A cor que o proprio telefone usa em post com foto. */
    @Unique
    private static final int aurorion_essentials$PHOTO_COLOR = -15656921;

    @Shadow @Final private static List<Object> PLAYER_POSTS;
    @Shadow @Final private static Map<String, byte[]> REMOTE_POST_IMAGE_BYTES;
    @Shadow private static String profileDisplayName;
    @Shadow private static String profileBio;
    @Shadow private static Path profileAvatarPhoto;

    @Shadow
    private static String getPlayerName() {
        throw new AssertionError();
    }

    @Shadow
    private static String normalizeProfileKey(String value) {
        throw new AssertionError();
    }

    @Shadow
    public static String sanitizeCaption(String caption) {
        throw new AssertionError();
    }

    @Shadow
    public static void receiveMultiplayerPhotoPost(String postId, String username, String caption, byte[] imageBytes,
                                                   long createdGameTime, long serverGameTime) {
        throw new AssertionError();
    }

    @Shadow
    public static void receiveRemoteProfile(String playerName, String displayName, String bio, byte[] avatarBytes) {
        throw new AssertionError();
    }

    @Inject(method = "receiveMultiplayerPhotoPost(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[BJJ)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void aurorion_essentials$photoPost(String postId, String username, String caption, byte[] imageBytes,
                                                      long createdGameTime, long serverGameTime, CallbackInfo ci) {
        String lazy = PhoneGramImageRef.hashOfMark(imageBytes);
        if (lazy != null) PhoneGramMedia.lazyPost(postId, lazy);
        aurorion_essentials$restoreOwnPost(postId, username, caption, aurorion_essentials$PHOTO_COLOR,
                lazy == null ? imageBytes : null);
        if (lazy == null) return;
        // A marca nao e foto: o original segue sem bytes, e a foto chega quando for pedida.
        ci.cancel();
        receiveMultiplayerPhotoPost(postId, username, caption, aurorion_essentials$NO_BYTES, createdGameTime, serverGameTime);
    }

    @Inject(method = "receiveMultiplayerPost(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;IJJ)V",
            at = @At("HEAD"), require = 0)
    private static void aurorion_essentials$colorPost(String postId, String username, String caption, int imageColor,
                                                      long createdGameTime, long serverGameTime, CallbackInfo ci) {
        aurorion_essentials$restoreOwnPost(postId, username, caption, imageColor, null);
    }

    /**
     * O post do proprio jogador que o servidor mandou e o cliente ainda nao tem (o jogo foi reiniciado,
     * ou o post foi feito em outro PC). O que o original faria com o post de outro autor, sem a
     * notificacao "postou uma foto".
     */
    @Unique
    private static void aurorion_essentials$restoreOwnPost(String postId, String username, String caption, int color,
                                                           byte[] imageBytes) {
        if (postId == null || postId.isBlank()) return;
        String author = normalizeProfileKey(username);
        if (author.isEmpty() || !author.equals(normalizeProfileKey(getPlayerName()))) return;
        for (Object known : PLAYER_POSTS) {
            // Sem o accessor nao da para saber se o post ja esta na lista; melhor nao duplicar.
            if (!(known instanceof PhoneGramPostAccessor post)) return;
            if (postId.equals(post.aurorion_essentials$postId())) return;
        }
        Object post = PhoneGramMedia.newPost(postId, username, sanitizeCaption(caption), color);
        if (post == null) return;
        PLAYER_POSTS.add(0, post);
        if (imageBytes != null && imageBytes.length > 0) {
            REMOTE_POST_IMAGE_BYTES.put(postId, Arrays.copyOf(imageBytes, imageBytes.length));
        }
    }

    /**
     * A primeira leitura do {@code getTextureForPost}: {@code REMOTE_POST_TEXTURES.get(post.id())}. Sem
     * textura e sem bytes, e a hora de pedir a foto (uma vez; {@link PhoneGramMedia} segura repeticoes).
     */
    @Redirect(method = "getTextureForPost(Lcom/mattupolis/phone/client/gui/PhoneInstagramStore$GramPost;)Lnet/minecraft/resources/ResourceLocation;",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;", ordinal = 0),
            require = 0)
    private static Object aurorion_essentials$fetchPhoto(Map<?, ?> remoteTextures, Object postId) {
        Object texture = remoteTextures.get(postId);
        if (texture == null && postId instanceof String id && !REMOTE_POST_IMAGE_BYTES.containsKey(id)) {
            PhoneGramMedia.wantPost(id);
        }
        return texture;
    }

    /**
     * A primeira leitura do {@code hasImageForPost}: {@code REMOTE_POST_IMAGE_BYTES.containsKey(post.id())}.
     * Foto pedida e ainda a caminho conta como "tem foto": o app mostra o "carregando" dele em vez da
     * foto falsa colorida.
     */
    @Redirect(method = "hasImageForPost(Lcom/mattupolis/phone/client/gui/PhoneInstagramStore$GramPost;)Z",
            at = @At(value = "INVOKE", target = "Ljava/util/Map;containsKey(Ljava/lang/Object;)Z", ordinal = 0),
            require = 0)
    private static boolean aurorion_essentials$photoComing(Map<?, ?> remoteBytes, Object postId) {
        return remoteBytes.containsKey(postId) || postId instanceof String id && PhoneGramMedia.isPhotoComing(id);
    }

    @Inject(method = "receiveRemoteProfile(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[B)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void aurorion_essentials$profile(String playerName, String displayName, String bio, byte[] avatarBytes,
                                                    CallbackInfo ci) {
        String key = normalizeProfileKey(playerName);
        if (key.isEmpty()) return;
        boolean self = key.equals(normalizeProfileKey(getPlayerName()));
        if (self) {
            // O que esta no servidor e o que o jogador salvou por ultimo, de qualquer PC.
            if (displayName != null) profileDisplayName = displayName.trim();
            if (bio != null) profileBio = bio.trim();
        }
        String lazy = PhoneGramImageRef.hashOfMark(avatarBytes);
        if (lazy != null) {
            PhoneGramMedia.lazyAvatar(key, playerName, lazy, self);
            ci.cancel();
            receiveRemoteProfile(playerName, displayName, bio, aurorion_essentials$NO_BYTES);
            return;
        }
        if (avatarBytes == null || avatarBytes.length == 0) return;
        // Foto nova ao vivo: um pedido ainda pendente traria a antiga por cima dela.
        PhoneGramMedia.avatarArrived(key);
        if (self) PhoneGramMedia.cacheOwnAvatar(avatarBytes);
    }

    @Inject(method = "getProfileAvatarTextureForUser(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;",
            at = @At("HEAD"), require = 0)
    private static void aurorion_essentials$fetchAvatar(String username, CallbackInfoReturnable<ResourceLocation> cir) {
        PhoneGramMedia.wantAvatar(normalizeProfileKey(username));
    }

    /**
     * O perfil proprio desenha so este caminho. Sem foto escolhida nesta sessao, vale a copia local do
     * avatar que o servidor mandou — e a tela de editar abre com ela, entao salvar a bio nao a apaga.
     */
    @Inject(method = "getProfileAvatarPhoto()Ljava/nio/file/Path;", at = @At("HEAD"), require = 0)
    private static void aurorion_essentials$ownAvatar(CallbackInfoReturnable<Path> cir) {
        if (profileAvatarPhoto != null) return;
        Path cached = PhoneGramMedia.ownAvatar();
        if (cached != null) profileAvatarPhoto = cached;
    }
}
