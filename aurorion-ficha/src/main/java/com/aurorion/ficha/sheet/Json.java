package com.aurorion.ficha.sheet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Leitura tolerante do JSON do site. Campo ausente, nulo ou de outro tipo vira o valor neutro (texto
 * vazio, 0, false) em vez de exceção: uma versão nova do backend com campo diferente nunca derruba a
 * tela. Todo texto sai cortado no teto pedido e sem caractere de controle nem código de formatação
 * {@code §}, e toda lista sai com teto — é isso que deixa o pacote para o cliente com tamanho limitado.
 */
public final class Json {
    /** Sem escapar {@code < > & =}: o texto vai para o Tessera, que já escapa por conta própria. */
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    /**
     * Teto do texto que {@link #parseObject} aceita. A resposta do site já chega limitada a 256 KiB;
     * o que o servidor manda ao cliente é bem menor. Acima disso não é ficha: é lixo ou ataque.
     */
    public static final int MAX_DOCUMENT = 512 * 1024;

    private Json() {
    }

    @Nullable
    public static JsonObject parseObject(@Nullable String text) {
        if (text == null || text.isEmpty() || text.length() > MAX_DOCUMENT) return null;
        try {
            JsonElement element = JsonParser.parseString(text);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (RuntimeException | StackOverflowError e) {
            // JSON quebrado ou aninhado demais: neutro, nunca exceção para quem chamou.
            return null;
        }
    }

    public static String write(JsonElement element) {
        return GSON.toJson(element);
    }

    @Nullable
    public static JsonObject object(@Nullable JsonObject from, String member) {
        return from != null && from.get(member) instanceof JsonObject obj ? obj : null;
    }

    @Nullable
    public static JsonArray array(@Nullable JsonObject from, String member) {
        return from != null && from.get(member) instanceof JsonArray arr ? arr : null;
    }

    /**
     * Texto do campo: string ou número (o Go manda {@code idade}, {@code rarity} como inteiro).
     * Booleano, objeto, lista ou nulo viram vazio.
     */
    public static String str(@Nullable JsonObject from, String member, int max) {
        if (from == null || !(from.get(member) instanceof JsonPrimitive value) || value.isBoolean()) return "";
        return clean(value.getAsString(), max);
    }

    /** Como {@link #str}, mas o texto cortado termina em "…" (descrição: o jogador vê que tem mais no site). */
    public static String strClipped(@Nullable JsonObject from, String member, int max) {
        if (from == null || !(from.get(member) instanceof JsonPrimitive value) || value.isBoolean()) return "";
        return clip(value.getAsString(), max);
    }

    /** {@link #clean} com reticências quando passa do teto (o resultado nunca passa de {@code max}). */
    public static String clip(@Nullable String text, int max) {
        if (max <= 1) return clean(text, max);
        String full = clean(text, max + 1);
        if (full.codePointCount(0, full.length()) <= max) return full;
        String cut = full.substring(0, full.offsetByCodePoints(0, max - 1)).stripTrailing();
        return cut + "…";
    }

    /**
     * Endereço ({@code portrait_url}, {@code site_path}): maior que o teto vira vazio em vez de
     * cortado — um link pela metade é pior que nenhum. Só {@code http(s)://} ou caminho {@code /…}.
     */
    public static String link(@Nullable JsonObject from, String member, int max) {
        if (from == null || !(from.get(member) instanceof JsonPrimitive value) || !value.isString()) return "";
        String raw = value.getAsString().strip();
        if (raw.isEmpty() || raw.length() > max) return "";
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c <= ' ' || c == 0x7F || c == '§' || c == '"' || c == '<' || c == '>' || c == '\\'
                    || Character.isISOControl(c) || Character.getType(c) == Character.FORMAT) return "";
        }
        boolean web = raw.regionMatches(true, 0, "https://", 0, 8) || raw.regionMatches(true, 0, "http://", 0, 7);
        boolean path = raw.charAt(0) == '/' && !raw.startsWith("//");
        return web || path ? raw : "";
    }

    public static int integer(@Nullable JsonObject from, String member) {
        if (from == null || !(from.get(member) instanceof JsonPrimitive value)) return 0;
        if (value.isNumber() || value.isString()) return clamp(toDouble(value.getAsString()));
        return 0;
    }

    /** Ids ({@code uint} no Go): inteiro exato até {@code long}; texto numérico também vale. */
    public static long number(@Nullable JsonObject from, String member) {
        if (from == null || !(from.get(member) instanceof JsonPrimitive value)) return 0L;
        if (!value.isNumber() && !value.isString()) return 0L;
        String text = value.getAsString().strip();
        // Long.parseLong primeiro: não passa por BigDecimal (o longValue do Gson faria isso com "1e999999").
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            double d = toDouble(text);
            if (Double.isNaN(d)) return 0L;
            return (long) Math.max(Long.MIN_VALUE / 2.0, Math.min(Long.MAX_VALUE / 2.0, d));
        }
    }

    public static boolean bool(@Nullable JsonObject from, String member) {
        Boolean value = optionalBool(from, member);
        return value != null && value;
    }

    /** {@code null} quando o campo não veio: "sem CD" é diferente de "falhou". */
    @Nullable
    public static Boolean optionalBool(@Nullable JsonObject from, String member) {
        if (from == null || !(from.get(member) instanceof JsonPrimitive value)) return null;
        if (value.isBoolean()) return value.getAsBoolean();
        if (value.isNumber()) {
            double d = toDouble(value.getAsString());
            return Double.isNaN(d) ? null : d != 0;
        }
        String text = value.getAsString().strip();
        if ("true".equalsIgnoreCase(text) || "1".equals(text)) return true;
        if ("false".equalsIgnoreCase(text) || "0".equals(text)) return false;
        return null;
    }

    /** Lista de objetos convertida um a um, com teto; elemento que não é objeto é ignorado. */
    public static <T> List<T> objects(@Nullable JsonArray array, int max, Function<JsonObject, T> convert) {
        List<T> out = new ArrayList<>(array == null ? 0 : Math.min(array.size(), max));
        if (array == null) return out;
        for (JsonElement element : array) {
            if (out.size() >= max) break;
            if (element instanceof JsonObject obj) {
                T value = convert.apply(obj);
                if (value != null) out.add(value);
            }
        }
        return out;
    }

    /** Lista de textos; aceita também objetos com {@code id} (formato de condição alternativo). */
    public static List<String> strings(@Nullable JsonArray array, int max, int maxLength) {
        List<String> out = new ArrayList<>(array == null ? 0 : Math.min(array.size(), max));
        if (array == null) return out;
        for (JsonElement element : array) {
            if (out.size() >= max) break;
            if (element instanceof JsonPrimitive value) {
                if (value.isBoolean()) continue;
                String text = clean(value.getAsString(), maxLength);
                if (!text.isEmpty()) out.add(text);
            } else if (element instanceof JsonObject obj) {
                String id = str(obj, "id", maxLength);
                if (!id.isEmpty()) out.add(id);
            }
        }
        return out;
    }

    public static JsonArray toArray(List<String> values) {
        JsonArray out = new JsonArray();
        for (String value : values) out.add(value);
        return out;
    }

    /**
     * Corta em {@code max} pontos de código (as "runas" do backend), sem partir um par substituto.
     * Caractere de controle (quebra de linha inclusive: a tela quebra sozinha) vira espaço, e espaços
     * seguidos viram um. Sai o que mexe na exibição: o código de formatação {@code §x} do Minecraft
     * (num nome viraria cor ou texto embaralhado no chat), os caracteres invisíveis de formatação
     * (inversão bidirecional, largura zero) e metade solta de par substituto (vira {@code �}).
     */
    public static String clean(@Nullable String text, int max) {
        if (text == null || text.isEmpty() || max <= 0) return "";
        StringBuilder out = new StringBuilder(Math.min(text.length(), max + 8));
        int count = 0;
        boolean space = true; // começa "depois de um espaço": corta os espaços do começo
        for (int i = 0; i < text.length() && count < max; ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '§') {
                // §x: o x é o código; sai junto. § no fim do texto sai sozinho.
                if (i < text.length() && Character.isLetterOrDigit(text.charAt(i))) i++;
                continue;
            }
            if (Character.getType(cp) == Character.FORMAT) continue;
            if (Character.isSurrogate((char) cp) && Character.charCount(cp) == 1) cp = 0xFFFD;
            if (Character.isISOControl(cp) || Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                if (space) continue;
                out.append(' ');
                space = true;
            } else {
                out.appendCodePoint(cp);
                space = false;
            }
            count++;
        }
        int end = out.length();
        if (end > 0 && out.charAt(end - 1) == ' ') out.setLength(end - 1);
        return out.toString();
    }

    private static double toDouble(String text) {
        String t = text.strip();
        // Só decimal: "0x1F", "Infinity" com sinal estranho etc. caem no neutro.
        if (t.isEmpty() || t.length() > 64) return Double.NaN;
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static int clamp(double value) {
        if (Double.isNaN(value)) return 0;
        return (int) Math.max(Integer.MIN_VALUE / 2, Math.min(Integer.MAX_VALUE / 2, value));
    }
}
