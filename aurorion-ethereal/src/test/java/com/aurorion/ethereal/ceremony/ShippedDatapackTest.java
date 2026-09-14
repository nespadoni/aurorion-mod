package com.aurorion.ethereal.ceremony;

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
 * <p>Existe por causa de uma familia de falhas que nao aparece em compilacao nenhuma: o que liga uma
 * casa a uma cor, a um item e a uma posicao na lista e texto dentro de um arquivo. Um item que nao
 * existe compila, sobe o servidor, e so aparece no meio do Rito de Vinculacao de alguem, ao vivo, na
 * forma de uma folha de papel girando no lugar do brasao. Aqui isso quebra o build.
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
 * casa nova entra na validacao sozinha.
 */
class ShippedDatapackTest {
    private static final Path DATA = Path.of("src/main/resources/data/aurorion_ethereal/aurorion");

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

    /**
     * O icone deixou de ser enfeite de tela quando virou o <b>simbolo do Rito de Vinculacao</b>: e
     * ele que gira acima da pessoa nos treze segundos da cena. Casa sem icone cai no item generico,
     * e a cerimonia inteira fica com uma folha de papel flutuando no lugar do brasao.
     */
    @Test
    void everyHouseDeclaresASymbol() throws IOException {
        for (Path file : jsonFiles("houses")) {
            JsonObject house = read(file);
            assertTrue(house.has("icon"), idOf(file) + " nao declara icon — sem simbolo no rito");

            String icon = house.get("icon").getAsString();
            assertTrue(icon.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"),
                    idOf(file) + " tem icon invalido: " + icon);
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
