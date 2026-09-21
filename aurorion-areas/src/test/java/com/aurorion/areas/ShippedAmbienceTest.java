package com.aurorion.areas;

import com.aurorion.areas.data.AreaJson;
import com.aurorion.areas.profile.AmbientProfile;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Valida os JSON que o proprio jar entrega, com os {@code Codec} de verdade.
 *
 * <p>Este modulo tem a sorte de poder rodar os codecs num teste comum: {@link AmbientProfile} so usa
 * primitivos e {@code ResourceLocation}, sem tocar em registry do jogo — diferente do
 * {@code aurorion-ethereal}, que precisa cair para Gson porque os componentes de texto puxam meio
 * jogo junto.
 *
 * <p>Por que importa: um ambiente que nao decodifica e <b>ignorado em silencio</b> pelo
 * {@code DatapackRegistry} (uma linha no log e segue o baile). A floresta simplesmente ficaria sem
 * som, sem neblina e sem medo, e ninguem descobriria sem entrar la e esperar. Aqui isso quebra o
 * build.
 */
class ShippedAmbienceTest {
    private static final Path DATA = Path.of("src/main/resources/data/aurorion_areas/aurorion");

    @Test void everyShippedAmbienceDecodes() throws IOException {
        List<Path> files = jsonFiles("area_ambience");
        assertFalse(files.isEmpty(), "nenhum ambiente entregue no jar");
        for (Path file : files) {
            var result = AmbientProfile.codec(idOf(file)).parse(JsonOps.INSTANCE, read(file));
            assertTrue(result.result().isPresent(),
                    file.getFileName() + " nao decodifica: " + result.error().map(Object::toString).orElse(""));
        }
    }

    @Test void everyShippedRulePresetDecodes() throws IOException {
        List<Path> files = jsonFiles("area_rules");
        assertFalse(files.isEmpty(), "nenhum preset de regra entregue no jar");
        for (Path file : files) {
            assertDoesNotThrow(() -> AreaJson.readRules(read(file)), file.getFileName().toString());
        }
    }

    /**
     * A Floresta Negra e o unico ambiente com escalada, e os numeros dela sao a experiencia de jogo
     * — se alguem zerar um campo sem querer, a floresta vira um passeio e o teste nao diria nada se
     * so conferisse "decodifica".
     */
    @Test void theBlackForestStillEscalates() throws IOException {
        Path file = DATA.resolve("area_ambience/floresta_negra.json");
        AmbientProfile forest = AmbientProfile.codec(idOf(file)).parse(JsonOps.INSTANCE, read(file))
                .result().orElseThrow();

        assertTrue(forest.sounds().events().size() >= 20, "a floresta precisa de muitos sons diferentes");
        assertTrue(forest.fog().distance() > 0, "sem neblina nao ha floresta");
        assertTrue(forest.attack().damage() > 0, "sem ataque nao ha o que escalar");

        var dread = forest.dread();
        assertTrue(dread.damageScale() > 1, "o dano tem que crescer com o tempo");
        assertTrue(dread.intervalScale() < 1, "os eventos tem que ficar mais frequentes");
        assertTrue(dread.blindnessBonus() > 0, "a cegueira tem que aumentar");
        assertTrue(dread.fogScale() < 1, "a neblina tem que fechar");
        assertTrue(dread.rampSeconds() >= 60, "uma escalada curta demais vira susto, nao medo");

        // A floresta mata depois de sete minutos, por decisao da administracao. O numero e travado
        // aqui porque ele e o contrato com o sistema de vidas: mexer nele custa vida de jogador.
        assertEquals(420, dread.lethalAfterSeconds(), "a floresta passa a matar aos 7 minutos");
        assertFalse(forest.attack().lethal(), "os primeiros minutos continuam nao letais");
        assertTrue(dread.lethalAfterSeconds() > dread.rampSeconds(),
                "o dano tem que chegar ao auge antes de poder matar, nao junto");

        // Quem modera precisa saber que alguem entrou num lugar que mata — e quando ele virou letal.
        assertTrue(forest.alert().enter(), "a staff precisa ser avisada da entrada");
        assertTrue(forest.alert().lethal(), "a staff precisa ser avisada quando o lugar vira letal");
        assertTrue(forest.alert().exit(), "sem o aviso de saida, a staff nao sabe que acabou");
        assertTrue(forest.alert().cooldownSeconds() > 0,
                "sem carencia, quem fica em cima da fronteira metralha o chat da staff");

        assertTrue(forest.pulse().blackout() > .8F, "o apagao no auge tem que ser um apagao mesmo");

        assertTrue(forest.whispers().lines().size() >= 10, "poucas frases denunciam o sorteio");
        assertNotEquals(forest.whispers().color(), forest.whispers().peakColor(),
                "a cor precisa mudar com o medo, senao peak_color nao faz nada");
    }

