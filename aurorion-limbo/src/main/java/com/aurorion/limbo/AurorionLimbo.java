package com.aurorion.limbo;

import com.aurorion.core.config.AurorionConfigs;
import com.aurorion.limbo.config.LimboConfig;
import com.aurorion.limbo.registry.LimboEntities;
import com.aurorion.limbo.registry.LimboItems;
import com.aurorion.limbo.registry.LimboSounds;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * O Limbo: a dimensao de exilio, o prazo que corre dentro dela e o registro do que aconteceu.
 *
 * <p>Quem decide que alguem esta exilado continua sendo o {@code aurorion_vidas} — este mod nao
 * conta vida nem cobra morte. Ele cuida do que o exilio nao tinha: <b>um relogio</b> e <b>uma
 * memoria</b>.
 *
 * <h2>As tres saidas</h2>
 *
 * <ol>
 *   <li><b>Resgate.</b> Alguem devolve vida ({@code /vidas dar}, ou o ritual quando existir). O
 *       registro fecha como resgatado e a pessoa volta inteira.</li>
 *   <li><b>A Porta do Esquecido.</b> Nas ultimas horas do prazo, se <em>ninguem</em> tentou buscar,
 *       caminhar o bastante revela uma saida. Ela existe para que nenhum jogador fique refem da boa
 *       vontade alheia — e fica registrada, porque sair por ela significa que o servidor inteiro
 *       deixou a pessoa la.</li>
 *   <li><b>O prazo vencer.</b> O personagem morre definitivamente e assiste ao epilogo antes da desconexao.</li>
 * </ol>
 *
 * <h2>Por que a auditoria nao e um detalhe</h2>
 *
 * <p>A Porta do Esquecido so tem sentido de jogo se alguem souber que ela foi usada: o que faz dela
 * uma historia (e nao so uma saida barata) e a staff poder chegar depois e dizer "voce voltou
 * sozinho porque ninguem foi te buscar". Por isso todo evento do Limbo vai para um arquivo de
 * auditoria que vive fora do save-data, legivel de fora do jogo.
 *
 * <h2>Nenhuma dependencia de apresentacao</h2>
 *
 * <p>O mod nao importa classe do Immersive Messages, do ADM nem de nenhum outro mod do pack. O que
 * ele fala com o jogador passa pelo {@code LimboNarrator}, que escolhe a entrega uma vez no boot.
 */
@Mod(AurorionLimbo.MOD_ID)
public class AurorionLimbo {
    public static final String MOD_ID = "aurorion_limbo";
    public static final String MOD_NAME = "Aurorion Limbo";

    public static final Logger LOGGER = LogUtils.getLogger();

    public AurorionLimbo(IEventBus modEventBus, ModContainer container) {
        AurorionConfigs.register(container, ModConfig.Type.SERVER, LimboConfig.SPEC);
        AurorionConfigs.register(container, ModConfig.Type.SERVER,
                com.aurorion.limbo.config.FinaleConfig.SPEC, "finale");

        // Conteudo do resgate: um item e uma entidade temporaria. O Oraculo nao entra aqui de
        // proposito — ele e uma tag num mob que ja existe, e nao um registro novo.
        LimboItems.ITEMS.register(modEventBus);
        LimboEntities.ENTITIES.register(modEventBus);
        LimboSounds.SOUNDS.register(modEventBus);
    }
}
