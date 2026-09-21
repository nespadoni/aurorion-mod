package com.aurorion.aeonita.item;

import com.aurorion.aeonita.registry.AeonitaItems;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.GsonHelper;
import org.junit.jupiter.api.Test;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.json.typeadapter.KeyFramesAdapter;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le a geometria e as animacoes da capa com o proprio Gson do GeckoLib e monta o modelo assado,
 * que e exatamente o que o jogo faz ao carregar os assets.
 *
 * <p>Existe porque esses dois arquivos nao foram escritos a mao: vieram de um export do
 * Customizable Player Models convertido por script (bones renomeados, UV reescalada de 16x para
 * o tamanho real da textura, cubos de tamanho zero removidos, animacoes renomeadas). Um erro
 * nessa conversao nao quebra build nem levanta excecao no jogo — a capa so aparece torta, ou
 * parada, ou invisivel, e so em cliente, em cima de um jogador. Nenhum dos comandos de validacao
 * do repo (build, teste, gameTest) chega perto de {@code assets/}, entao sem este teste o unico
 * jeito de saber e abrir o jogo e olhar.
 */
class UniformCapeAssetsTest {
    private static final String NAMESPACE = "aurorion_aeonita";
    private static final String GEO = "/assets/aurorion_aeonita/geo/armor/uniform_cape.geo.json";
    private static final String ANIMATIONS = "/assets/aurorion_aeonita/animations/armor/uniform_cape.animation.json";

    /**
     * Os oito nomes que o {@code GeoArmorRenderer} procura para copiar a pose do modelo humanoide.
     * Quando um nao existe ele devolve {@code null} sem reclamar e o render segue com ele — o erro
     * so aparece como {@code NullPointerException} no meio de um frame, no cliente de quem estiver
     * perto de alguem de capa.
     */
    private static final List<String> ARMOR_BONES = List.of(
            "armorHead", "armorBody", "armorLeftArm", "armorRightArm",
            "armorLeftLeg", "armorRightLeg", "armorLeftBoot", "armorRightBoot");

    @Test
    void geometriaAssaComOsBonesQueOGeckoLibProcura() {
        BakedGeoModel baked = BakedModelFactory.getForNamespace(NAMESPACE)
                .constructGeoModel(GeometryTree.fromModel(readModel()));

        for (String bone : ARMOR_BONES) {
            assertTrue(baked.getBone(bone).isPresent(), "faltou o bone de armadura " + bone);
        }
    }

    @Test
    void naoSobrouCuboDegeneradoDoExportDoCpm() {
        BakedGeoModel baked = BakedModelFactory.getForNamespace(NAMESPACE)
                .constructGeoModel(GeometryTree.fromModel(readModel()));

        // O CPM exporta cada 'group' como um cubo marcador, e a forma dele muda a cada reexport:
        // ja saiu como [0, 0, 0], como a linha [1.5, 0, 0] e como a placa [1.5, 0, 0.5]. Pelo
        // tamanho nao da para separar marcador de geometria, porque a capa usa placas achatadas
        // de proposito (a gola, a perna esquerda). O que separa e a UV: o marcador tem area zero
        // em toda face, entao nao pinta pixel nenhum -- e o GeckoLib assa seis quads por cubo
        // desses e paga os vertices por frame assim mesmo.
        for (GeoBone bone : baked.topLevelBones()) {
            assertNoEmptyCubes(bone);
        }
    }

    private static void assertNoEmptyCubes(GeoBone bone) {
        bone.getCubes().forEach(cube -> assertTrue(
                Arrays.stream(cube.quads()).anyMatch(UniformCapeAssetsTest::pintaAlgumPixel),
                "cubo sem area de UV sobrou no bone " + bone.getName() + ": " + cube.size()));
        bone.getChildBones().forEach(UniformCapeAssetsTest::assertNoEmptyCubes);
    }

    /**
     * Se a face cobre area na textura. O GeckoLib ja dividiu a UV pelo tamanho da textura ao
     * assar, entao aqui basta ver se os quatro vertices nao colapsaram num ponto ou numa linha.
     */
    private static boolean pintaAlgumPixel(GeoQuad quad) {
        if (quad == null) {
            return false;
        }
        float menorU = Float.MAX_VALUE, maiorU = -Float.MAX_VALUE;
        float menorV = Float.MAX_VALUE, maiorV = -Float.MAX_VALUE;
        for (GeoVertex vertice : quad.vertices()) {
            menorU = Math.min(menorU, vertice.texU());
            maiorU = Math.max(maiorU, vertice.texU());
            menorV = Math.min(menorV, vertice.texV());
            maiorV = Math.max(maiorV, vertice.texV());
        }
        return maiorU > menorU && maiorV > menorV;
    }

    /**
     * A ponte entre o Java e o JSON: {@code RawAnimation} guarda o nome como string solta, e um
     * nome que nao existe no arquivo faz o GeckoLib simplesmente nao animar, em silencio.
     */
    @Test
    void asAnimacoesPedidasPelaCapaExistemNoArquivo() {
        BakedAnimations animations = KeyFramesAdapter.GEO_GSON.fromJson(
                GsonHelper.getAsJsonObject(readJson(ANIMATIONS), "animations"), BakedAnimations.class);

        for (RawAnimation raw : List.of(UniformCapeItem.IDLE, UniformCapeItem.WALKING,
                UniformCapeItem.RUNNING, UniformCapeItem.JUMPING)) {
            for (RawAnimation.Stage stage : raw.getAnimationStages()) {
                assertNotNull(animations.getAnimation(stage.animationName()),
                        "a capa pede a animacao '" + stage.animationName() + "', que nao esta no arquivo");
            }
        }
    }

