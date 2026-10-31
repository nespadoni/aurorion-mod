package com.aurorion.magia.config;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/** Config do servidor, em {@code config/aurorion/magia-server.toml}. */
public final class MagiaConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue STAFF_BYPASS;
    public static final ModConfigSpec.BooleanValue AUTHORITATIVE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FORBIDDEN_SPELLS;
    public static final ModConfigSpec.ConfigValue<String> UNLOCK_MODE;

    public static final ModConfigSpec.ConfigValue<String> NODE_TIER1, NODE_TIER2, NODE_TIER3, TIER2_RARITY;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SPECIALTY_NODES;

    public static final ModConfigSpec.BooleanValue TEST_ENABLED;
    public static final ModConfigSpec.IntValue DC_COMMON, DC_UNCOMMON, DC_RARE, DC_EPIC, DC_LEGENDARY, DC_FORBIDDEN,
            NATURAL_MIN_DC, DEFAULT_BONUS, CRIT_LEVELS, EXHAUST_SECONDS, DIZZY_SECONDS;

    public static final ModConfigSpec.IntValue SPECIALIST_BONUS, SPECIALIST_MANA, SPECIALIST_COOLDOWN;
    public static final ModConfigSpec.DoubleValue SPECIALIST_POWER;
    public static final ModConfigSpec.IntValue DOMINATION_RADIUS;

    public static final ModConfigSpec.IntValue DREAD_RADIUS;
    public static final ModConfigSpec.IntValue DREAD_KNEEL_RADIUS;
    public static final ModConfigSpec.BooleanValue DREAD_PROSTRATES;
    public static final ModConfigSpec.BooleanValue DREAD_SPARE_ALLIES;
    public static final ModConfigSpec.BooleanValue HEALING_TOUCH_HOSTILES;

    public static final ModConfigSpec.BooleanValue SPELLS_IGNORE_TEAMS;
    public static final ModConfigSpec.BooleanValue SPHAERA_BREAKS_BLOCKS;
    public static final ModConfigSpec.DoubleValue IUSTITIA_THRESHOLD;
    public static final ModConfigSpec.DoubleValue MORS_THRESHOLD;
    public static final ModConfigSpec.IntValue PYROBOLUS_RANGE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> PERSISTENT_CONTROLS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SILENT_CONTROLS;
    public static final ModConfigSpec.IntValue DISPEL_RADIUS;

    public static final ModConfigSpec.IntValue SOUL_DURATION;
    public static final ModConfigSpec.IntValue VOID_RADIUS;
    public static final ModConfigSpec.IntValue VOID_DURATION;
    public static final ModConfigSpec.IntValue SHRINE_RADIUS;
    public static final ModConfigSpec.IntValue SHRINE_DURATION;

    public static final ModConfigSpec.IntValue QUANTUM_ENERGY, QUANTUM_RECHARGE, QUANTUM_BLINK_COST, QUANTUM_STONE_COST,
            QUANTUM_DIMENSION_COST, QUANTUM_RANGE, QUANTUM_STRAIN_FROM, QUANTUM_STRAIN_DECAY, QUANTUM_EXHAUSTED;
    public static final ModConfigSpec.IntValue ANACHRONIC_RANGE, ANACHRONIC_COOLDOWN_HOURS, ANACHRONIC_SAFE_SECONDS,
            ANACHRONIC_MAX_SECONDS, ANACHRONIC_FRAGILE_BASE, ANACHRONIC_FRAGILE_EXTRA;
    public static final ModConfigSpec.IntValue LUNAR_HEAL_RADIUS, LUNAR_HEAL_RADIUS_STEP;
    public static final ModConfigSpec.BooleanValue LUNAR_NIGHT_VISION, LUNAR_DAY_PENALTY;
    public static final ModConfigSpec.IntValue POCKET_BRING_RADIUS, POCKET_MAX_GUESTS, POCKET_MEDIUM_SECONDS,
            POCKET_FRAGILE_SECONDS, POCKET_RADIUS;
    public static final ModConfigSpec.ConfigValue<String> POCKET_START_BLOCK;
    public static final ModConfigSpec.IntValue FLASH_RADIUS;
    public static final ModConfigSpec.BooleanValue FLASH_SPARES_CASTER;
    public static final ModConfigSpec.BooleanValue FLASH_NEEDS_SIGHT;
    public static final ModConfigSpec SPEC;

    static {
        BUILDER.comment("Liberacao de magias por aula/RP.").push("liberacao");

        STAFF_BYPASS = BUILDER
                .comment(
                        "Staff (permissao 2+) conjura qualquer magia sem liberacao. Serve para ensaiar aula e",
                        "testar; desligue se a staff tambem joga personagem com progressao propria.")
                .define("staffBypass", true);

        AUTHORITATIVE = BUILDER
                .comment(
                        "A lista do Aurorion e a unica fonte de verdade.",
                        "true: o que o jogador aprendeu por fora (manuscrito do Iron's Restrictions, magias",
                        "  padrao dele, eldritch pesquisado) e esquecido no proximo login ou comando, e nao conjura.",
                        "false: o aprendido por fora continua valendo junto com as liberacoes do Aurorion.",
                        "Com true, deixe DefaultLearntSpells vazio no config do Iron's Restrictions.")
                .define("authoritative", true);

        FORBIDDEN_SPELLS = BUILDER
                .comment(
                        "Magias proibidas alem das do Aurorion (que ja vem marcadas). Proibida nao vem junto",
                        "com a escola: so '/aurorion spells unlock spell <id>' a concede, uma a uma.",
                        "O bloqueio de craft e de loot so vale para as magias do Aurorion; para as de outros",
                        "addons, desligue 'allowCrafting' no config de magia do Iron's.",
                        "Ids completos, ex.: [\"irons_spellbooks:black_hole\"]")
                .defineListAllowEmpty("forbiddenSpells", List.of(), () -> "irons_spellbooks:black_hole",
                        value -> value instanceof String id && ResourceLocation.tryParse(id) != null);

        UNLOCK_MODE = BUILDER
                .comment(
                        "Quem decide o que cada um conjura.",
                        "no (padrao): os nos do Despertar Arcano na Trama, que so um professor concede",
                        "  (/trama no conceder <jogador> AR1). I: magias comuns e incomuns; II: raras, epicas e",
                        "  lendarias; III: proibidas. O /aurorion spells unlock continua valendo como excecao.",
                        "aula: so o /aurorion spells unlock, como antes da Trama.",
                        "Sem o aurorion-trama no pack, vale 'aula' automaticamente.")
                .define("modo", "no", value -> "no".equals(value) || "aula".equals(value));

        BUILDER.pop();
        BUILDER.comment("Nos da Trama que a magia le (aurorion-trama).").push("nos");

        NODE_TIER1 = BUILDER.comment("Despertar Arcano I.").define("nivel1", "AR1", MagiaConfig::nodeId);
        NODE_TIER2 = BUILDER.comment("Despertar Arcano II.").define("nivel2", "AR2", MagiaConfig::nodeId);
        NODE_TIER3 = BUILDER.comment("Despertar Arcano III (proibidas).").define("nivel3", "AR3", MagiaConfig::nodeId);
        TIER2_RARITY = BUILDER
                .comment("A partir desta raridade (do nivel conjurado) a magia exige o Despertar II.",
                        "COMMON, UNCOMMON, RARE, EPIC ou LEGENDARY.")
                .define("raridadeNivel2", "RARE", value -> value instanceof String text
                        && List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY").contains(text));
        SPECIALTY_NODES = BUILDER
                .comment("Especialidade: escola=no. So vale com o Despertar I.")
                .defineListAllowEmpty("especialidades", List.of(
                                "irons_spellbooks:fire=ES_FIRE", "irons_spellbooks:ice=ES_ICE",
                                "irons_spellbooks:lightning=ES_LIGHTNING", "irons_spellbooks:holy=ES_HOLY",
                                "irons_spellbooks:ender=ES_ENDER", "irons_spellbooks:blood=ES_BLOOD",
                                "irons_spellbooks:evocation=ES_EVOCATION", "irons_spellbooks:nature=ES_NATURE",
                                "irons_spellbooks:eldritch=ES_ELDRITCH"),
                        () -> "irons_spellbooks:fire=ES_FIRE",
                        value -> value instanceof String text && text.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+=[A-Za-z0-9_]+"));

        BUILDER.pop();
        BUILDER.comment(
                "Teste da ficha a cada conjuracao: d20 + Vitral + Astros & Mistos (ou o que a ficha configurar)",
                "contra a CD da raridade conjurada. Falha: a magia nao sai e o custo e cheio (mana e recarga; o",
                "pergaminho se perde). Falha critica: alem disso, Exausto. Critico: a magia sai com nivel a mais.").push("teste");

        TEST_ENABLED = BUILDER.comment("Desligado, toda conjuracao liberada sai, como antes.").define("habilitado", true);
        DC_COMMON = BUILDER.comment("CD das magias comuns.").defineInRange("cdComum", 8, 0, 40);
        DC_UNCOMMON = BUILDER.comment("CD das incomuns.").defineInRange("cdIncomum", 10, 0, 40);
        DC_RARE = BUILDER.comment("CD das raras.").defineInRange("cdRara", 13, 0, 40);
        DC_EPIC = BUILDER.comment("CD das epicas.").defineInRange("cdEpica", 16, 0, 40);
        DC_LEGENDARY = BUILDER.comment("CD das lendarias.").defineInRange("cdLendaria", 19, 0, 40);
        DC_FORBIDDEN = BUILDER.comment("Somado a CD de uma magia proibida.").defineInRange("cdProibida", 3, 0, 20);
        NATURAL_MIN_DC = BUILDER.comment("O 1 natural so e falha critica com CD a partir desta.")
                .defineInRange("cdMinimaNatural", 8, 0, 40);
        DEFAULT_BONUS = BUILDER
                .comment("Bonus de quem nao tem ficha lida (sem integracao, ficha antiga, recem-chegado).")
                .defineInRange("bonusSemFicha", 4, -10, 30);
        CRIT_LEVELS = BUILDER
                .comment("Niveis a mais no acerto critico (dano, duracao, alcance e quantidade crescem com o nivel).",
                        "O teto e o nivel maximo da magia mais estes niveis.")
                .defineInRange("niveisNoCritico", 1, 0, 5);
        EXHAUST_SECONDS = BUILDER.comment("Exausto depois da falha critica: lento e sem conjurar.")
                .defineInRange("segundosExausto", 10, 0, 120);
        DIZZY_SECONDS = BUILDER.comment("Tontura (nausea) da falha critica.").defineInRange("segundosTonto", 3, 0, 30);

        BUILDER.pop();
        BUILDER.comment("Especialista: a escola da especialidade escolhida na Trama.").push("especialista");

        SPECIALIST_BONUS = BUILDER.comment("Bonus no teste (alem da vantagem: rola dois d20 e fica com o maior).")
                .defineInRange("bonusTeste", 2, 0, 20);
        SPECIALIST_MANA = BUILDER.comment("Mana a menos, em %.").defineInRange("manaMenos", 15, 0, 90);
        SPECIALIST_COOLDOWN = BUILDER.comment("Recarga a menos, em %.").defineInRange("recargaMenos", 20, 0, 90);
        SPECIALIST_POWER = BUILDER
                .comment("Poder da escola a mais (o atributo do Iron's: dano, cura e efeitos que escalam com poder).",
                        "0.2 = +20%.")
                .defineInRange("poderEscola", 0.2, 0.0, 2.0);

        BUILDER.pop();
        BUILDER.comment("Magia entre aliados.").push("aliados");

        SPELLS_IGNORE_TEAMS = BUILDER
                .comment(
                        "true (padrao): magia acerta qualquer pessoa, de qualquer time. Nenhuma magia poupa aliado",
                        "(nem do Aurorion nem do Iron's), e o dano entre aliados passa mesmo com o fogo amigo do",
                        "time desligado. Area sem PvP (servidor ou aurorion-areas) continua protegendo.",
                        "A autoria do dano e os efeitos de acerto da magia sao preservados.",
                        "false: magia poupa o proprio time, como o Iron's faz sozinho.",
                        "A Presenca Aterradora tem chave propria (dreadSparesAllies).")
                .define("magiasIgnoramTime", true);

        BUILDER.pop();
        BUILDER.comment("Imperium Mentis.").push("imperium");

        DOMINATION_RADIUS = BUILDER
                .comment(
                        "Raio (blocos) em que um mob dominado procura o proximo alvo. A busca roda uma vez",
                        "por segundo e so em mob dominado; o custo cresce com o volume do raio.")
                .defineInRange("dominationRadius", 12, 4, 24);

        BUILDER.pop();
        BUILDER.comment("Passivas do personagem (/aurorion passivas).").push("passivas");

        DREAD_RADIUS = BUILDER
                .comment(
                        "Presenca Aterradora: raio (blocos) em que as pessoas sentem medo — sombra nos cantos",
                        "da tela, tremor e batida de coracao. Ninguem fica cego. Nao tira vida nem atributo de",
                        "ninguem. Para a gente ao redor, aumentar este raio NAO custa mais nada: a aura le a",
                        "lista de jogadores da dimensao, sem busca espacial. So a fuga das criaturas varre uma",
                        "esfera, a cada 2 segundos, e SO enquanto alguem estiver com a aura ligada.")
                .defineInRange("dreadRadius", 30, 8, 48);

        DREAD_KNEEL_RADIUS = BUILDER
                .comment(
                        "Presenca Aterradora: raio (blocos) em que as pessoas se prostram. O padrao e igual a",
                        "dreadRadius — todo mundo que sente o medo tambem cede ao chao. Valor maior que",
                        "dreadRadius e cortado para ele; 0 desliga a prostracao e deixa a aura so sensorial.",
                        "Baixe este numero se a cena precisar que a plateia continue de pe e andando.")
                .defineInRange("dreadKneelRadius", 30, 0, 48);

        DREAD_PROSTRATES = BUILDER
                .comment(
                        "Presenca Aterradora: a pose de quem cede diante da aura.",
                        "true (padrao): prostracao — os dois joelhos no chao, o corpo curvado.",
                        "false: um joelho so, o corpo cedendo sem se entregar.",
                        "Nas duas, as maos continuam livres: comer, beber, erguer escudo e conjurar seguem",
                        "funcionando — a aura fica ligada por tempo indeterminado, e tirar o item da mao de",
                        "quem atravessa a rua viraria impossibilidade de jogar em vez de susto.")
                .define("dreadProstrates", true);

        DREAD_SPARE_ALLIES = BUILDER
                .comment(
                        "Aliados de time do portador ficam de fora da aura. Ligado, os capangas do vilao nao",
                        "se prostram para ele; desligado, a presenca nao poupa ninguem.")
                .define("dreadSparesAllies", true);

        HEALING_TOUCH_HOSTILES = BUILDER
                .comment(
                        "Mao que Cura: se o golpe tambem cura criatura hostil.",
                        "false (padrao): zumbi, esqueleto e companhia continuam levando dano — sem isso a",
                        "  personagem fica incapaz de se defender de qualquer bicho.",
                        "true: a mao cura tudo o que toca, e quem a tem simplesmente nao luta corpo a corpo.")
                .define("healingTouchHealsHostiles", false);

        BUILDER.pop();
        BUILDER.comment("Magias de destruicao e execucao.").push("destruicao");

        SPHAERA_BREAKS_BLOCKS = BUILDER
                .comment(
                        "Esfera Espiritual (Genki Dama): se a explosao abre cratera no terreno.",
                        "Mesmo ligada, ela nao quebra bloco onde quem conjurou nao poderia construir (protecao",
                        "de spawn), em area do aurorion-areas que proiba magia, nem bloco lacrado; e mods de",
                        "protecao que tratam explosao (ExplosionEvent) continuam valendo.",
                        "false: so dano e empurrao, o terreno fica intacto.")
                .define("esferaQuebraBlocos", true);

        IUSTITIA_THRESHOLD = BUILDER
                .comment(
                        "Justica Demaciana: fracao da vida maxima abaixo da qual o alvo e executado no nivel 1.",
                        "Cada nivel acima soma 0.05 (padrao: 25%, 30%, 35%).")
                .defineInRange("limiarJustica", 0.25, 0.0, 0.9);

        MORS_THRESHOLD = BUILDER
                .comment(
                        "Morte Vinda das Profundezas: fracao da vida maxima abaixo da qual quem estiver no X e",
                        "executado no nivel 1. Cada nivel acima soma 0.05 (padrao: 20%, 25%, 30%).")
                .defineInRange("limiarProfundezas", 0.20, 0.0, 0.9);

        PYROBOLUS_RANGE = BUILDER
                .comment(
                        "Bomba Megainfernal: alcance maximo da mira, em blocos. A mira le blocos ate essa",
                        "distancia uma vez por conjuracao; acima da distancia de visao do servidor, ela pode",
                        "carregar chunks ao procurar o chao.")
                .defineInRange("alcanceBomba", 128, 32, 256);

        BUILDER.pop();
        BUILDER.comment("Controle de cenas RP. Use os ids curtos das magias do Aurorion.").push("roleplay");
        List<String> controls = List.of("vox_interdicta", "mutatio_ferae", "genua_flecte",
                "imperium_mentis", "aspectus_captus", "mundus_vacuus", "ferrum_ligatum", "carcer_aquae");
        PERSISTENT_CONTROLS = BUILDER.comment("Magias cujo controle dura ate reconjurar no alvo ou remover o efeito.",
                        "Remova um id para usar a duracao normal (agora dobrada). Lista vazia desliga a persistencia.")
                .defineListAllowEmpty("controlesAteReconjurar", controls, () -> "mutatio_ferae", controls::contains);
        SILENT_CONTROLS = BUILDER.comment("Magias de controle que tambem bloqueiam voz, chat e conjuracao enquanto duram.",
                        "Vox Interdicta sempre silencia. Remova ids para permitir fala nos demais controles.")
                .defineListAllowEmpty("controlesSilenciam", controls, () -> "mutatio_ferae", controls::contains);
        DISPEL_RADIUS = BUILDER.comment("Esconjurar: raio em blocos, incluindo quem conjurou e aliados.")
                .defineInRange("raioEsconjurar", 8, 1, 32);
        BUILDER.pop();
        BUILDER.comment("Ultimates de alma e expansoes; tempos em ticks (20 = 1 segundo).").push("dominios");
        SOUL_DURATION = BUILDER.comment("Reino das Almas: duracao do duelo em outra dimensao.")
                .defineInRange("duracaoReinoAlmas", 600, 100, 2400);
        VOID_RADIUS = BUILDER.comment("Vazio Infinito: raio da esfera fechada.")
                .defineInRange("raioVazioInfinito", 24, 4, 40);
        VOID_DURATION = BUILDER.comment("Vazio Infinito: duracao da incapacitacao dentro da expansao.")
                .defineInRange("duracaoVazioInfinito", 400, 40, 1200);
        SHRINE_RADIUS = BUILDER.comment("Santuario Malevolente: esfera aberta, sem destruir blocos. Maximo 200.")
                .defineInRange("raioSantuarioMalevolente", 200, 8, 200);
        SHRINE_DURATION = BUILDER.comment("Santuario Malevolente: duracao; cortes a cada 10 ticks.")
                .defineInRange("duracaoSantuarioMalevolente", 600, 100, 2400);
        BUILDER.pop();

        BUILDER.comment("Passivas dos representantes dos elementos. Tempos em segundos, salvo indicacao.")
                .push("representantes");
        BUILDER.comment("Quantico (Robs): teleporte livre e pedras de teleporte, com energia propria.").push("quantico");
        QUANTUM_ENERGY = BUILDER.comment("Energia maxima, em cargas.")
                .defineInRange("energiaMaxima", 20, 1, 200);
        QUANTUM_RECHARGE = BUILDER.comment("Segundos para recuperar uma carga.")
                .defineInRange("segundosPorCarga", 30, 1, 3600);
        QUANTUM_BLINK_COST = BUILDER.comment("Cargas por salto (teleporte para onde se olha).")
                .defineInRange("custoSalto", 1, 0, 200);
        QUANTUM_STONE_COST = BUILDER.comment("Cargas por viagem a uma pedra de teleporte (Waystones).")
                .defineInRange("custoPedra", 3, 0, 200);
        QUANTUM_DIMENSION_COST = BUILDER.comment("Cargas a mais quando a pedra fica em outra dimensao.")
                .defineInRange("custoOutraDimensao", 2, 0, 200);
        QUANTUM_RANGE = BUILDER.comment("Alcance do salto, em blocos.")
                .defineInRange("alcanceSalto", 32, 4, 128);
        QUANTUM_STRAIN_FROM = BUILDER.comment("A partir de quantos teleportes seguidos Robs sente o peso da energia.")
                .defineInRange("pesoAPartirDe", 10, 1, 200);
        QUANTUM_STRAIN_DECAY = BUILDER.comment("Segundos para um teleporte sair da conta do peso.")
                .defineInRange("segundosPorAlivio", 45, 1, 3600);
        QUANTUM_EXHAUSTED = BUILDER.comment("Esgotou a energia: segundos carregando antes do proximo teleporte.")
                .defineInRange("segundosEsgotado", 90, 0, 3600);
        BUILDER.pop();

        BUILDER.comment("Anacronico (Gretta): ver pelos olhos de outra pessoa, uma vez por dia.").push("anacronico");
        ANACHRONIC_RANGE = BUILDER.comment("Distancia maxima do alvo ao comecar, em blocos.")
                .defineInRange("alcance", 24, 4, 128);
        ANACHRONIC_COOLDOWN_HOURS = BUILDER.comment("Horas REAIS entre duas possessoes (24 = uma por dia).")
                .defineInRange("horasEntreUsos", 24, 0, 720);
        ANACHRONIC_SAFE_SECONDS = BUILDER.comment("Segundos sem custo extra.")
                .defineInRange("segundosLeves", 10, 1, 120);
        ANACHRONIC_MAX_SECONDS = BUILDER.comment("Segundos maximos fora do corpo.")
                .defineInRange("segundosMaximos", 30, 1, 300);
        ANACHRONIC_FRAGILE_BASE = BUILDER.comment("Segundos fragilizada ao voltar, mesmo sem passar do tempo leve.")
                .defineInRange("fragilBase", 30, 0, 3600);
        ANACHRONIC_FRAGILE_EXTRA = BUILDER.comment("Segundos fragilizada a mais por segundo alem do tempo leve.")
                .defineInRange("fragilPorSegundoExtra", 9, 0, 600);
        BUILDER.pop();

        BUILDER.comment("Minguantis (Iacy): mais forte quanto mais funda a noite, e cura quem esta perto.").push("minguantis");
        LUNAR_HEAL_RADIUS = BUILDER.comment("Raio da restauracao na primeira hora da noite, em blocos.")
                .defineInRange("raioCura", 8, 1, 48);
        LUNAR_HEAL_RADIUS_STEP = BUILDER.comment("Blocos a mais de raio por fase de poder (ate 3 fases, na meia-noite).")
                .defineInRange("raioCuraPorFase", 2, 0, 16);
        LUNAR_NIGHT_VISION = BUILDER.comment("Visao noturna enquanto a bencao estiver ativa.")
                .define("visaoNoturna", true);
        LUNAR_DAY_PENALTY = BUILDER.comment("O contrario do Escanor ate o fim: de dia, fraqueza leve.")
                .define("fraquezaDiurna", false);
        BUILDER.pop();

        BUILDER.comment("Vazio (Iris): um vazio so dela, onde ela tem criativo e nada entra nem sai.").push("vazio");
        POCKET_BRING_RADIUS = BUILDER.comment("Marcados ate esta distancia entram junto com Iris.")
                .defineInRange("raioLevar", 16, 1, 64);
        POCKET_MAX_GUESTS = BUILDER.comment("Quantos marcados ela pode manter.")
                .defineInRange("maximoMarcados", 3, 0, 8);
        POCKET_MEDIUM_SECONDS = BUILDER.comment("Com 2 marcados dentro: segundos de pressao na fenda para invadir.")
                .defineInRange("segundosInvasaoMediana", 12, 1, 600);
        POCKET_FRAGILE_SECONDS = BUILDER.comment("Com 3 ou mais marcados dentro: segundos para invadir.")
                .defineInRange("segundosInvasaoFacil", 3, 1, 600);
        POCKET_RADIUS = BUILDER.comment("Ate onde se anda no vazio a partir do bloco inicial, em blocos: o tamanho",
                        "da area em que Iris pode construir.")
                .defineInRange("raioBolso", 64, 16, 512);
        POCKET_START_BLOCK = BUILDER.comment("O unico bloco que existe no vazio na primeira entrada, para ela comecar a construir.")
                .define("blocoInicial", "minecraft:stone", value -> value instanceof String text
                        && net.minecraft.resources.ResourceLocation.tryParse(text) != null);
        BUILDER.pop();
        BUILDER.pop();

        BUILDER.comment("Clarao (Fulgor Caecans).").push("clarao");
        FLASH_RADIUS = BUILDER.comment("Raio da cegueira no nivel 1; +4 por nivel. O estouro e no alto: o raio e grande.")
                .defineInRange("raio", 24, 2, 64);
        FLASH_SPARES_CASTER = BUILDER.comment("Quem lancou nao fica cego.")
                .define("poupaConjurador", true);
        FLASH_NEEDS_SIGHT = BUILDER.comment("Atras de parede nao pega. Desligado (padrao): todo mundo no raio fica cego.")
                .define("exigeVisao", false);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }

    private MagiaConfig() {
    }

    private static boolean nodeId(Object value) {
        return value instanceof String text && text.matches("[A-Za-z0-9_]{1,32}");
    }
}
