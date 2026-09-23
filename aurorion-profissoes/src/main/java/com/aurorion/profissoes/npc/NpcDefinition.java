package com.aurorion.profissoes.npc;

import com.aurorion.profissoes.data.Profession;
import java.util.List;

/**
 * Um NPC como a staff escreveu em {@code config/aurorion/npcs.json}, ja validado.
 *
 * <p>Os ids de item continuam texto aqui: a existencia do item so pode ser conferida com os
 * registros do jogo carregados, e isso e trabalho do {@link NpcCatalog}. Assim o parser inteiro
 * roda em teste de unidade, sem servidor.
 *
 * @param fallbackRadius raio em que um jogador com a mesma profissao "assume" o atendimento:
 *                       {@code 0} desliga a regra, {@code -1} considera o servidor inteiro.
 */
public record NpcDefinition(String id, String displayName, String title, Profession profession,
                            String skin, boolean slimSkin, String greeting, List<String> dialogue,
                            String admDialogue, int fallbackRadius, boolean fallbackBlocksTrades,
                            List<Service> services, List<Trade> trades) {
    public NpcDefinition {
        dialogue = List.copyOf(dialogue); services = List.copyOf(services); trades = List.copyOf(trades);
    }

    public enum ActionType { COMMAND, HEAL, REPAIR, FINISH_FOOD }

    /** Quando o estoque volta ao maximo. */
    public enum Restock { RESTART, DAILY, NEVER }

    /** Item e/ou dinheiro da Economia (em fragmentos). Os dois zerados: gratuito. */
    public record Cost(String itemId, int amount, long money) {
        public static final Cost FREE = new Cost("", 0, 0);
        public boolean hasItem() { return !itemId.isEmpty() && amount > 0; }
    }

    /**
     * Um servico. Comandos rodam em qualquer tipo: no {@code COMMAND} sao a acao (e o pagamento e
     * devolvido se nenhum der certo); nos nativos, sao extras depois da acao (som, particula, log).
     */
    public record Service(String name, String description, ActionType action, List<String> commands,
                          Cost cost, int cooldownSeconds) {
        public Service { commands = List.copyOf(commands); }
    }

    /**
     * Uma oferta da loja.
     *
     * @param key        identifica o estoque salvo. Vem do {@code id} da oferta; sem ele, da posicao
     *                   e do item — reordenar a lista zera o estoque dessas ofertas.
     * @param components componentes no formato do {@code /give}, ex. {@code [enchantments={...}]}
     * @param nbtData    SNBT gravado em {@code minecraft:custom_data}
     * @param maxStock   {@code -1} para infinito
     */
    public record Trade(String key, String itemId, int amount, String components, String nbtData,
                        Cost price, int maxStock, Restock restock) {
        public boolean limited() { return maxStock >= 0; }
    }
}
