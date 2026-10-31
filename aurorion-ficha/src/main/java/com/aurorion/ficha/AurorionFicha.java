package com.aurorion.ficha;

import com.aurorion.core.character.CharacterSheet;
import com.aurorion.core.config.AurorionConfigs;
import com.aurorion.ficha.config.FichaClientConfig;
import com.aurorion.ficha.config.FichaConfig;
import com.aurorion.ficha.server.SheetStatsStore;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * {@code /ficha}: a ficha de RPG do site (motor v0.3) dentro do jogo.
 *
 * <p>O servidor conversa com o site pela integração ({@code aurorion-integracao}): lê a ficha que o
 * perfil usa, rola testes pelo executor oficial (idempotente por {@code command_id}) e repassa os
 * pedidos do mestre. O cliente desenha as telas com o Tessera UI (HTML/CSS) e os cartões de
 * rolagem no HUD. Nada roda por tick no servidor.
 */
@Mod(AurorionFicha.MOD_ID)
public final class AurorionFicha {
    public static final String MOD_ID = "aurorion_ficha";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AurorionFicha(IEventBus modEventBus, ModContainer container) {
        // config/aurorion/ficha-server.toml. Comandos, rede e servidor se registram por @EventBusSubscriber.
        AurorionConfigs.register(container, ModConfig.Type.SERVER, FichaConfig.SPEC);
        // config/aurorion/ficha-client.toml: onde ficam os cartões no HUD e o que escondem numa live.
        // Só a spec (sem classe de cliente); no servidor dedicado o NeoForge nem carrega config CLIENT.
        AurorionConfigs.register(container, ModConfig.Type.CLIENT, FichaClientConfig.SPEC);
        // O retrato da ficha para os testes do jogo (magia, ofício), lido de memória por quem pergunta.
        CharacterSheet.provide(SheetStatsStore::of);
    }
}
