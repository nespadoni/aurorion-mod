package com.aurorion.profissoes;

import com.aurorion.core.config.AurorionConfigs;
import com.aurorion.profissoes.config.ProfessionsConfig;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.bus.api.IEventBus;
import com.aurorion.profissoes.loot.NoMendingLoot;
import com.aurorion.profissoes.npc.NpcEntities;
import com.aurorion.profissoes.npc.ProfessionNpcEntity;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import org.slf4j.Logger;

@Mod(AurorionProfissoes.MOD_ID)
public final class AurorionProfissoes {
    public static final String MOD_ID = "aurorion_profissoes";
    public static final Logger LOGGER = LogUtils.getLogger();
    public AurorionProfissoes(IEventBus bus, ModContainer container) {
        AurorionConfigs.register(container, ModConfig.Type.SERVER, ProfessionsConfig.SPEC);
        NoMendingLoot.REGISTRY.register(bus);
        // NPCs de oficio: cobrem o atendimento quando nao ha um profissional jogador por perto.
        NpcEntities.ENTITIES.register(bus);
        bus.addListener((EntityAttributeCreationEvent event) ->
                event.put(NpcEntities.NPC.get(), ProfessionNpcEntity.attributes().build()));
    }
}
