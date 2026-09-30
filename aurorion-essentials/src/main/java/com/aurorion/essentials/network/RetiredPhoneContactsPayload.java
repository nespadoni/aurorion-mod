package com.aurorion.essentials.network;

import com.aurorion.essentials.AurorionEssentials;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Personagens aposentados por reset: id do personagem -> nick da conta. O cliente apaga do telefone os
 * contatos e favoritos de PIX desses nicks, uma vez por id. Ver
 * {@link com.aurorion.essentials.compat.PhoneRetirements}.
 */
public record RetiredPhoneContactsPayload(Map<UUID, String> retired) implements CustomPacketPayload {
    public static final Type<RetiredPhoneContactsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AurorionEssentials.MOD_ID, "retired_phone_contacts"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RetiredPhoneContactsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(LinkedHashMap::new, UUIDUtil.STREAM_CODEC, ByteBufCodecs.STRING_UTF8),
            RetiredPhoneContactsPayload::retired,
            RetiredPhoneContactsPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
