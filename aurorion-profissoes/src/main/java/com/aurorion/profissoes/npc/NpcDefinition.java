package com.aurorion.profissoes.npc;

import com.aurorion.profissoes.data.Profession;
import java.util.List;

/**
 * Um NPC do catalogo (do mod ou do {@code npcs_extras.json} da staff), ja validado.
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

    public enum ActionType {
        COMMAND, HEAL, REPAIR, FINISH_FOOD, ENCHANT, POTION_STRENGTH, POTION_DURATION, NAME_TAG;

        /** Faixa de gravidade (cura) ou de dano (reparo): o preco acompanha o tamanho do servico. */
        public boolean tiered() { return this == HEAL || this == REPAIR; }
    }

    /** Faixas da tabela de servicos da Economia do Ato 2. {@code 0} em {@link Service#tier()}: qualquer uma. */
    public static final int TIER_ANY = 0, TIER_MAX = 4;

    /** Nome da faixa para o jogador: cura usa gravidade; reparo usa o dano do equipamento. */
    public static String tierLabel(ActionType action, int tier) {
        if (action == ActionType.REPAIR) return switch (tier) {
            case 1 -> "leve"; case 2 -> "médio"; case 3 -> "pesado"; case 4 -> "quase destruído"; default -> "qualquer";
        };
        return switch (tier) {
            case 1 -> "leve"; case 2 -> "moderado"; case 3 -> "grave"; case 4 -> "crítico"; default -> "qualquer";
        };
    }

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
     *
     * @param tier        so em cura e reparo: o servico atende apenas aquela faixa (1 a 4);
     *                    {@link #TIER_ANY} atende qualquer uma
     * @param enchantment so em {@code ENCHANT}: id do encantamento aplicado
     * @param level       so em {@code ENCHANT}: nivel aplicado
     */
    public record Service(String name, String description, ActionType action, List<String> commands,
                          Cost cost, int cooldownSeconds, int tier, String enchantment, int level) {
        public Service { commands = List.copyOf(commands); }

        public Service(String name, String description, ActionType action, List<String> commands, Cost cost, int cooldownSeconds) {
            this(name, description, action, commands, cost, cooldownSeconds, TIER_ANY, "", 0);
        }
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
