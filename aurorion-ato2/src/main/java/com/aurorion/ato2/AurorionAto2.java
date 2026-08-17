package com.aurorion.ato2;

import com.aurorion.ato2.config.HouseConfig;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Mecanicas exclusivas do Ato 2. Tudo que existe aqui tem prazo de validade: quando o ato acabar,
 * este jar sai do modpack e nenhum item, bloco ou receita vai junto — o conteudo mora no
 * {@code aurorion_aeonita}, que fica.
 *
 * <p>Nao ha nenhum {@code DeferredRegister} neste mod de proposito: registrar conteudo aqui
 * significaria que desligar o Ato 2 apagaria coisa do inventario dos jogadores.
 *
 * <p>Todo o resto se registra sozinho via {@code @EventBusSubscriber(modid = MOD_ID)} — so a config
 * precisa de registro manual.
 */
@Mod(AurorionAto2.MOD_ID)
public class AurorionAto2 {
    public static final String MOD_ID = "aurorion_ato2";
    public static final String MOD_NAME = "Aurorion Ato 2";

    public static final Logger LOGGER = LogUtils.getLogger();

    public AurorionAto2(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, HouseConfig.SPEC);
    }
}
