package com.aurorion.diario;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * {@code /diario}: o diário do personagem dentro do jogo, o mesmo do site.
 *
 * <p>O servidor conversa com o site pela integração ({@code aurorion-integracao}) e guarda os
 * rascunhos quando o site está fora; o cliente desenha a tela com o Tessera UI (HTML/CSS) e usa o
 * campo de texto nativo do Minecraft para escrever. Nada roda por tick.
 */
@Mod(AurorionDiario.MOD_ID)
public final class AurorionDiario {
    public static final String MOD_ID = "aurorion_diario";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AurorionDiario(IEventBus modEventBus, ModContainer container) {
        // Comandos, rede e servidor se registram por @EventBusSubscriber.
    }
}