    /**
     * Nenhuma rotacao passa de meia volta.
     *
     * <p>O CPM escreve angulo negativo como 360 menos o valor — 329 no lugar de -31, 362 no lugar
     * de 2. Parado, e a mesma pose, e nada no jogo reclama. O estrago e na TRANSICAO: o GeckoLib
     * mistura duas animacoes interpolando os graus em linha reta, entao sair de {@code running}
     * com o bone em 0 e entrar em {@code jumping} com ele em 329 nao gira 31 graus para tras —
     * gira 329 para a frente, a capa dando a volta inteira em volta do jogador.
     *
     * <p>O conversor normaliza, e este teste e quem percebe se um export novo entrar cru. Nada
     * mais pegaria: o arquivo e JSON valido, o GeckoLib assa sem reclamar, e so aparece em cliente,
     * no meio de um pulo.
     */
    @Test
    void nenhumaRotacaoDaAVoltaInteira() {
        JsonObject animacoes = GsonHelper.getAsJsonObject(readJson(ANIMATIONS), "animations");

        for (Map.Entry<String, JsonElement> animacao : animacoes.entrySet()) {
            JsonObject bones = GsonHelper.getAsJsonObject(
                    animacao.getValue().getAsJsonObject(), "bones", new JsonObject());

            for (Map.Entry<String, JsonElement> bone : bones.entrySet()) {
                JsonElement rotacao = bone.getValue().getAsJsonObject().get("rotation");
                if (rotacao == null) {
                    continue;
                }
                for (JsonArray vetor : vetoresDe(rotacao)) {
                    for (JsonElement grau : vetor) {
                        assertTrue(Math.abs(grau.getAsDouble()) <= 180,
                                "rotacao de " + grau.getAsDouble() + " graus em " + animacao.getKey()
                                        + "/" + bone.getKey() + ": o export do CPM entrou sem normalizar");
                    }
                }
            }
        }
    }

    /**
     * Os vetores de um canal de animacao. O Blockbench escreve o mesmo canal de tres jeitos, e os
     * tres aparecem no mesmo arquivo: lista solta e {@code {"vector": [...]}} quando o valor e
     * fixo, e um keyframe por tempo quando tem movimento.
     */
    private static List<JsonArray> vetoresDe(JsonElement canal) {
        if (canal.isJsonArray()) {
            return List.of(canal.getAsJsonArray());
        }
        JsonObject objeto = canal.getAsJsonObject();
        if (objeto.has("vector")) {
            return List.of(objeto.getAsJsonArray("vector"));
        }

        List<JsonArray> vetores = new ArrayList<>();
        for (Map.Entry<String, JsonElement> quadro : objeto.entrySet()) {
            JsonElement valor = quadro.getValue();
            vetores.add(valor.isJsonArray()
                    ? valor.getAsJsonArray()
                    : valor.getAsJsonObject().getAsJsonArray("vector"));
        }
        return vetores;
    }

    /**
     * O geo e a animacao sao um so para as seis capas, mas o resto e por capa: duas texturas, um
     * modelo de item, duas traducoes e o arquivo de temperatura do Legendary Survival Overhaul.
     * Esquecer um deles nao quebra nada no build -- no jogo a capa aparece roxa e preta, ou com a
     * chave crua no lugar do nome, ou sem resistencia ao frio nenhuma.
     */
    @Test
    void cadaCapaTemTodosOsArquivosDela() {
        JsonObject ptBr = readJson("/assets/" + NAMESPACE + "/lang/pt_br.json");
        JsonObject enUs = readJson("/assets/" + NAMESPACE + "/lang/en_us.json");

        for (String capa : UniformCapeItem.CAPAS) {
            String item = "uniform_cape_" + capa;
            assertResourceExists("/assets/" + NAMESPACE + "/textures/entity/armor/uniform_cape/" + capa + ".png");
            assertResourceExists("/assets/" + NAMESPACE + "/textures/item/" + item + ".png");
            assertResourceExists("/assets/" + NAMESPACE + "/models/item/" + item + ".json");

            String chave = "item." + NAMESPACE + "." + item;
            assertTrue(ptBr.has(chave), "falta a traducao pt_br de " + chave);
            assertTrue(enUs.has(chave), "falta a traducao en_us de " + chave);

            // Sem este arquivo a capa nao aquece nada. Ele e inerte quando o LSO nao esta
            // instalado, entao mora aqui e nao num mod de compatibilidade (SDD secao 3).
            JsonObject temperatura = readJson(
                    "/data/" + NAMESPACE + "/legendarysurvivaloverhaul/temperature/items/" + item + ".json");
            assertTrue(temperatura.get("cold_resistance").getAsFloat() > 0,
                    "a capa " + capa + " nao da resistencia ao frio nenhuma");
        }
    }

    /**
     * Uma capa registrada sem entrar em {@code CAPAS} passaria por todo o teste acima sem ser
     * olhada, que e justamente o caso que ele existe para pegar.
     */
    @Test
    void aListaDeCapasCobreTodasAsRegistradas() {
        long registradas = Arrays.stream(AeonitaItems.class.getDeclaredFields())
                .filter(field -> field.getName().startsWith("UNIFORM_CAPE_"))
                .count();

        assertEquals(registradas, UniformCapeItem.CAPAS.size(),
                "UniformCapeItem.CAPAS e as capas registradas no AeonitaItems se separaram");
    }

    private static void assertResourceExists(String path) {
        try (InputStream stream = UniformCapeAssetsTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "asset nao encontrado no classpath: " + path);
        } catch (java.io.IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Model readModel() {
        return KeyFramesAdapter.GEO_GSON.fromJson(readJson(GEO), Model.class);
    }

    private static JsonObject readJson(String path) {
        try (InputStream stream = UniformCapeAssetsTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "asset nao encontrado no classpath: " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (java.io.IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
