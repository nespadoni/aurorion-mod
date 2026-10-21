package com.aurorion.areas.rules;

/**
 * Regras de protecao do mundo: quem pode mexer nos blocos de uma area.
 *
 * <p><b>Ficam fora de {@link AreaRule} de proposito.</b> {@code AreaRule} e resolvida na posicao do
 * <em>jogador</em>, uma vez por tick dele, e cada regra a mais ali e trabalho a mais no tick de 80
 * pessoas. Estas valem na posicao do <em>bloco</em> e so sao consultadas quando alguem quebra, poe,
 * abre, explode, escorre ou empurra alguma coisa — perguntas que o tick nunca faz.
 *
 * <p>Semantica (ver {@code AreaData#protection}):
 * <ul>
 *   <li>Area comum: tudo liberado ate a staff negar ({@code /area regra <id> construir negar}).</li>
 *   <li>Area de casa: <b>negado por padrao para quem nao e da casa</b>. Quem e da casa tem uma
 *       excecao implicita nas regras da propria area de casa. Um {@code permitir} explicito na area
 *       de casa abre para todo mundo; uma area de prioridade maior que opine tambem vence.</li>
 *   <li>{@link #byPlayer} = regra de acao de uma pessoa (excecao por jogador vale);
 *       as outras sao do ambiente e so olham de onde vem a coisa.</li>
 * </ul>
 */
public enum ProtectionRule {
    /** Quebrar e colocar blocos (inclui balde, isqueiro, enxada e afins). */
    BUILD("construir", true),
    /** Abrir bau/fornalha/qualquer bloco com inventario, portas, alavancas, botoes, molduras. */
    INTERACT("interagir", true),
    /** Explosao destruindo bloco dentro da area, venha de onde vier. */
    EXPLOSION("explosao", false),
    /** Fluido (e o fogo que a lava acende) entrando na area vindo de fora dela. */
    FLUID("fluido", false),
    /** Pistao de fora empurrando ou puxando blocos para dentro/fora da area. */
    PISTON("pistao", false);

    public static final ProtectionRule[] ALL = values();
    /**
     * O passe de visitante da barreira de casa ({@code HouseBarrier#PASS_RULE}). Mora aqui para a
     * camada de dados nao depender do pacote de servidor: quem tem o passe entra, entao tambem abre
     * a porta — {@code interagir} vira permitido para ele. Construir continua negado.
     */
    public static final String VISITOR_PASS = "casa";
    private final String key;
    private final boolean byPlayer;

    ProtectionRule(String key, boolean byPlayer) {
        this.key = key;
        this.byPlayer = byPlayer;
    }

    public String key() { return key; }
    public boolean byPlayer() { return byPlayer; }
}
