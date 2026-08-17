package com.aurorion.portais.line;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * Um ponto fixo no mundo: a plataforma de embarque (fora) ou a de desembarque (dentro da dimensao).
 *
 * <p>A estacao de desembarque tem uma segunda funcao, alem de aparecer no aviso: e para onde o
 * jogador que morre dentro da dimensao trancada volta. Ter um ponto declarado no datapack e o que
 * evita a alternativa ruim — procurar "um lugar seguro" no Nether na hora do respawn.
 */
public record Station(ResourceKey<Level> dimension, BlockPos pos, Optional<Component> name) {
    public static final Codec<Station> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Station::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(Station::pos),
            ComponentSerialization.CODEC.optionalFieldOf("name").forGetter(Station::name)
    ).apply(instance, Station::new));

    /** Nome declarado, ou as coordenadas cruas quando o datapack nao deu nome a estacao. */
    public Component label() {
        return name.orElseGet(() -> Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ()));
    }
}
