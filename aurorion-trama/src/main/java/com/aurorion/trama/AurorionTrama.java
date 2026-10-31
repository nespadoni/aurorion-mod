package com.aurorion.trama;

import com.aurorion.core.character.CharacterAbilities;
import com.aurorion.core.character.SkillTreeGate;
import com.aurorion.core.config.AurorionConfigs;
import com.aurorion.trama.config.TramaConfig;
import com.aurorion.trama.server.TramaAbilities;
import com.aurorion.trama.server.TramaRuntime;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.puffish.skillsmod.api.SkillsAPI;
import org.slf4j.Logger;

@Mod(AurorionTrama.MOD_ID)
public final class AurorionTrama {
    public static final String MOD_ID = "aurorion_trama";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AurorionTrama(IEventBus bus, ModContainer container) {
        AurorionConfigs.register(container, ModConfig.Type.SERVER, TramaConfig.SPEC);
        // Formacao e nos comprados na aba Habilidades da ficha, se ela estiver no pack.
        CharacterAbilities.register(MOD_ID, TramaAbilities::collect);
        // Os nos para quem pergunta sem importar a Trama (a magia: Despertar Arcano e especialidade).
        SkillTreeGate.provide(TramaRuntime::has);
        SkillsAPI.registerSkillUnlockEvent((player, category, skill) -> {
            if (category.equals(TramaRuntime.CATEGORY)) { TramaRuntime.invalidate(player.getUUID()); TramaRuntime.changed(player); }
        });
        SkillsAPI.registerSkillLockEvent((player, category, skill) -> {
            if (category.equals(TramaRuntime.CATEGORY)) { TramaRuntime.invalidate(player.getUUID()); TramaRuntime.changed(player); }
        });
    }
}
