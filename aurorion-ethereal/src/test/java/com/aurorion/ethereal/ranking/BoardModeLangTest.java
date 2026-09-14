package com.aurorion.ethereal.ranking;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Amarra cada meta do projetor as traducoes dela.
 *
 * <p>O enum monta as chaves por concatenacao ({@code "gui.aurorion_ethereal.mode." + id}), entao um
 * modo novo compila sem nenhuma traducao e so aparece em jogo — como uma linha crua de chave no meio
 * do holograma, que e o pior lugar para descobrir. Aqui isso quebra o build.
 */
class BoardModeLangTest {
    private static final Path LANG = Path.of("src/main/resources/assets/aurorion_ethereal/lang");

    @Test
    void everyModeHasANameAndALineFormat() throws IOException {
        JsonObject ptBr = read("pt_br.json");

        for (BoardMode mode : BoardMode.values()) {
            String nameKey = "gui.aurorion_ethereal.mode." + mode.id();
            String lineKey = "aurorion_ethereal.board.line." + mode.id();

            assertTrue(ptBr.has(nameKey), "Falta a traducao " + nameKey);
            assertTrue(ptBr.has(lineKey), "Falta a traducao " + lineKey);

            // A linha recebe posicao, nome e valor, nessa ordem — indices explicitos porque a ordem
            // das palavras muda de idioma para idioma.
            String format = ptBr.get(lineKey).getAsString();
            assertTrue(format.contains("%1$s") && format.contains("%2$s") && format.contains("%3$s"),
                    lineKey + " precisa de %1$s, %2$s e %3$s: " + format);
        }
    }

    @Test
    void everyModeHasAnIcon() {
        for (BoardMode mode : BoardMode.values()) {
            assertTrue(!mode.icon().isBlank(), mode + " sem simbolo para o cabecalho do holograma");
        }
    }

    /** Um idioma com chave a mais que o outro e sempre uma das duas ficando para tras. */
    @Test
    void bothLanguagesCoverTheSameKeys() throws IOException {
        Set<String> ptBr = new TreeSet<>(read("pt_br.json").keySet());
        Set<String> enUs = new TreeSet<>(read("en_us.json").keySet());

        assertEquals(ptBr, enUs, "pt_br e en_us divergiram");
    }

    private static JsonObject read(String file) throws IOException {
        Path path = LANG.resolve(file);
        assertTrue(Files.isRegularFile(path), "Arquivo nao encontrado: " + path.toAbsolutePath());

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
