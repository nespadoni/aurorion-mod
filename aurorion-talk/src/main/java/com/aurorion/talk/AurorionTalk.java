package com.aurorion.talk;

import com.aurorion.talk.config.TalkConfig;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(AurorionTalk.MOD_ID)
public class AurorionTalk {
    public static final String MOD_ID = "aurorion_talk";
    public static final String MOD_NAME = "Aurorion Talk";

    public static final Logger LOGGER = LogUtils.getLogger();

    public AurorionTalk(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, TalkConfig.SPEC);
    }
}
