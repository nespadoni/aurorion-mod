package com.aurorion.essentials.network;

import com.aurorion.essentials.AurorionEssentials;
import com.aurorion.essentials.compat.PhoneGramImageRef;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Fotos do Gram sob demanda: o cliente pede uma foto pelo hash quando vai desenha-la, e o servidor
 * responde com os bytes (vazio = o servidor nao tem essa foto). Ver {@link com.aurorion.essentials.compat.PhoneGramImageStore}.
 */
public final class PhoneGramImagePayloads {
    private PhoneGramImagePayloads() {
    }

    /** Cliente -> servidor: "manda a foto deste hash". */
    public record Request(String hash) implements CustomPacketPayload {
        public static final Type<Request> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AurorionEssentials.MOD_ID, "gram_image_request"));

        public static final StreamCodec<ByteBuf, Request> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(PhoneGramImageRef.HASH_LENGTH), Request::hash,
                Request::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Servidor -> cliente: a foto pedida. */
    public record Image(String hash, byte[] bytes) implements CustomPacketPayload {
        public static final Type<Image> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(AurorionEssentials.MOD_ID, "gram_image"));

        public static final StreamCodec<ByteBuf, Image> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(PhoneGramImageRef.HASH_LENGTH), Image::hash,
                ByteBufCodecs.byteArray(PhoneGramImageRef.MAX_IMAGE_BYTES), Image::bytes,
                Image::new
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
