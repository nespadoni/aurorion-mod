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
    /** Nick da Mojang: 16 no maximo. Ver {@link #encodable}. */
    public static final int MAX_NICK = 16;
    public static final int MAX_NAME = 256;

    public static final Type<PhoneNameDirectoryPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AurorionEssentials.MOD_ID, "phone_name_directory"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PhoneNameDirectoryPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, PhoneNameDirectoryPayload::replace,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.stringUtf8(MAX_NICK), ByteBufCodecs.stringUtf8(MAX_NAME), MAX_ENTRIES),
            PhoneNameDirectoryPayload::entries,
            PhoneNameDirectoryPayload::new
    );

    /**
     * A entrada cabe no codec? Um nick maior que {@link #MAX_NICK} (perfil tecnico, conta vinda de
     * outro proxy) fazia o encoder lancar excecao no meio do envio — e o diretorio inteiro do login
     * deixava de chegar por causa de uma conta. Quem monta o pacote pula a entrada invalida.
     */
    public static boolean encodable(String nick, String name) {
        return nick != null && !nick.isEmpty() && nick.length() <= MAX_NICK
                && name != null && name.length() <= MAX_NAME;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
