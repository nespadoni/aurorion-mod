package com.aurorion.personagem.mixin;

import com.aurorion.personagem.alt.AltLogin;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Troca o perfil de quem entra como o segundo personagem (ver {@link AltLogin}).
 *
 * <p>O ponto e o inicio de {@code verifyLoginAndFinishConnectionSetup}, e nao
 * {@code startClientVerification}: este roda na thread de autenticacao da Mojang, aquele roda no
 * {@code tick} da thread do servidor, onde da para ler o {@code SavedData}. E e antes de tudo que o
 * vanilla confere — ban, whitelist, jogador duplicado — e antes da fase de configuracao, entao todo
 * mod ja recebe o perfil do alt.
 *
 * <p>O campo {@code authenticatedProfile} e trocado junto porque o vanilla volta a le-lo depois (na
 * espera de desconectar um duplicado e ao montar a fase de configuracao).
 */
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class LoginProfileMixin {
    @Shadow
    @Final
    MinecraftServer server;

    @Shadow
    @Final
    Connection connection;

    @Shadow
    private GameProfile authenticatedProfile;

    @ModifyVariable(method = "verifyLoginAndFinishConnectionSetup", at = @At("HEAD"), argsOnly = true)
    private GameProfile aurorion$enterAsAlt(GameProfile profile) {
        GameProfile resolved = AltLogin.resolve(server, profile, connection);
        if (resolved != profile) authenticatedProfile = resolved;
        return resolved;
    }
}