    /**
     * A tabela de criaturas e o que o jogador encontra, e ela erra em silencio: id de mod que nao
     * esta instalado e ignorado na hora do spawn, entao um erro de digitacao vira "essa criatura
     * nunca aparece" e mais nada. Estas checagens sao o unico lugar onde isso da erro.
     */
    @Test void theBlackForestSpawnsInTiers() throws IOException {
        Path file = DATA.resolve("area_ambience/floresta_negra.json");
        var spawns = AmbientProfile.codec(idOf(file)).parse(JsonOps.INSTANCE, read(file))
                .result().orElseThrow().spawns();

        assertFalse(spawns.entries().isEmpty(), "a floresta precisa povoar");
        assertTrue(spawns.maxNearby() > 0 && spawns.maxNearby() <= 12,
                "sem teto, uma permanencia longa vira um exercito; um teto alto demais vira lag");
        assertTrue(spawns.minDistance() >= 6, "nascer no colo do jogador nao assusta, irrita");
        assertTrue(spawns.startAfterSeconds() > 0, "entrar e sair nao pode povoar a floresta");
        assertTrue(spawns.peakCount() >= spawns.count(), "a leva tem que crescer com o medo, nao encolher");

        for (var entry : spawns.entries()) {
            assertTrue(entry.id().toString().matches("[a-z0-9_.-]+:[a-z0-9_./-]+"), "id invalido: " + entry.id());
            assertFalse(entry.id().getPath().endsWith("_not_despawn"),
                    entry.id() + " nunca desaparece sozinha — ela ficaria acumulando na floresta");
        }
        // Tres camadas: o que aparece ao entrar nao pode ser o mesmo que aparece aos seis minutos.
        assertTrue(spawns.entries().stream().anyMatch(e -> e.from() == 0), "falta o que aparece na entrada");
        assertTrue(spawns.entries().stream().anyMatch(e -> e.from() > 0 && e.from() < .8F), "falta a camada do meio");
        assertTrue(spawns.entries().stream().anyMatch(e -> e.from() >= .7F), "falta o que so aparece no auge");
        assertTrue(spawns.entries().stream().anyMatch(e -> e.id().getNamespace().equals("minecraft")),
                "ao menos uma criatura tem que existir num pack sem mod de monstro nenhum");
    }

    /** Id malformado nao quebra nada na carga: o som some sem aviso. */
    @Test void everyShippedSoundIdIsWellFormed() throws IOException {
        for (Path file : jsonFiles("area_ambience")) {
            AmbientProfile profile = AmbientProfile.codec(idOf(file)).parse(JsonOps.INSTANCE, read(file))
                    .result().orElseThrow();
            for (ResourceLocation id : profile.sounds().events()) {
                assertTrue(id.toString().matches("[a-z0-9_.-]+:[a-z0-9_./-]+"), "som invalido: " + id);
            }
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
    private static ResourceLocation idOf(Path file) {
        String name = file.getFileName().toString();
        return ResourceLocation.fromNamespaceAndPath("aurorion_areas", name.substring(0, name.length() - ".json".length()));
    }
}
