package com.aurorion.ethereal.ceremony;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valida os JSON que o proprio jar entrega.
 *
 * <p>Existe por causa de uma falha que nao aparece em compilacao nenhuma: o vinculo entre uma opcao
 * de pergunta e uma casa e um texto dentro de um arquivo. Errar {@code aurorion_ethereal:nix} por
 * {@code nyx} compila, sobe o servidor, e so aparece quando um jogador termina a cerimonia e a
 * contagem vem com uma casa a menos. Aqui isso quebra o build.
 *
 * <p><b>Por que Gson e nao os {@code Codec} de verdade</b>: {@code ComponentSerialization.CODEC}
 * puxa {@code HoverEvent} -> {@code ItemStack} -> registries do jogo, e {@code Bootstrap.bootStrap()}
 * nao roda fora do jogo sob NeoForge (as feature flags pedem a lista de mods do FML, que so existe
 * com o loader no ar). Rodar os codecs aqui exigiria subir meio jogo por causa de um teste de texto.
 * O que este teste cobre e a parte que erra na pratica — ids cruzados entre dois arquivos — e a
 * validacao pelos codecs continua acontecendo na carga do datapack, com o arquivo quebrado sendo
 * pulado e registrado no log.
 *
 * <p>Os arquivos sao lidos do diretorio de fontes, e nao de uma lista escrita a mao: acrescentar uma
 * casa ou uma pergunta nova entra na validacao sozinho.
 */
class ShippedDatapackTest {
    private static final Path DATA = Path.of("src/main/resources/data/aurorion_ethereal/aurorion");
    private static final String NAMESPACE = "aurorion_ethereal";

    @Test
    void shipsFiveHouses() throws IOException {
        assertEquals(5, jsonFiles("houses").size(), "As cinco casas de Ethereal");
    }

    @Test
    void everyHouseHasNameMottoAndItsOwnColor() throws IOException {
        Set<String> colors = new HashSet<>();

        for (Path file : jsonFiles("houses")) {
            JsonObject house = read(file);
            String id = idOf(file);

            assertFalse(house.get("name").getAsString().isBlank(), id + " sem nome");
            assertFalse(house.get("motto").getAsString().isBlank(), id + " sem lema");

            String color = house.get("color").getAsString();
            assertTrue(color.matches("#[0-9A-Fa-f]{6}"), id + " tem cor invalida: " + color);
            assertTrue(colors.add(color.toUpperCase()),
                    id + " repete a cor de outra casa — a cor e a identidade visual da casa");
        }
    }

    @Test
    void everyCeremonyOptionPointsToAnExistingHouse() throws IOException {
        Set<String> known = houseIds();

        for (Path file : jsonFiles("ceremony_questions")) {
            JsonObject question = read(file);
            assertFalse(question.get("question").getAsString().isBlank(), idOf(file) + " sem enunciado");

            JsonArray options = question.getAsJsonArray("options");
            assertTrue(options.size() >= 2, idOf(file) + " precisa de pelo menos duas opcoes");

            for (JsonElement element : options) {
                JsonObject option = element.getAsJsonObject();
                String house = option.get("house").getAsString();

                assertFalse(option.get("text").getAsString().isBlank(),
                        idOf(file) + " tem uma opcao sem texto");
                assertTrue(known.contains(house),
                        idOf(file) + ": a opcao '" + option.get("text").getAsString()
                                + "' aponta para " + house + ", que nao existe");
            }
        }
    }

    /** Uma casa que nenhuma opcao sugere nunca sairia de uma cerimonia — seria inalcancavel. */
    @Test
    void everyHouseIsReachableFromSomeAnswer() throws IOException {
        Set<String> suggested = new HashSet<>();
        for (Path file : jsonFiles("ceremony_questions")) {
            for (JsonElement element : read(file).getAsJsonArray("options")) {
                suggested.add(element.getAsJsonObject().get("house").getAsString());
            }
        }

        for (String house : houseIds()) {
            assertTrue(suggested.contains(house), house + " nao e sugerida por nenhuma resposta");
        }
    }

    /** Ordem repetida deixaria a sequencia das perguntas dependendo do nome do arquivo. */
    @Test
    void questionsHaveDistinctOrder() throws IOException {
        Set<Integer> orders = new HashSet<>();

        for (Path file : jsonFiles("ceremony_questions")) {
            int order = read(file).get("order").getAsInt();
            assertTrue(orders.add(order), idOf(file) + " repete a ordem " + order);
        }
    }

    /**
     * As casas nao podem ficar todas no mesmo lugar da lista: a ordem decide a grade do altar, e um
     * empate deixaria a posicao delas dependendo do nome do arquivo.
     */
    @Test
    void housesHaveDistinctOrder() throws IOException {
        Set<Integer> orders = new HashSet<>();

        for (Path file : jsonFiles("houses")) {
            int order = read(file).get("order").getAsInt();
            assertTrue(orders.add(order), idOf(file) + " repete a ordem " + order);
        }
    }

    // --- Leitura -------------------------------------------------------------------------------

    private static Set<String> houseIds() throws IOException {
        Set<String> ids = new HashSet<>();
        for (Path file : jsonFiles("houses")) {
            ids.add(NAMESPACE + ":" + idOf(file));
        }
        return ids;
    }

    private static JsonObject read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static List<Path> jsonFiles(String directory) throws IOException {
        Path folder = DATA.resolve(directory);
        assertTrue(Files.isDirectory(folder), "Pasta nao encontrada: " + folder.toAbsolutePath());

        try (Stream<Path> files = Files.list(folder)) {
            List<Path> found = new ArrayList<>(files.filter(path -> path.toString().endsWith(".json")).toList());
            found.sort(Path::compareTo);
            return found;
        }
    }

    /** Como no jogo, o id vem do caminho do arquivo e nao do conteudo. */
    private static String idOf(Path file) {
        String name = file.getFileName().toString();
        return name.substring(0, name.length() - ".json".length());
    }
}
