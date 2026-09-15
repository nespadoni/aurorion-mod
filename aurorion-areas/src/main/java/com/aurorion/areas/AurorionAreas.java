package com.aurorion.areas;

import com.aurorion.areas.compat.IronSpellsCompat;
import com.aurorion.areas.config.AreasConfig;
import com.aurorion.core.config.AurorionConfigs;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

@Mod(AurorionAreas.MOD_ID)
public final class AurorionAreas {
    public static final String MOD_ID = "aurorion_areas";
    public static final Logger LOGGER = LogUtils.getLogger();
    public AurorionAreas(IEventBus bus, ModContainer container) {
        AurorionConfigs.register(container, ModConfig.Type.SERVER, AreasConfig.SPEC);
        bus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(IronSpellsCompat::register));
    }
}
