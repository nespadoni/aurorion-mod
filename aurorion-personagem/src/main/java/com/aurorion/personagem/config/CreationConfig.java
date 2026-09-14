package com.aurorion.personagem.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * O texto da tela e as duas decisoes que mudam quem e barrado.
 *
 * <p>Tudo aqui e do servidor: a tela do cliente nao tem texto proprio, ela desenha o que recebeu no
 * pacote. Trocar uma frase e editar {@code config/aurorion/personagem-server.toml} e recarregar —
 * sem tocar em resource pack e sem atualizar o mod de ninguem.
 */
public final class CreationConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<String> TITLE = B
            .comment("Titulo da tela de criacao.")
            .define("titulo", "Quem é você em Aurorion?", CreationConfig::shortText);

    public static final ModConfigSpec.ConfigValue<String> WELCOME = B
            .comment("Texto de quem nunca teve personagem nesta conta.")
            .define("boasVindas", "Antes da primeira pedra e do primeiro passo, um nome. "
                    + "Ele será como o mundo vai te chamar, e ficará com você até o fim desta história.",
                    CreationConfig::longText);

    public static final ModConfigSpec.ConfigValue<String> REBIRTH = B
            .comment("Texto de quem esta criando outro personagem depois de uma morte definitiva.")
            .define("recomeco", "A história anterior terminou. Nada dela vem junto: nem o que você "
                    + "guardou, nem o caminho que abriu, nem o nome que usou. Diga quem chega agora.",
                    CreationConfig::longText);

    public static final ModConfigSpec.ConfigValue<String> RULES = B
            .comment("Linha de regra do nome, abaixo dos campos.")
            .define("regra", "Nome e sobrenome, apenas letras, de 2 a 24 caracteres cada. "
                    + "Um nome só pertence a um personagem — mesmo depois que ele morre.",
                    CreationConfig::longText);

    public static final ModConfigSpec.ConfigValue<String> GREETING = B
            .comment("Anunciado no chat quando o personagem nasce. %s vira o nome completo.")
            .define("saudacao", "%s abriu os olhos em Aurorion pela primeira vez.", CreationConfig::longText);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FIRST_WORDS = B
            .comment("Mensagens enviadas so para quem acabou de criar o personagem.")
            .defineListAllowEmpty("primeirasPalavras", List.of(
                    "Você é %s.",
                    "Este mundo não vai lembrar de você por você. Faça com que lembre."
            ), () -> "", CreationConfig::longText);

    public static final ModConfigSpec.BooleanValue ASK_EXISTING = B
            .comment("Pede nome a quem ja jogava antes deste mod existir.",
                    "A progressao dessas pessoas NAO e apagada: elas so ganham um nome.",
                    "Em false, quem ja jogava segue sem nome e so os personagens novos sao nomeados.")
            .define("cobrarDeQuemJaJoga", true);

    public static final ModConfigSpec.BooleanValue SPAWN_ON_BIRTH = B
            .comment("Leva o personagem novo para o spawn do mundo depois do reset.",
                    "Em false ele acorda onde o anterior morreu — inclusive dentro do Limbo.")
            .define("nascerNoSpawn", true);

    public static final ModConfigSpec SPEC = B.build();

    private CreationConfig() {
    }

    private static boolean shortText(Object value) {
        return value instanceof String text && !text.isBlank() && text.length() <= 128;
    }

    private static boolean longText(Object value) {
        return value instanceof String text && text.length() <= 512;
    }
}
