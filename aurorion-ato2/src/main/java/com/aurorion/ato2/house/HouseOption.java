package com.aurorion.ato2.house;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Uma casa como ela aparece na tela de escolha: a definicao mais quantos jogadores ja estao nela.
 * A contagem nao mora em {@link House} porque nao e conteudo — muda o tempo todo e so faz sentido
 * no instante em que a tela foi aberta.
 */
public record HouseOption(House house, int members) {
    public static final StreamCodec<RegistryFriendlyByteBuf, HouseOption> STREAM_CODEC = StreamCodec.composite(
            House.STREAM_CODEC, HouseOption::house,
            ByteBufCodecs.VAR_INT, HouseOption::members,
            HouseOption::new);

    /** Lotada e so quem declarou limite e ja bateu nele — casa sem limite nunca lota. */
    public boolean isFull() {
        return house.hasCapacity() && members >= house.capacity();
    }
}
