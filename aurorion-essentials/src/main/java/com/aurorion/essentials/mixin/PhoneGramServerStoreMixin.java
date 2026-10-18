package com.aurorion.essentials.mixin;

import com.aurorion.essentials.compat.PhoneGramImageStore;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.OutputStream;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;

/**
 * Posts e perfis do Gram no servidor: fotos em arquivo, gravacao fora da thread do jogo e fotos sob
 * demanda no login. O porque inteiro esta em {@link PhoneGramImageStore}.
 *
 * <p>So as fotos mudam de lugar: legenda, autor, curtidas, comentarios e seguidores continuam no
 * {@code server_social.properties} do telefone, no formato dele. Tirar o essentials depois deixa as
 * fotos sem carregar (a referencia nao e base64), mas nada mais se perde.
 *
 * <p>Fica no {@code aurorion_essentials.phone.mixins.json}, que nao e obrigatorio, e todo injetor tem
 * {@code require = 0}: se o telefone mudar, o servidor sobe com o comportamento original. O
 * {@code PhoneGramPersistenceContractTest} confere os alvos contra o jar instalado.
 */
@Pseudo
@Mixin(targets = "com.mattupolis.phone.server.social.PhoneSocialServerStore", remap = false)
public abstract class PhoneGramServerStoreMixin {
    @Shadow @Final private static LinkedHashMap<String, Object> GRAM_PROFILES;

    @Shadow
    public static void rememberGramProfile(String playerName, String displayName, String bio, byte[] avatarBytes) {
        throw new AssertionError();
    }

    @Shadow
    private static void ensurePersistentLoaded() {
        throw new AssertionError();
    }

    @Redirect(method = {"saveGramPosts(Ljava/util/Properties;)V", "saveGramProfiles(Ljava/util/Properties;)V"},
            at = @At(value = "INVOKE", target = "Ljava/util/Base64$Encoder;encodeToString([B)Ljava/lang/String;"),
            require = 0)
    private static String aurorion_essentials$imageToFile(Base64.Encoder encoder, byte[] bytes) {
        return PhoneGramImageStore.persist(encoder, bytes);
    }

    @Redirect(method = {"loadGramPosts(Ljava/util/Properties;)V", "loadGramProfiles(Ljava/util/Properties;)V"},
            at = @At(value = "INVOKE",
                    target = "Lcom/mattupolis/phone/server/social/PhoneSocialServerStore;bytesFromB64(Ljava/lang/String;)[B"),
            require = 0)
    private static byte[] aurorion_essentials$imageFromFile(String value) {
        return PhoneGramImageStore.restore(value);
    }

    @Redirect(method = "savePersistentStore()V",
            at = @At(value = "INVOKE",
                    target = "Ljava/nio/file/Files;newOutputStream(Ljava/nio/file/Path;[Ljava/nio/file/OpenOption;)Ljava/io/OutputStream;"),
            require = 0)
    private static OutputStream aurorion_essentials$writeOffThread(Path file, OpenOption[] options) {
        return PhoneGramImageStore.deferredStoreOutput(file);
    }

    @Inject(method = "syncToPlayer(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("HEAD"), require = 0)
    private static void aurorion_essentials$beginSync(ServerPlayer player, CallbackInfo ci) {
        PhoneGramImageStore.beginSync(player);
    }

    @Inject(method = "syncToPlayer(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At("RETURN"), require = 0)
    private static void aurorion_essentials$endSync(ServerPlayer player, CallbackInfo ci) {
        PhoneGramImageStore.endSync();
    }

    @ModifyArg(method = "syncToPlayer(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(value = "INVOKE", target = "Lcom/mattupolis/phone/network/packet/ClientboundGramPhotoPostPacket;"
                    + "<init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[BJJ)V"),
            index = 3, require = 0)
    private static byte[] aurorion_essentials$postPhotoOnDemand(byte[] bytes) {
        return PhoneGramImageStore.forSync(bytes);
    }

    @ModifyArg(method = "syncToPlayer(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At(value = "INVOKE", target = "Lcom/mattupolis/phone/network/packet/ClientboundGramProfileSyncPacket;"
                    + "<init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[B)V"),
            index = 3, require = 0)
    private static byte[] aurorion_essentials$avatarOnDemand(byte[] bytes) {
        return PhoneGramImageStore.forSync(bytes);
    }

    /**
     * Salvar o perfil sem foto nao apaga a foto que ja estava la. O app nao tem "remover foto"; perfil
     * sem avatar so chega quando o cliente nao sabia qual era o atual (o caminho da foto e so memoria
     * do cliente) — e trocar so a bio apagava o avatar para todo mundo. O Twitter do proprio telefone
     * ja juntava assim ({@code rememberTwitterProfile}); o Gram nao.
     *
     * <p>Roda dentro do {@code synchronized} do metodo; a chamada de novo reentra no mesmo monitor.
     */
    @Inject(method = "rememberGramProfile(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[B)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private static void aurorion_essentials$keepAvatar(String playerName, String displayName, String bio, byte[] avatarBytes,
                                                       CallbackInfo ci) {
        if (playerName == null || playerName.isBlank() || avatarBytes != null && avatarBytes.length > 0) return;
        // O original carrega o disco antes de mexer no mapa; aqui tambem, senao o primeiro perfil salvo
        // depois de reiniciar o servidor nao acharia o avatar anterior.
        ensurePersistentLoaded();
        // Mesma chave do telefone, inclusive o toLowerCase() sem Locale.
        byte[] previous = PhoneGramImageStore.avatarOf(GRAM_PROFILES.get(playerName.toLowerCase()));
        if (previous == null || previous.length == 0) return;
        ci.cancel();
        rememberGramProfile(playerName, displayName, bio, previous);
    }
}
