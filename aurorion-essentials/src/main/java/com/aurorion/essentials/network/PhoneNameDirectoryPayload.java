package com.aurorion.essentials.network;

import com.aurorion.essentials.AurorionEssentials;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Nick para nome de personagem, de quem esta offline tambem — o que o telefone precisa para mostrar
 * o contato de alguem que nao esta no servidor agora com o nome do personagem.
 *
 * <p>O {@link SyncFakeNamesPayload} so leva quem esta online, de proposito (e o que a tab list e a
 * plaqueta usam). A agenda do celular, o extrato do PIX e a caixa de e-mail mostram gente que saiu ha
 * dias; esses so o servidor sabe quem sao.
 *
 * @param replace {@code true} no login (a lista inteira); {@code false} para uma mudanca so
 * @param entries nick para nome; nome vazio tira o nick
 */
public record PhoneNameDirectoryPayload(boolean replace, Map<String, String> entries) implements CustomPacketPayload {
    /** Teto do diretorio: nome de personagem para mais contas do que isto nao cabe numa agenda. */
    public static final int MAX_ENTRIES = 4096;

    public static final Type<PhoneNameDirectoryPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AurorionEssentials.MOD_ID, "phone_name_directory"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PhoneNameDirectoryPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, PhoneNameDirectoryPayload::replace,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.stringUtf8(16), ByteBufCodecs.stringUtf8(256), MAX_ENTRIES),
            PhoneNameDirectoryPayload::entries,
            PhoneNameDirectoryPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
