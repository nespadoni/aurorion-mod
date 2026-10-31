package com.aurorion.ficha.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * {@code config/aurorion/ficha-client.toml}: onde e como os cartões da ficha aparecem no HUD deste
 * cliente e o que eles escondem numa live. Só deste computador — o servidor não vê nem manda nada daqui.
 *
 * <p>Os padrões foram escolhidos contra o pack real: o canto superior direito é do minimapa (JourneyMap
 * em {@code TopRight}, 30 % da altura) e dos ícones de efeito; o superior esquerdo, do HUD de grupo do
 * Simple Voice Chat e do minimapa do Map Atlases; o inferior esquerdo, do chat e do ícone de voz; o
 * topo central, do Jade e das barras de chefe. Por isso a pilha de rolagens fica à direita no meio
 * da altura (e desce sozinha abaixo do minimapa do JourneyMap, se ele estiver lá) e o pedido do mestre
 * no centro, abaixo do Jade. Valores lidos ao vivo: mudar o arquivo vale na hora.
 */
public final class FichaClientConfig {
    /**
     * Onde fica um bloco do HUD. {@code *_TOPO} empilha para baixo a partir do topo (abaixo do que
     * já ocupa aquele canto), {@code *_MEIO} começa perto do meio da altura, {@code *_BAIXO} empilha
     * para cima a partir da base (acima da barra de itens ou do chat).
     */
    public enum Ancora {
        DIREITA_TOPO, DIREITA_MEIO, DIREITA_BAIXO, ESQUERDA_TOPO, ESQUERDA_MEIO, ESQUERDA_BAIXO, CENTRO_TOPO;

        public boolean right() {
            return this == DIREITA_TOPO || this == DIREITA_MEIO || this == DIREITA_BAIXO;
        }

        public boolean left() {
            return this == ESQUERDA_TOPO || this == ESQUERDA_MEIO || this == ESQUERDA_BAIXO;
        }

        public boolean center() {
            return this == CENTRO_TOPO;
        }

        public boolean bottom() {
            return this == DIREITA_BAIXO || this == ESQUERDA_BAIXO;
        }

        public boolean middle() {
            return this == DIREITA_MEIO || this == ESQUERDA_MEIO;
        }
    }

    /** Cartões das rolagens dos outros (os seus e as respostas aos seus pedidos sempre aparecem). */
    public enum Outros {
        TODAS, PEDIDAS, NENHUMA
    }

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    static {
        B.comment("Cartões de rolagem: o seu (grande, com o d20) e os de quem rolou perto (menores).").push("cartoes");
    }

    public static final ModConfigSpec.EnumValue<Ancora> CARDS_ANCHOR = B
            .comment("Onde a pilha fica. DIREITA_MEIO (padrão) começa perto do meio da altura, abaixo dos ícones de",
                    "efeito, das barras de chefe e do minimapa do JourneyMap. DIREITA_TOPO é o lugar antigo (colado",
                    "nos efeitos: cobre minimapa no canto direito). DIREITA_BAIXO empilha para cima, acima da barra de itens.",
                    "Opções: DIREITA_TOPO, DIREITA_MEIO, DIREITA_BAIXO, ESQUERDA_TOPO, ESQUERDA_MEIO, ESQUERDA_BAIXO, CENTRO_TOPO")
            .defineEnum("ancora", Ancora.DIREITA_MEIO);

    public static final ModConfigSpec.IntValue CARDS_X = B
            .comment("Desloca a pilha na horizontal, em pixels da GUI (positivo = para a direita).")
            .defineInRange("deslocamentoX", 0, -400, 400);

    public static final ModConfigSpec.IntValue CARDS_Y = B
            .comment("Desloca a pilha na vertical, em pixels da GUI (positivo = para baixo).")
            .defineInRange("deslocamentoY", 0, -300, 300);

    public static final ModConfigSpec.DoubleValue CARDS_SCALE = B
            .comment("Tamanho dos cartões (1.0 = normal). Em escala de GUI 4 ou tela pequena, 0.85 deixa caber mais.")
            .defineInRange("escala", 1.0, 0.75, 1.25);

