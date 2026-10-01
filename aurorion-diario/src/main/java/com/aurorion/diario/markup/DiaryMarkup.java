package com.aurorion.diario.markup;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A ponte entre o texto do editor do jogo e o documento do diário (o mesmo do site).
 *
 * <p>No jogo, a pessoa escreve num campo de texto com uma marcação curta:
 * <pre>
 * ## Título             ### Subtítulo
 * &gt; citação            - item de lista
 * ---                   (separador)
 * **negrito**           *itálico*
 * [[imagem:12|legenda]] (imagem vinda do site — preservada, nunca apagada ao salvar)
 * </pre>
 * Linha em branco separa parágrafos; quebra simples continua dentro do parágrafo. Uma barra
 * invertida no começo da linha ({@code \# texto}) ou antes de um asterisco ({@code \*}) torna o
 * caractere literal.
 *
 * <p>O documento canônico é o de blocos: o backend valida tudo de novo. Esta classe só traduz, nos
 * dois sentidos, e a ida e volta preserva o que o editor do jogo sabe representar.
 */
public final class DiaryMarkup {
    public static final int DOCUMENT_VERSION = 1;
    public static final int MAX_MARKUP_CHARS = 40_000;

    private DiaryMarkup() {
    }

    // ── Marcação → documento ────────────────────────────────────────────────────

    public static JsonObject toDocument(String markup) {
        JsonArray blocks = new JsonArray();
        String text = markup == null ? "" : markup.replace("\r\n", "\n").replace('\r', '\n');
        if (text.length() > MAX_MARKUP_CHARS) text = text.substring(0, MAX_MARKUP_CHARS);
        String[] lines = text.split("\n", -1);

        List<String> paragraph = new ArrayList<>();
        List<String> quote = new ArrayList<>();
        JsonArray list = null;
        for (String raw : lines) {
            String line = stripTrailing(raw);
            LineKind kind = kindOf(line);
            if (kind != LineKind.PARAGRAPH || line.isBlank()) flush(paragraph, blocks, "paragraph");
            if (kind != LineKind.QUOTE) flush(quote, blocks, "quote");
            if (kind != LineKind.LIST && list != null) {
                blocks.add(listBlock(list));
                list = null;
            }
            switch (kind) {
                case HEADING2 -> blocks.add(textBlock("heading", 2, line.substring(3)));
                case HEADING3 -> blocks.add(textBlock("heading", 3, line.substring(4)));
                case QUOTE -> quote.add(line.startsWith("> ") ? line.substring(2) : line.substring(1));
                case LIST -> {
                    if (list == null) list = new JsonArray();
                    if (list.size() < 100) list.add(runs(line.substring(2)));
                }
                case DIVIDER -> blocks.add(simple("divider"));
                case IMAGE -> {
                    JsonObject image = imageBlock(line);
                    if (image != null) blocks.add(image);
                }
                case PARAGRAPH -> {
                    if (!line.isBlank()) paragraph.add(unescapeLineStart(line));
                }
            }
        }
        flush(paragraph, blocks, "paragraph");
        flush(quote, blocks, "quote");
        if (list != null) blocks.add(listBlock(list));

        JsonObject doc = new JsonObject();
        doc.addProperty("document_version", DOCUMENT_VERSION);
        doc.add("blocks", blocks);
        return doc;
    }

    private enum LineKind { HEADING2, HEADING3, QUOTE, LIST, DIVIDER, IMAGE, PARAGRAPH }

    private static LineKind kindOf(String line) {
        if (line.startsWith("### ")) return LineKind.HEADING3;
        if (line.startsWith("## ")) return LineKind.HEADING2;
        if (line.startsWith(">")) return LineKind.QUOTE;
        if (line.startsWith("- ") || line.startsWith("• ")) return LineKind.LIST;
        if (line.equals("---") || line.equals("———")) return LineKind.DIVIDER;
        if (line.startsWith("[[imagem:") && line.endsWith("]]")) return LineKind.IMAGE;
        return LineKind.PARAGRAPH;
    }

    /**
     * A barra no começo da linha só é "escape de linha" quando protege uma marcação de bloco
     * ({@code \## não é título}). Fora disso ela pertence à etapa inline ({@code \*} ou uma barra
     * literal escrita como duas) e fica onde está.
     */
    private static String unescapeLineStart(String line) {
        if (!line.startsWith("\\")) return line;
        String rest = line.substring(1);
        return kindOf(rest) != LineKind.PARAGRAPH ? rest : line;
    }

    private static void flush(List<String> lines, JsonArray blocks, String type) {
        if (lines.isEmpty()) return;
        blocks.add(textBlock(type, 0, String.join("\n", lines)));
        lines.clear();
    }

    private static JsonObject simple(String type) {
        JsonObject block = new JsonObject();
        block.addProperty("type", type);
        return block;
    }

    private static JsonObject textBlock(String type, int level, String text) {
        JsonObject block = simple(type);
        if (level > 0) block.addProperty("level", level);
        block.add("runs", runs(text));
        return block;
    }

    private static JsonObject listBlock(JsonArray items) {
        JsonObject block = simple("list");
        block.add("items", items);
        return block;
    }

    /** {@code [[imagem:12|legenda|descrição]]}; id inválido descarta a linha (o backend recusaria). */
    private static JsonObject imageBlock(String line) {
        String inner = line.substring("[[imagem:".length(), line.length() - 2);
        String[] parts = inner.split("\\|", 3);
        long id;
        try {
            id = Long.parseLong(parts[0].trim());
        } catch (NumberFormatException e) {
            return null;
        }
        if (id <= 0) return null;
        JsonObject block = simple("image");
        block.addProperty("attachment_id", id);
        if (parts.length > 1 && !parts[1].isBlank()) block.addProperty("caption", parts[1].trim());
        if (parts.length > 2 && !parts[2].isBlank()) block.addProperty("alt", parts[2].trim());
        return block;
    }

    // ── Marcas inline ───────────────────────────────────────────────────────────

    private enum Token { TEXT, BOLD, ITALIC }

    private record Piece(Token token, String text) {
    }

    /** Converte {@code **negrito**} e {@code *itálico*} em trechos. Marca sem par vira texto literal. */
    static JsonArray runs(String text) {
        List<Piece> pieces = tokenize(text);
        // Pareia as marcas; a que sobra sem par volta a ser texto.
        int openBold = -1;
        int openItalic = -1;
        boolean[] literal = new boolean[pieces.size()];
        for (int i = 0; i < pieces.size(); i++) {
            Token token = pieces.get(i).token();
            if (token == Token.BOLD) openBold = openBold < 0 ? i : -1;
            if (token == Token.ITALIC) openItalic = openItalic < 0 ? i : -1;
        }
        if (openBold >= 0) literal[openBold] = true;
        if (openItalic >= 0) literal[openItalic] = true;

        JsonArray out = new JsonArray();
        StringBuilder current = new StringBuilder();
        boolean bold = false;
        boolean italic = false;
        for (int i = 0; i < pieces.size(); i++) {
            Piece piece = pieces.get(i);
            if (piece.token() == Token.TEXT || literal[i]) {
                current.append(piece.text());
                continue;
            }
            emit(out, current, bold, italic);
            if (piece.token() == Token.BOLD) bold = !bold;
            else italic = !italic;
        }
        emit(out, current, bold, italic);
        return out;
    }

    private static List<Piece> tokenize(String text) {
        List<Piece> pieces = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length() && (text.charAt(i + 1) == '*' || text.charAt(i + 1) == '\\')) {
                buf.append(text.charAt(++i));
                continue;
            }
            if (c == '*') {
                if (!buf.isEmpty()) {
                    pieces.add(new Piece(Token.TEXT, buf.toString()));
                    buf.setLength(0);
                }
                boolean doubled = i + 1 < text.length() && text.charAt(i + 1) == '*';
                pieces.add(doubled ? new Piece(Token.BOLD, "**") : new Piece(Token.ITALIC, "*"));
                if (doubled) i++;
                continue;
            }
            buf.append(c);
        }
        if (!buf.isEmpty()) pieces.add(new Piece(Token.TEXT, buf.toString()));
        return pieces;
    }

    private static void emit(JsonArray out, StringBuilder text, boolean bold, boolean italic) {
        if (text.isEmpty()) return;
        JsonObject run = new JsonObject();
        run.addProperty("text", text.toString());
        if (bold || italic) {
            JsonArray marks = new JsonArray();
            if (bold) marks.add("bold");
            if (italic) marks.add("italic");
            run.add("marks", marks);
        }
        out.add(run);
        text.setLength(0);
    }

    // ── Documento → marcação ────────────────────────────────────────────────────

    public static String toMarkup(JsonObject document) {
        if (document == null || !(document.get("blocks") instanceof JsonArray blocks)) return "";
        List<String> out = new ArrayList<>();
        for (JsonElement element : blocks) {
            if (!(element instanceof JsonObject block)) continue;
            String type = string(block, "type");
            switch (type) {
                case "heading" -> out.add((intOf(block, "level") == 3 ? "### " : "## ") + flat(markRuns(block.get("runs"))));
                case "quote" -> out.add(prefixLines(markRuns(block.get("runs")), "> "));
                case "list" -> {
                    List<String> items = new ArrayList<>();
                    if (block.get("items") instanceof JsonArray array) {
                        for (JsonElement item : array) items.add("- " + flat(markRuns(item)));
                    }
                    out.add(String.join("\n", items));
                }
                case "divider" -> out.add("---");
                case "image" -> out.add("[[imagem:" + longOf(block, "attachment_id")
                        + "|" + safeImageText(string(block, "caption")) + "|" + safeImageText(string(block, "alt")) + "]]");
                default -> {
                    String text = markRuns(block.get("runs"));
                    if (!text.isEmpty()) out.add(escapeLineStarts(text));
                }
            }
        }
        return String.join("\n\n", out);
    }

    private static String markRuns(JsonElement runs) {
        if (!(runs instanceof JsonArray array)) return "";
        StringBuilder out = new StringBuilder();
        for (JsonElement element : array) {
            if (!(element instanceof JsonObject run)) continue;
            String text = string(run, "text").replace("\\", "\\\\").replace("*", "\\*");
            boolean bold = false;
            boolean italic = false;
            if (run.get("marks") instanceof JsonArray marks) {
                for (JsonElement mark : marks) {
                    bold |= "bold".equals(mark.getAsString());
                    italic |= "italic".equals(mark.getAsString());
                }
            }
            String open = (bold ? "**" : "") + (italic ? "*" : "");
            String close = (italic ? "*" : "") + (bold ? "**" : "");
            out.append(open).append(text).append(close);
        }
        return out.toString();
    }

    /** Linha de parágrafo que começaria com marcação de bloco ganha a barra que a torna literal. */
    private static String escapeLineStarts(String text) {
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (kindOf(line) != LineKind.PARAGRAPH) lines[i] = "\\" + line;
        }
        return String.join("\n", lines);
    }

    private static String prefixLines(String text, String prefix) {
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) lines[i] = prefix + lines[i];
        return String.join("\n", lines);
    }

    /** Título e item de lista ocupam uma linha só. */
    private static String flat(String text) {
        return text.replace('\n', ' ');
    }

    private static String safeImageText(String text) {
        return text.replace("|", "/").replace("]]", "]").replace('\n', ' ');
    }

    private static String string(JsonObject object, String member) {
        JsonElement value = object.get(member);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
    }

    private static int intOf(JsonObject object, String member) {
        JsonElement value = object.get(member);
        return value != null && value.isJsonPrimitive() ? value.getAsInt() : 0;
    }

    private static long longOf(JsonObject object, String member) {
        JsonElement value = object.get(member);
        return value != null && value.isJsonPrimitive() ? value.getAsLong() : 0L;
    }

    private static String stripTrailing(String line) {
        int end = line.length();
        while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) end--;
        return line.substring(0, end);
    }
}
