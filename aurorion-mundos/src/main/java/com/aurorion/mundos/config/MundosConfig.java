package com.aurorion.mundos.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Config do lado servidor, em {@code config/aurorion_mundos-server.toml}.
 *
 * <p>A fronteira com o datapack e a do resto do ecossistema — <b>aqui regra, la conteudo</b> — mas
 * com uma diferenca que contraria a diretriz 4 da SDD §7 de proposito:
 *
 * <p><b>O seed de cada mundo fica aqui, e nao no datapack.</b> Um datapack recarrega com
 * {@code /reload}; um seed <b>nao pode</b> mudar com o servidor no ar, porque o terreno ja gerado nao
 * muda junto — o mundo ficaria costurado com dois mapas diferentes. Config so e lida no boot
 * ({@code DedicatedServer#initServer} carrega a config SERVER antes do {@code loadLevel()} que cria
 * as dimensoes), que e exatamente a garantia que este dado precisa.
 *
 * <p>O segundo motivo e operacional: para pre-gerar o terreno em outra maquina, o que precisa viajar
 * junto com o modpack e <b>este arquivo</b>. Um numero num TOML e copiavel; um datapack inteiro e
 * mais coisa para divergir.
 */
public final class MundosConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<List<? extends String>> SEEDS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RUNTIME_GENERATION;

    public static final ModConfigSpec.DoubleValue DEFAULT_BORDER_SIZE;
    public static final ModConfigSpec.IntValue DEFAULT_SEARCH_RADIUS;

    public static final ModConfigSpec.IntValue DENY_MESSAGE_COOLDOWN_SECONDS;
    public static final ModConfigSpec.BooleanValue LOG_DENIALS;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.comment(
                "Os mundos extras do Aurorion.",
                "A EXISTENCIA de uma dimensao nao se declara aqui — isso e datapack, em",
                "data/<namespace>/dimension/<nome>.json. Aqui fica o que nao pode mudar com o servidor no ar."
        ).push("mundos");

        SEEDS = BUILDER
                .comment(
                        "O seed de cada mundo, no formato <dimensao>=<numero>.",
                        "",
                        "Dimensao SEM seed declarado aqui nao e tocada por este mod: continua usando o seed do",
                        "mundo, igual ao vanilla. E deliberado — assim nenhuma dimensao de mod de terceiro muda",
                        "de terreno por engano so porque este mod esta instalado.",
                        "",
                        "CUIDADO: trocar um seed depois que o mundo ja foi gerado nao regera nada. O terreno",
                        "antigo continua no disco e o novo passa a ser gerado com outro seed, deixando uma",
                        "emenda visivel. Trocar o seed exige apagar a pasta dimensions/<namespace>/<nome>/.",
                        "",
                        "Para pre-gerar em outra maquina, e este valor que tem que ser identico nas duas."
                )
                .defineList("seeds",
                        List.of("aurorion_mundos:mundo_dois=284119730051", "aurorion_mundos:mundo_tres=-762045118829"),
                        () -> "aurorion_mundos:mundo_dois=0",
                        entry -> entry instanceof String text && text.indexOf('=') > 0);

        RUNTIME_GENERATION = BUILDER
                .comment(
                        "Dimensoes em que um portal PODE cavar um portal novo — e portanto gerar terreno — na",
                        "hora em que um jogador atravessa. Vazia por padrao.",
                        "",
                        "Essa e a trava que mantem a promessa de so gerar quando voce mandar. Com a dimensao",
                        "fora desta lista, uma travessia que nao ache portal do outro lado e negada com",
                        "mensagem, em vez de chamar PortalForcer#createPortal — que varre um espiral de 16",
                        "blocos consultando a altura do terreno, e cada consulta dessas gera o chunk se ele",
                        "ainda nao existe. Com 90 pessoas isso e um pico de worldgen no horario de pico.",
                        "",
                        "O fluxo pretendido e o oposto: pre-gerar o terreno com o Chunky em outra maquina,",
                        "copiar a pasta dimensions/<namespace>/<nome>/ para o mundo de producao com o servidor",
                        "parado, e construir os portais de chegada na mao antes de abrir a dimensao.",
                        "",
                        "A segunda trava, e a mais forte, e a barreira: borda dentro da area pre-gerada",
                        "significa que nao ha para onde andar, logo nao ha chunk novo para gerar."
                )
                .defineList("allowRuntimeGeneration",
                        List.of(),
                        () -> "aurorion_mundos:mundo_dois",
                        entry -> entry instanceof String);

        BUILDER.pop();
        BUILDER.comment("A barreira de cada mundo. Tamanho e centro sao por dimensao, e persistem.").push("barreira");

        DEFAULT_BORDER_SIZE = BUILDER
                .comment(
                        "Diametro da barreira de um mundo na primeira vez que ele sobe, em blocos.",
                        "Depois disso vale o que estiver salvo — /mundos borda grava, e reiniciar nao perde.",
                        "",
                        "O padrao e restritivo de proposito. A barreira e o que garante que nenhum chunk seja",
                        "gerado em runtime, e ela so garante isso se couber DENTRO do que voce pre-gerou.",
                        "Ajuste para o tamanho real da sua pre-geracao antes de abrir a dimensao.",
                        "",
                        "Nao afeta o overworld, o Nether nem o End: este mod so mexe na barreira das dimensoes",
                        "com seed declarado em [mundos].seeds."
                )
                .defineInRange("defaultSize", 10000.0D, 1.0D, 5.9999968E7D);

        BUILDER.pop();
        BUILDER.comment("Como o portal escolhe o ponto de chegada.").push("portais");

        DEFAULT_SEARCH_RADIUS = BUILDER
                .comment(
                        "Raio, em blocos, da busca por um portal ja existente do outro lado — quando a ligacao",
                        "do datapack nao declara um proprio.",
                        "",
                        "O vanilla usa 128 fora do Nether. Ele precisa disso porque o Nether comprime 8:1 e o",
                        "ponto de chegada cai longe do esperado; entre dois mundos de escala 1:1 o destino cai",
                        "na MESMA coordenada, entao 128 nao compra nada e custa caro: PoiManager varre",
                        "(2*128/16+1)^2 = 289 chunks por travessia. Com 16 sao 9.",
                        "",
                        "So aumente se algum mundo seu usar coordinate_scale diferente de 1.0."
                )
                .defineInRange("defaultSearchRadius", 16, 1, 128);

        DENY_MESSAGE_COOLDOWN_SECONDS = BUILDER
                .comment(
                        "Intervalo minimo entre dois avisos de travessia negada para o mesmo jogador.",
                        "Sem isso, quem fica parado dentro do portal recebe a mensagem a cada tick."
                )
                .defineInRange("denyMessageCooldownSeconds", 5, 1, 300);

        LOG_DENIALS = BUILDER
                .comment(
                        "Escreve no log toda travessia negada por falta de portal do outro lado.",
                        "Ligue enquanto estiver montando os portais de chegada de um mundo novo."
                )
                .define("logDenials", false);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private MundosConfig() {
    }
}
