package com.aurorion.servicos;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * O app "Servicos" do celular: quem trabalha anuncia, liga o "Trabalhando" e recebe pedido; quem
 * precisa procura um profissional ou faz um pedido aberto para a area inteira; quem emprega publica
 * vaga. Tudo termina numa conversa no proprio celular — o app aproxima as pessoas, a negociacao e RP.
 *
 * <p>O servidor guarda anuncios, pedidos e vagas ({@code ServicosData}) e decide tudo; o cliente so
 * desenha o que recebe. Sem o telefone instalado o mod carrega e nao mostra nada.
 */
@Mod(AurorionServicos.MOD_ID)
public final class AurorionServicos {
    public static final String MOD_ID = "aurorion_servicos";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AurorionServicos() {
    }
}
