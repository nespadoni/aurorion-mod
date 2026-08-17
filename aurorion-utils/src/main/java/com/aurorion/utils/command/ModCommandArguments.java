package com.aurorion.utils.command;

import com.aurorion.utils.AurorionUtils;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Tipos de argumento proprios precisam existir no registro {@code COMMAND_ARGUMENT_TYPE} pra
 * sobreviverem a serializacao da arvore de comandos que o servidor manda pro cliente — sem isso o
 * {@code /abduzir} nao chegaria completo do outro lado e as sugestoes de cor nunca apareceriam.
 */
public final class ModCommandArguments {
    public static final DeferredRegister<ArgumentTypeInfo<?, ?>> COMMAND_ARGUMENT_TYPES =
            DeferredRegister.create(BuiltInRegistries.COMMAND_ARGUMENT_TYPE, AurorionUtils.MOD_ID);

    /**
     * {@code contextFree} porque {@link BeamColorArgument} nao depende de nada do mundo pra
     * parsear: a paleta e estatica e o resto e hexadecimal.
     */
    public static final DeferredHolder<ArgumentTypeInfo<?, ?>, SingletonArgumentInfo<BeamColorArgument>> BEAM_COLOR =
            COMMAND_ARGUMENT_TYPES.register("beam_color", () -> ArgumentTypeInfos.registerByClass(
                    BeamColorArgument.class, SingletonArgumentInfo.contextFree(BeamColorArgument::beamColor)));

    private ModCommandArguments() {
    }
}
