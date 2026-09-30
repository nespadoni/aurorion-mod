package com.aurorion.essentials.client;

import com.aurorion.essentials.fakename.FakeName;
import com.aurorion.essentials.fakename.FakeNameRegistry;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.jetbrains.annotations.Nullable;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;

/**
 * Troca o nick pelo nome do personagem no instante do desenho, e so ali.
 *
 * <p><b>Exibicao apenas.</b> O telefone roteia ligacao, mensagem e PIX por nome, resolvido no
 * servidor com {@code PlayerList#getPlayerByName} — ou seja, pelo nick da conta Mojang. O campo que
 * a tela guarda e devolve ao servidor continua sendo o nick real; muda o que aparece. O motivo
 * completo esta em {@code MattupolisPhoneCompat}. O caminho inverso — alguem digita o nome do
 * personagem no e-mail ou no PIX — e o {@link #nickFor(String)}, que devolve o nick antes do envio.
 *
 * <p>Dois indices de nick para nome:
 * <ul>
 *   <li>quem esta online agora (ou esteve nesta sessao), com o nome vivo do {@link FakeNameRegistry};</li>
 *   <li>o diretorio que o servidor manda no login ({@code PhoneNameDirectoryPayload}), com todo
 *       personagem que ja teve nome — e o que faz o contato de quem esta offline aparecer com o nome
 *       do personagem, e nao com o nick.</li>
 * </ul>
 *
 * <p>Isto roda no caminho de render: todo texto de toda tela do telefone, todo frame. Por isso o
 * indice de nick e montado no maximo uma vez por segundo em vez de varrer a lista de jogadores a
 * cada chamada — com 80 pessoas online e uma agenda aberta, a varredura por chamada era mais de mil
 * comparacoes de string por frame (ver SDD.md §5). A troca dentro de frases ("Arthur - Oi!", "★ nick",
 * "nick is offline.") quebra o texto em palavras e consulta os indices palavra a palavra; o
 * resultado fica num cache por texto, zerado so quando algum nome muda.
 */
public final class MattupolisPhoneNames {
    /**
     * Nick para conta. Ordenado sem diferenciar maiuscula de minuscula: a busca sai sem alocar a
     * copia em minusculas que um {@code HashMap} exigiria a cada texto desenhado.
     */
    private static final Map<String, UUID> BY_NICK = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    /** Nick para nome do personagem, de quem esta offline tambem. Vem do servidor. */
    private static final Map<String, String> DIRECTORY = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    /** Texto original para texto exibido. Limpo quando enche ou quando um nome muda. */
    private static final Map<String, String> SHOWN = new HashMap<>();
    private static final int SHOWN_LIMIT = 1024;

    /** Um segundo de atraso para um nome recem-trocado e invisivel numa tela de celular. */
    private static final long REFRESH_INTERVAL_MS = 1000L;

    /** Menor e maior nick possivel na Mojang: fora disso a palavra nem e consultada. */
    private static final int MIN_NICK = 3;
    private static final int MAX_NICK = 16;

    private static ClientPacketListener connection;
    private static boolean indexed;
    private static long refreshedAt;
    private static long signature;
    private static int directoryVersion;

    private MattupolisPhoneNames() {}

    /** O nome do personagem quando o texto inteiro e um nick (ou um {@code @nick} do Twitter). */
    public static String display(String text) {
        if (text == null || text.isBlank()) return text;
        if (!ready()) return text;
        String exact = exactOrHandle(text);
        return exact != null ? exact : text;
    }

    /**
     * Como o {@link #display(String)}, mas troca tambem o nick que aparece dentro de uma frase:
     * previa de notificacao, contato fixado, mensagem de sistema, "X Location" do GPS.
     */
    public static String displayOrReplace(String text) {
        if (text == null || text.length() < MIN_NICK) return text;
        if (!ready()) return text;

        String cached = SHOWN.get(text);
        if (cached != null) return cached;

        String exact = exactOrHandle(text);
        String shown = exact != null ? exact : replaceTokens(text, MattupolisPhoneNames::exact);
        if (SHOWN.size() >= SHOWN_LIMIT) SHOWN.clear();
        SHOWN.put(text, shown);
        return shown;
    }

    /** Igual ao {@link #display(String)}, cortado na largura do cabecalho de chamada. */
    public static String callLabel(String name) {
        String display = display(name);
        if (display == null) return name;

        var font = Minecraft.getInstance().font;
        return font.width(display) <= 144 ? display : font.plainSubstrByWidth(display, 132) + "...";
    }

