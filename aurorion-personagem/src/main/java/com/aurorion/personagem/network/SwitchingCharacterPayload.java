package com.aurorion.personagem.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * "Vou te desconectar para trocar de personagem — volte sozinho."
 *
 * <p>Chega logo antes da desconexao, pelo mesmo canal, entao o cliente ja sabe o motivo quando a tela
 * de desconectado aparecer e reconecta no mesmo servidor ({@code ClientSwitch}).
 *
 * @param characterName nome de quem entra, so para a tela de espera
 */
public record SwitchingCharacterPayload(String characterName) implements CustomPacketPayload {
    public static final Type<SwitchingCharacterPayload> TYPE =
            new Type<>(ResourceLocation.parse("aurorion_personagem:switching_character"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SwitchingCharacterPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(64), SwitchingCharacterPayload::characterName,
                    SwitchingCharacterPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
