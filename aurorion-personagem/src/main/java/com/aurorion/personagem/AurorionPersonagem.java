package com.aurorion.personagem;

import com.aurorion.core.character.CharacterGate;
import com.aurorion.core.config.AurorionConfigs;
import com.aurorion.personagem.config.CreationConfig;
import com.aurorion.personagem.creation.CreationManager;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/**
 * Quem e a pessoa, e quem e o personagem.
 *
 * <p>O Minecraft so conhece uma identidade: o UUID da conta. O Aurorion precisa de duas, porque a
 * morte definitiva do {@code aurorion_limbo} encerra <b>uma historia</b>, nao um jogador — a conta
 * continua valida, sem banimento, e a mesma pessoa volta com outro personagem.
 *
 * <h2>O que este mod faz</h2>
 *
 * <ul>
 *   <li><b>Pergunta o nome.</b> Quem entra sem personagem nomeado ve uma tela com dois campos, nome
 *       e sobrenome. Enquanto nao responder, nao joga.</li>
 *   <li><b>Publica o nome.</b> O nome completo vira o nome exibido no jogo (o mesmo mecanismo do
 *       {@code /fakename}), entao quem morreu como "Alda Verrine" volta como outra pessoa aos olhos
 *       de todo mundo.</li>
 *   <li><b>Apaga o que era do personagem anterior.</b> Inventario, XP, avancos, estatisticas,
 *       vidas, casa, passes — tudo, antes de a identidade nova existir.</li>
 * </ul>
 *
 * <h2>Por que um mod separado, e nao dentro do core</h2>
 *
 * <p>O {@code aurorion-core} e biblioteca: ele guarda a identidade e o diario da troca, mas nao
 * adiciona tela, comando nem regra ao jogo (SDD §4). Quem instala so o core continua com um servidor
 * onde ninguem e barrado no login. A cobranca do nome e uma <b>decisao de servidor</b>, e liga-la ou
 * desliga-la e instalar ou nao instalar este mod.
 *
 * <h2>Por que nao depende dos outros mods do ecossistema</h2>
 *
 * <p>Zerar vidas, casa, passes e estilo de balao seria facil por import direto — e transformaria
 * este mod no unico lugar do repositorio que conhece todos os outros. Em vez disso ele dispara
 * {@link com.aurorion.core.character.CharacterResetEvent} e <b>cada mod apaga o que e seu</b>, no seu
 * proprio codigo, ao lado dos dados que ele mesmo escreveu. Um mod novo que guarde algo por jogador
 * entra no reset acrescentando um listener — sem tocar aqui.
 */
@Mod(AurorionPersonagem.MOD_ID)
public class AurorionPersonagem {
    public static final String MOD_ID = "aurorion_personagem";
    public static final String MOD_NAME = "Aurorion Personagem";

    public static final Logger LOGGER = LogUtils.getLogger();

    public AurorionPersonagem(IEventBus modEventBus, ModContainer container) {
        AurorionConfigs.register(container, ModConfig.Type.SERVER, CreationConfig.SPEC);

        // A partir daqui o core passa a barrar login sem personagem, e o aurorion_limbo para de
        // desconectar quem terminou o epilogo: existe para onde mandar essa pessoa.
        CharacterGate.enableCreation(CreationManager::needsCreation);
    }
}
