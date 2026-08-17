package com.aurorion.utils;

import com.aurorion.utils.command.ModCommandArguments;
import com.aurorion.utils.config.AbductionConfig;
import com.aurorion.utils.entity.ModEntities;
import com.aurorion.utils.sound.ModSounds;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Ponto de entrada. Comando e o ticker de abducao se registram sozinhos via
 * {@code @EventBusSubscriber(modid = MOD_ID)} — so os {@code DeferredRegister} (entidade, som,
 * tipo de argumento) e a config precisam de registro manual, aqui.
 */
@Mod(AurorionUtils.MOD_ID)
public class AurorionUtils {
    public static final String MOD_ID = "aurorion_utils";
    public static final String MOD_NAME = "Aurorion Utils";

    public static final Logger LOGGER = LogUtils.getLogger();

    public AurorionUtils(IEventBus modEventBus, ModContainer container) {
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModSounds.SOUND_EVENTS.register(modEventBus);
        ModCommandArguments.COMMAND_ARGUMENT_TYPES.register(modEventBus);
        container.registerConfig(ModConfig.Type.SERVER, AbductionConfig.SPEC);
    }
}
