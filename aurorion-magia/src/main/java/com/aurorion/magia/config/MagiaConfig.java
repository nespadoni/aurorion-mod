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
    public static final ModConfigSpec.IntValue DOMINATION_RADIUS;

    public static final ModConfigSpec.IntValue DREAD_RADIUS;
    public static final ModConfigSpec.IntValue DREAD_KNEEL_RADIUS;
    public static final ModConfigSpec.BooleanValue DREAD_PROSTRATES;
    public static final ModConfigSpec.BooleanValue DREAD_DARKENS;
    public static final ModConfigSpec.BooleanValue DREAD_SPARE_ALLIES;
    public static final ModConfigSpec.BooleanValue HEALING_TOUCH_HOSTILES;

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
                        "Presenca Aterradora: raio (blocos) em que as pessoas sentem medo — tela preta",
                        "fechando, tremor, batida de coracao e nevoa negra. Nao tira vida nem atributo de",
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

        DREAD_DARKENS = BUILDER
                .comment(
                        "Presenca Aterradora: a aura apaga a luz do mundo de quem esta dentro (a Escuridao do",
                        "vanilla), e nao so fecha a nevoa. E o que faz tudo em volta escurecer de verdade,",
                        "inclusive com shader pack — o pacote de shaders respeita a iluminacao do jogo.",
                        "Desligue se a escuridao total estiver inviabilizando cena em lugar fechado.")
                .define("dreadDarkens", true);

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
        SPEC = BUILDER.build();
    }

    private MagiaConfig() {
    }
}
