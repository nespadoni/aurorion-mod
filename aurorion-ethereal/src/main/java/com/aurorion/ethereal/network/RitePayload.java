package com.aurorion.ethereal.network;

import com.aurorion.ethereal.AurorionEthereal;
import com.aurorion.ethereal.house.House;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Servidor -> clientes por perto: comeca/termina um Rito de Vinculacao.
 *
 * <p>Dois pacotes por rito, e nao um por quadro: o cliente recebe quem, com que cara e a partir de
 * qual tick, e conta o resto sozinho. Treze segundos de animacao custam a rede o mesmo que dois
 * cliques.
 *
 * <p>Vai para <b>todo mundo que enxerga</b> a pessoa, nao so para ela. Um rito que so o proprio
 * jogador ve seria uma tela; o que faz dele uma cerimonia e a plateia.
 *
 * <p>Nome, lema, cor e icone viajam prontos porque o cliente nao tem os datapacks — para ele, casa
 * nao existe como conceito. Assim uma casa criada no datapack cinco minutos antes se comporta igual.
 *
 * @param entityId  de quem esta sendo vinculado; o cliente resolve a entidade no proprio nivel.
 * @param houseName vazio quando {@code active} e false — o pacote de fim so precisa dizer quem.
 */
public record RitePayload(int entityId, boolean active, Component houseName, Component motto,
                          int color, Optional<ResourceLocation> icon) implements CustomPacketPayload {
    public static final Type<RitePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AurorionEthereal.MOD_ID, "binding_rite"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RitePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.entityId);
                buf.writeBoolean(payload.active);
                ComponentSerialization.STREAM_CODEC.encode(buf, payload.houseName);
                ComponentSerialization.STREAM_CODEC.encode(buf, payload.motto);
                buf.writeInt(payload.color);
                ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC).encode(buf, payload.icon);
            },
            buf -> new RitePayload(buf.readVarInt(), buf.readBoolean(),
                    ComponentSerialization.STREAM_CODEC.decode(buf),
                    ComponentSerialization.STREAM_CODEC.decode(buf),
                    buf.readInt(),
                    ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC).decode(buf)));

    public static RitePayload start(int entityId, House house) {
        return new RitePayload(entityId, true, house.name(), house.motto(), house.color(), house.icon());
    }

    public static RitePayload end(int entityId) {
        return new RitePayload(entityId, false, CommonComponents.EMPTY, CommonComponents.EMPTY,
                House.DEFAULT_COLOR, Optional.empty());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
