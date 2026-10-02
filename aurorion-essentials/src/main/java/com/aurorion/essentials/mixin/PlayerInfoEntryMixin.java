package com.aurorion.essentials.mixin;

import com.aurorion.essentials.tab.TabNames;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Toda entrada da tab passa por este construtor — a do vanilla (via {@code Entry(ServerPlayer)}, que ja
 * chega pintada pelo {@link TabListNameMixin}) e a que um mod de tab monte por conta propria, como o
 * Just Essentials, que reenvia a tab a cada {@code refreshTicks}. Sem este ponto, a cor da casa sumiria
 * na primeira atualizacao dele.
 *
 * <p>{@code LOAD}: o valor e trocado logo antes de o construtor ler o parametro para gravar o campo.
 * Nesse ponto {@code profileId} e {@code profile} ja foram gravados (conferido com {@code javap} no
 * NeoForge 21.1.248), entao os acessores do record ja respondem.
 *
 * <p>Tambem roda no cliente, ao ler o pacote; la o {@link TabNames} nao esta ativo e devolve o valor
 * intacto.
 */
@Mixin(ClientboundPlayerInfoUpdatePacket.Entry.class)
public abstract class PlayerInfoEntryMixin {

    @ModifyVariable(
            method = "<init>(Ljava/util/UUID;Lcom/mojang/authlib/GameProfile;ZILnet/minecraft/world/level/GameType;"
                    + "Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/RemoteChatSession$Data;)V",
            at = @At("LOAD"), argsOnly = true)
    private Component aurorion_essentials$tabName(Component displayName) {
        ClientboundPlayerInfoUpdatePacket.Entry self = (ClientboundPlayerInfoUpdatePacket.Entry) (Object) this;
        GameProfile profile = self.profile();
        return TabNames.decorate(self.profileId(), profile == null ? null : profile.getName(), displayName);
    }
}
