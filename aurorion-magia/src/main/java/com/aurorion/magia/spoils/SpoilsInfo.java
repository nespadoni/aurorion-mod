package com.aurorion.magia.spoils;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A etiqueta da mochila: de quem eram os pertences e, quando nao couberam numa so, qual das partes ela e.
 *
 * @param owner o nome com que a pessoa aparecia no jogo (o do personagem, nao o nick)
 * @param part  de 1 a {@code parts}
 */
public record SpoilsInfo(String owner, int part, int parts) {
    public static final Codec<SpoilsInfo> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("dono").forGetter(SpoilsInfo::owner),
            Codec.INT.fieldOf("parte").forGetter(SpoilsInfo::part),
            Codec.INT.fieldOf("partes").forGetter(SpoilsInfo::parts)
    ).apply(instance, SpoilsInfo::new));

    public static final StreamCodec<ByteBuf, SpoilsInfo> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SpoilsInfo::owner,
            ByteBufCodecs.VAR_INT, SpoilsInfo::part,
            ByteBufCodecs.VAR_INT, SpoilsInfo::parts,
            SpoilsInfo::new);
}
