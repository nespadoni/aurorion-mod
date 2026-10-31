package com.aurorion.core.character;

import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * O que a ficha do site diz deste personagem, lido <b>de memória</b>, na hora.
 *
 * <p>Contrato no molde do {@link com.aurorion.core.lives.LivesGate}: o {@code aurorion-ficha} responde
 * com um retrato que ele mesmo guarda (atualizado a cada leitura da ficha e no login), e quem pergunta
 * — a magia, para o teste do lançamento; as profissões, para o teste de fabricação — não importa a
 * ficha nem espera rede nenhuma.
 *
 * <p><b>E se a ficha não estiver no pack, ou ainda não tiver sido lida?</b> {@link #of} responde
 * {@link Stats#UNKNOWN}, e quem pergunta usa o próprio bônus padrão do config. Nunca "zero": a falta
 * de um jar ou de uma ficha vinculada não pode transformar todo mundo em incapaz.
 */
public final class CharacterSheet {
    /** O que o dono da ficha precisa saber responder. Consulta pura em memória; aceita jogador offline. */
    @FunctionalInterface
    public interface Sheet {
        Stats of(MinecraftServer server, UUID profile);
    }

    /**
     * Retrato imutável de uma ficha. Montado uma vez a cada leitura do site; consultar não aloca.
     *
     * <p>Os ids são os do catálogo do site ({@code for}, {@code des}, {@code vig}, {@code sab},
     * {@code int}, {@code vit}; perícias como {@code criacao} e {@code astros_e_mistos}). Os derivados
     * ({@code vida_max}, {@code pe_max}, {@code stamina_max}, {@code defesa}, {@code rd},
     * {@code iniciativa}, {@code fortitude}) vão junto para quem um dia montar combate com a ficha.
     *
     * @param magicBonus bônus do teste de magia, já calculado pela ficha com o atributo e a perícia do config
     * @param craftBonus bônus do teste de ofício, idem
     * @param techniques as técnicas <b>equipadas</b> na ficha (golpes especiais do combate com ficha)
     */
    public record Stats(boolean known, long characterId, Map<String, Integer> attributes,
                        Map<String, Integer> skills, Map<String, Integer> skillBonuses,
                        Map<String, Integer> derived, int magicBonus, int craftBonus, List<Technique> techniques) {
        public static final Stats UNKNOWN = new Stats(false, 0L, Map.of(), Map.of(), Map.of(), Map.of(), 0, 0, List.of());

        public Stats {
            attributes = Map.copyOf(attributes);
            skills = Map.copyOf(skills);
            skillBonuses = Map.copyOf(skillBonuses);
            derived = Map.copyOf(derived);
            techniques = List.copyOf(techniques);
        }

        /** Atributo efetivo, ou {@code fallback} se a ficha não tem esse atributo. */
        public int attribute(String id, int fallback) {
            return attributes.getOrDefault(id, fallback);
        }

        /** Valor treinado da perícia (0–5), ou {@code fallback}. */
        public int skill(String id, int fallback) {
            return skills.getOrDefault(id, fallback);
        }

        /** Bônus total de teste da perícia (atributo + treino + Marca), como o site calcula. */
        public int skillBonus(String id, int fallback) {
            return skillBonuses.getOrDefault(id, fallback);
        }

        public int derived(String id, int fallback) {
            return derived.getOrDefault(id, fallback);
        }
    }

    /**
     * Uma técnica da ficha. {@code tier} é a raridade 1–5 do site (as estrelas); {@code level} o nível da
     * técnica; {@code element} o id do elemento (pode vir vazio).
     */
    public record Technique(String name, String element, int tier, int level) {
        public Technique {
            name = name == null ? "" : name;
            element = element == null ? "" : element;
        }
    }

    @Nullable
    private static volatile Sheet sheet;

    private CharacterSheet() {
    }

    /** Chamado uma vez pelo mod da ficha, na construção dele. */
    public static void provide(Sheet value) {
        sheet = value;
    }

    public static boolean installed() {
        return sheet != null;
    }

    /** @return o retrato da ficha do perfil, ou {@link Stats#UNKNOWN} se não há ficha lida. */
    public static Stats of(MinecraftServer server, UUID profile) {
        Sheet current = sheet;
        if (current == null) return Stats.UNKNOWN;
        Stats stats = current.of(server, profile);
        return stats == null ? Stats.UNKNOWN : stats;
    }
}
