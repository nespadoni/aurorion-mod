package com.aurorion.ficha.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * {@code config/aurorion/ficha-server.toml}: alcance das rolagens, quem é mestre no jogo, prazo dos
 * pedidos e os avisos que o jogador lê.
 *
 * <p>Nada de credencial aqui: endereço e token do site são da integração
 * ({@code integracao-startup.toml}). Config {@code SERVER} é enviada a quem conecta — não pode ter
 * segredo. Os valores são lidos ao vivo a cada uso; mudar o arquivo não exige reiniciar.
 */
public final class FichaConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    static {
        B.push("rolagens");
    }

    public static final ModConfigSpec.IntValue PUBLIC_RADIUS = B
            .comment("Rolagem pública: quem estiver até este número de blocos, na mesma dimensão, vê o cartão",
                    "e a linha no chat. Rolagem secreta só chega a quem rolou e aos mestres online.")
            .defineInRange("raioPublico", 24, 1, 256);

    public static final ModConfigSpec.IntValue CACHE_SECONDS = B
            .comment("Por quanto tempo a ficha lida do site vale para reabrir a tela sem nova chamada.",
                    "Rolar, trocar o vínculo ou sair do servidor descarta a cópia antes disso. 0 desliga.")
            .defineInRange("cacheSegundos", 20, 0, 300);

    static {
        B.pop().push("mestre");
    }

    public static final ModConfigSpec.IntValue GM_PERMISSION = B
            .comment("Nível de permissão (OP) do perfil para abrir a mesa do mestre, pedir testes e ver as",
                    "rolagens secretas. Vantagem, desvantagem e atributo alternativo ainda exigem staff",
                    "confirmada pelo site. O alt de /personagem alt nasce sem OP e por isso não é mestre:",
                    "a staff mestra pela conta principal. Mínimo 1: com 0 todo jogador veria as secretas.")
            .defineInRange("permissaoMestre", 2, 1, 4);

    public static final ModConfigSpec.IntValue TABLE_RADIUS = B
            .comment("A mesa do mestre lista os jogadores até este número de blocos, na mesma dimensão.")
            .defineInRange("raioMesa", 48, 4, 512);

    public static final ModConfigSpec.IntValue REQUEST_SECONDS = B
            .comment("Prazo de um pedido de teste do mestre. Nada persiste: reiniciar o servidor apaga os pedidos.")
            .defineInRange("pedidoExpiraSegundos", 180, 30, 1800);

    static {
        B.pop().push("textos");
    }

    public static final ModConfigSpec.BooleanValue WARN_ALT = B
            .comment("No login de um alt, consulta o site uma vez e avisa se esse personagem ainda não tem ficha.")
            .define("avisarAltSemFicha", true);

    public static final ModConfigSpec.BooleanValue WARN_OUTDATED = B
            .comment("No login da conta principal, consulta o site uma vez (5 a 8 s depois de entrar) e avisa se a",
                    "ficha ativa ainda é antiga (sem a construção nova; enviada e aguardando aprovação não avisa).",
                    "No máximo uma vez por dia por perfil; a resposta já fica no cache para abrir a ficha.",
                    "Desligado, o login da conta principal não chama o site.")
            .define("avisarFichaAntiga", true);

    public static final ModConfigSpec.ConfigValue<String> ALT_WITHOUT_SHEET = B
            .comment("Aviso do login de um alt sem ficha vinculada.")
            .define("altSemFicha", "Este personagem ainda não tem ficha. Escolha qual ficha ou NPC ele usa:",
                    FichaConfig::shortText);

    public static final ModConfigSpec.ConfigValue<String> REQUEST_TITLE = B
            .comment("Abertura do pedido de teste, no HUD e no chat.")
            .define("pedidoTitulo", "O mestre pede", FichaConfig::shortText);

    public static final ModConfigSpec.ConfigValue<String> NOT_LINKED = B
            .comment("Orientação para quem ainda não vinculou a conta do site.")
            .define("semVinculo", "Vincule sua conta do site primeiro: no site, abra Perfil → RPG → No jogo (ou "
                    + "Perfil → Diário), gere o código e digite aqui /vincular CÓDIGO.", FichaConfig::longText);

    public static final ModConfigSpec.ConfigValue<String> SITE_DOWN = B
            .comment("Quando o site não responde.")
            .define("siteFora", "O site não respondeu agora. Tente de novo em instantes.", FichaConfig::shortText);

    static {
        B.pop().push("retrato");
    }

    public static final ModConfigSpec.BooleanValue SNAPSHOT_ON_LOGIN = B
            .comment("No login, lê a ficha uma vez (5 a 8 s depois de entrar) e guarda o retrato que a magia e as",
                    "profissões usam nos testes. Desligado, o retrato só se atualiza quando a pessoa abre a ficha.")
            .define("lerNoLogin", true);

    public static final ModConfigSpec.IntValue SNAPSHOT_RENEW_MINUTES = B
            .comment("Com a pessoa online, relê a ficha a cada tantos minutos (a mudança feita no site chega ao",
                    "jogo sem ela abrir a ficha). 0 desliga.")
            .defineInRange("renovarMinutos", 30, 0, 720);

    public static final ModConfigSpec.ConfigValue<String> MAGIC_ATTRIBUTE = B
            .comment("Atributo da ficha que rege o teste de magia (for, des, vig, sab, int, vit).")
            .define("magiaAtributo", "vit", FichaConfig::catalogId);

    public static final ModConfigSpec.ConfigValue<String> MAGIC_SKILL = B
            .comment("Perícia somada ao teste de magia (só o valor treinado; o atributo já vem acima).")
            .define("magiaPericia", "astros_e_mistos", FichaConfig::catalogId);

    public static final ModConfigSpec.ConfigValue<String> CRAFT_SKILL = B
            .comment("Perícia do teste de ofício (fabricar, fundir, forjar). Usa o bônus de teste completo,",
                    "como o site rola: atributo + treino + Marca.")
            .define("oficioPericia", "criacao", FichaConfig::catalogId);

    static {
        B.pop();
    }

    public static final ModConfigSpec SPEC = B.build();

    private FichaConfig() {
    }

    /**
     * Lê o valor ao vivo; antes de a config do servidor carregar (ou no cliente sem servidor
     * integrado) devolve o padrão em vez de lançar.
     */
    public static int get(ModConfigSpec.IntValue value) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return value.getDefault();
        }
    }

    public static boolean get(ModConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return value.getDefault();
        }
    }

    public static String get(ModConfigSpec.ConfigValue<String> value) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return value.getDefault();
        }
    }

    private static boolean catalogId(Object value) {
        return value instanceof String text && text.matches("[a-z_]{2,32}");
    }

    private static boolean shortText(Object value) {
        return value instanceof String text && !text.isBlank() && text.length() <= 160;
    }

    private static boolean longText(Object value) {
        return value instanceof String text && !text.isBlank() && text.length() <= 400;
    }
}
