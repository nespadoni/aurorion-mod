package com.aurorion.magia.registry;

import com.aurorion.magia.AurorionMagia;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * O que um pergaminho de passiva carrega: o id da passiva que ele ensina.
 *
 * <p>Um item so para todas as passivas, e nao um item por passiva. Item registrado e conteudo que
 * nunca mais sai do modpack (SDD §6.1) — com um componente, uma passiva nova custa uma linha no
 * {@code enum}; com um item por passiva, custaria uma entrada permanente no registro a cada ideia
 * nova.
 *
 * <p>E exatamente o que o proprio Iron's faz com o pergaminho de magia dele, que tambem e um item so
 * com a magia guardada num componente.
 */
public final class MagiaComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, AurorionMagia.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> PASSIVE =
            COMPONENTS.register("passiva", id -> DataComponentType.<ResourceLocation>builder()
                    .persistent(ResourceLocation.CODEC)
                    .networkSynchronized(ResourceLocation.STREAM_CODEC)
                    .build());

    private MagiaComponents() {
    }
}