    public static final ModConfigSpec.IntValue OWN_SECONDS = B
            .comment("Segundos que o cartão da sua rolagem (ou da resposta a um pedido seu) fica na tela.")
            .defineInRange("segundosSuas", 9, 3, 30);

    public static final ModConfigSpec.IntValue OTHER_SECONDS = B
            .comment("Segundos que o cartão de uma rolagem de outro jogador fica na tela.")
            .defineInRange("segundosOutros", 6, 2, 30);

    public static final ModConfigSpec.IntValue MAX_CARDS = B
            .comment("Quantos cartões cabem na pilha ao mesmo tempo (os mais velhos saem). O que não couber na",
                    "altura da tela espera a vez escondido.")
            .defineInRange("maximo", 6, 1, 10);

    public static final ModConfigSpec.EnumValue<Outros> OTHERS = B
            .comment("Rolagens dos outros: TODAS (padrão), PEDIDAS (só as que responderam a um pedido do mestre)",
                    "ou NENHUMA. A linha no chat continua chegando em todos os casos.")
            .defineEnum("rolagensDosOutros", Outros.TODAS);

    public static final ModConfigSpec.BooleanValue AVOID_MINIMAP = B
            .comment("Com o JourneyMap instalado, descobrir onde o minimapa está e não pôr cartão por cima dele.",
                    "Desligado, só a âncora e os deslocamentos valem.")
            .define("desviarMinimapa", true);

    static {
        B.pop().comment("Cartão do pedido do mestre (e os avisos de ficha: alt sem ficha, ficha antiga).").push("pedido");
    }

    public static final ModConfigSpec.EnumValue<Ancora> ASK_ANCHOR = B
            .comment("Onde o pedido aparece. CENTRO_TOPO (padrão): no meio, abaixo do Jade e das barras de chefe — fora",
                    "do HUD de grupo do Voice Chat e do Map Atlases (canto superior esquerdo) e do chat. ESQUERDA_TOPO",
                    "é o lugar antigo (à esquerda, ~1/4 da altura). DIREITA_* põe a pilha de rolagens para desviar dele.")
            .defineEnum("ancora", Ancora.CENTRO_TOPO);

    public static final ModConfigSpec.IntValue ASK_X = B
            .comment("Desloca o pedido na horizontal, em pixels da GUI (positivo = para a direita).")
            .defineInRange("deslocamentoX", 0, -400, 400);

    public static final ModConfigSpec.IntValue ASK_Y = B
            .comment("Desloca o pedido na vertical, em pixels da GUI (positivo = para baixo).")
            .defineInRange("deslocamentoY", 0, -300, 300);

    public static final ModConfigSpec.DoubleValue ASK_SCALE = B
            .comment("Tamanho do cartão do pedido (1.0 = normal).")
            .defineInRange("escala", 1.0, 0.75, 1.25);

    static {
        B.pop().comment("O que os cartões escondem quando a tela pode estar sendo transmitida.").push("privacidade");
    }

    public static final ModConfigSpec.BooleanValue FOLLOW_STREAMER = B
            .comment("Com o modo streamer do Aurorion Essentials ligado (/streamer on ou total), os cartões do HUD",
                    "mostram só o que um vizinho veria: rolagem secreta vira \"Rolagem secreta\" sem números, a CD",
                    "oculta pelo mestre some e o bônus do teste vira \"?\" (pode ser número de NPC). A mesa do mestre",
                    "mostra um selo \"modo streamer\".")
            .define("seguirModoStreamer", true);

    public static final ModConfigSpec.BooleanValue HIDE_SECRETS = B
            .comment("Esconder sempre, com ou sem o modo streamer, o mesmo que o modo streamer esconde.")
            .define("ocultarSegredos", false);

    static {
        B.pop();
    }

    public static final ModConfigSpec SPEC = B.build();

    private FichaClientConfig() {
    }

    /** Lê o valor ao vivo; antes de a config carregar devolve o padrão em vez de lançar. */
    public static <T> T get(ModConfigSpec.ConfigValue<T> value) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return value.getDefault();
        }
    }

    public static float scale(ModConfigSpec.DoubleValue value) {
        double v = get(value);
        return (float) Math.max(0.75, Math.min(1.25, v));
    }
}
