package com.aurorion.portais;

import com.aurorion.core.config.AurorionConfigs;
import com.aurorion.portais.config.TransitConfig;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Controle de acesso as dimensoes por horario.
 *
 * <p>A regra de base e <b>negar</b>: toda dimensao fora de {@code freeDimensions} fica trancada, nos
 * dois sentidos. Isso e o que torna o mod flexivel para dimensao de mod sem precisar listar mod
 * nenhum — uma dimensao nova que apareca no modpack ja nasce trancada, e liberar e escrever uma
 * linha de datapack.
 *
 * <p>O acesso abre em janelas agendadas ("linhas de trem"), definidas em
 * {@code data/<namespace>/aurorion/linhas/*.json}. Horario e conteudo, nao comportamento — mudar o
 * dia da partida e editar JSON e dar {@code /reload} (SDD §7, diretriz 4).
 *
 * <p>Nao ha {@code DeferredRegister} aqui: este mod nao registra item, bloco nem entidade. Ele so
 * decide quem pode atravessar o que, e quando.
 */
@Mod(AurorionPortais.MOD_ID)
public class AurorionPortais {
    public static final String MOD_ID = "aurorion_portais";
    public static final String MOD_NAME = "Aurorion Portais";

    public static final Logger LOGGER = LogUtils.getLogger();

    public AurorionPortais(IEventBus modEventBus, ModContainer container) {
        AurorionConfigs.register(container, ModConfig.Type.SERVER, TransitConfig.SPEC);
    }
}