    /**
     * O nick de quem tem este nome de personagem, para o que o jogador digita (destinatario do
     * e-mail, alvo do PIX). O servidor do telefone so entende nick; sem isto, digitar o nome que o
     * proprio celular mostra dava "jogador nao encontrado".
     *
     * <p>Um nick digitado passa intacto, mesmo que coincida com o nome de algum personagem: quem
     * sabe o nick quer o dono dele.
     */
    public static String nickFor(String typed) {
        if (typed == null || typed.isBlank()) return typed;
        if (!ready()) return typed;
        String wanted = typed.trim();
        if (BY_NICK.containsKey(wanted) || DIRECTORY.containsKey(wanted)) return wanted;

        for (Map.Entry<String, UUID> entry : BY_NICK.entrySet()) {
            FakeName fake = FakeNameRegistry.get(entry.getValue());
            if (fake != null && fake.plain().equalsIgnoreCase(wanted)) return entry.getKey();
        }
        for (Map.Entry<String, String> entry : DIRECTORY.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(wanted)) return entry.getKey();
        }
        return typed;
    }

    /**
     * Recebe o diretorio do servidor. {@code replace} vem no login (a lista inteira); fora dele sao
     * so as mudancas, e um nome vazio tira o nick do diretorio.
     */
    public static void applyDirectory(boolean replace, Map<String, String> entries) {
        if (replace) DIRECTORY.clear();
        entries.forEach((nick, name) -> {
            if (name == null || name.isBlank()) DIRECTORY.remove(nick);
            else DIRECTORY.put(nick, name);
        });
        directoryVersion++;
        SHOWN.clear();
    }

    private static boolean ready() {
        ClientPacketListener current = Minecraft.getInstance().getConnection();
        if (current == null) return false;
        refreshIndex(current);
        return true;
    }

    @Nullable
    private static String exactOrHandle(String text) {
        String exact = exact(text);
        if (exact != null) return exact;
        // O Twitter do telefone mostra "@" + nick em minusculas. Vira o @ do personagem.
        if (text.length() > 1 && text.charAt(0) == '@') {
            String name = exact(text.substring(1));
            if (name != null) return "@" + handleOf(name);
        }
        return null;
    }

    @Nullable
    private static String exact(String nick) {
        if (nick.length() < MIN_NICK || nick.length() > MAX_NICK) return null;
        UUID account = BY_NICK.get(nick);
        if (account != null) {
            FakeName fake = FakeNameRegistry.get(account);
            if (fake != null) return fake.plain();
        }
        return DIRECTORY.get(nick);
    }

    private static void refreshIndex(ClientPacketListener current) {
        if (connection != current) {
            BY_NICK.clear();
            DIRECTORY.clear();
            SHOWN.clear();
            connection = current;
            indexed = false;
        }

        long now = Util.getMillis();
        if (!needsRefresh(indexed, refreshedAt, now)) return;
        indexed = true;
        refreshedAt = now;

        // Acrescenta sem limpar: a agenda do telefone continua listando quem saiu no meio da sessao,
        // e esse contato tem que seguir aparecendo com o nome do personagem. O mapa so zera quando a
        // conexao troca, que e quando os UUIDs deixam de valer.
        for (var info : current.getOnlinePlayers()) {
            var profile = info.getProfile();
            BY_NICK.put(profile.getName(), profile.getId());
        }

        // O cache de textos so vale enquanto nenhum nome mudar. Conferir e barato: uma volta pelos
        // nomes de quem esta online, uma vez por segundo.
        long names = BY_NICK.size() * 31L + directoryVersion;
        for (var entry : FakeNameRegistry.all().entrySet()) {
            names = names * 31 + (entry.getKey().hashCode() ^ entry.getValue().plain().hashCode());
        }
        if (names != signature) {
            signature = names;
            SHOWN.clear();
        }
    }

    /**
     * Um "ja montei alguma vez" explicito em vez de um instante inicial sentinela.
     *
     * <p>A versao anterior comecava com {@code refreshedAt = Long.MIN_VALUE} para forcar a primeira
     * montagem, e {@code now - Long.MIN_VALUE} estourava o {@code long}: a diferenca voltava
     * negativa, ficava eternamente abaixo do intervalo, e o indice nunca era montado. O celular
     * mostrava o nick de todo mundo porque o mapa estava sempre vazio.</p>
     */
    static boolean needsRefresh(boolean alreadyIndexed, long lastRefresh, long now) {
        return !alreadyIndexed || now - lastRefresh >= REFRESH_INTERVAL_MS;
    }

    /**
     * Troca cada palavra que o {@code resolver} reconhece. Palavra aqui e o que pode ser nick:
     * letras, digitos e {@code _}. Devolve a mesma instancia quando nada muda, para o cache nao
     * guardar copias.
     */
    static String replaceTokens(String text, Function<String, String> resolver) {
        StringBuilder out = null;
        int copied = 0;
        int length = text.length();
        int i = 0;
        while (i < length) {
            if (!isNickChar(text.charAt(i))) {
                i++;
                continue;
            }
            int start = i;
            while (i < length && isNickChar(text.charAt(i))) i++;
            int size = i - start;
            if (size < MIN_NICK || size > MAX_NICK) continue;
            String name = resolver.apply(text.substring(start, i));
            if (name == null) continue;
            if (out == null) out = new StringBuilder(length + 16);
            out.append(text, copied, start).append(name);
            copied = i;
        }
        if (out == null) return text;
        return out.append(text, copied, length).toString();
    }

    private static boolean isNickChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
    }

    /** O @ do personagem no Twitter: "Joao da Silva" vira "joao_da_silva", como o telefone faz com nick. */
    static String handleOf(String name) {
        String plain = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        StringBuilder out = new StringBuilder(plain.length());
        for (char c : plain.trim().toLowerCase(Locale.ROOT).toCharArray()) {
            if (c == ' ') out.append('_');
            else if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_') out.append(c);
        }
        return out.isEmpty() ? "personagem" : out.toString();
    }
}
