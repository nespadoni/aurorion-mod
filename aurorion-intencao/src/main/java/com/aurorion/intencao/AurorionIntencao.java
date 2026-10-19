package com.aurorion.intencao;

import com.aurorion.core.config.AurorionConfigs;
import com.aurorion.intencao.config.IntencaoConfig;
import com.aurorion.intencao.registry.IntencaoEntities;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * A batalha de intencao, do lado do jogo. A cena em si acontece na mesa do Discord; aqui mora o
 * ritual em volta dela: o Chamado, o Plano da Intencao e o Veredito.
 *
 * <ul>
 *   <li>{@code server}: sessoes, ancoras de retorno, arena, comando e protecao do plano.</li>
 *   <li>{@code entity}: o Eco (o corpo em transe que fica no mundo) e os fragmentos de memoria.</li>
 *   <li>{@code network}: "o servidor manda intencao, o cliente desenha" — dois pacotes raros.</li>
 *   <li>{@code client}: telas de entrada e veredito, neblina, som e particulas. Nada ali roda no
 *       servidor.</li>
 * </ul>
 *
 * <p>Sem regra de jogo: ninguem ganha atributo. A consequencia mecanica fica com o mestre na mesa.
 */
@Mod(AurorionIntencao.MOD_ID)
public class AurorionIntencao {
    public static final String MOD_ID = "aurorion_intencao";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AurorionIntencao(IEventBus modEventBus, ModContainer container) {
        IntencaoEntities.ENTITIES.register(modEventBus);
        AurorionConfigs.register(container, ModConfig.Type.SERVER, IntencaoConfig.SPEC);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
