package com.aurorion.ethereal.network;

import com.aurorion.ethereal.AurorionEthereal;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Servidor -> cliente: a cerimonia acabou (respondida ate o fim, ou cancelada pela staff).
 *
 * <p>Repare no que ele <b>nao</b> carrega: a casa. Terminar de responder nao revela nada — quem
 * decide e o Conselho, e a revelacao vem depois, no {@link RevealHousePayload}.
 */
public record CeremonyClosedPayload(Component message) implements CustomPacketPayload {
    public static final Type<CeremonyClosedPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AurorionEthereal.MOD_ID, "ceremony_closed"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CeremonyClosedPayload> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.STREAM_CODEC, CeremonyClosedPayload::message,
            CeremonyClosedPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
