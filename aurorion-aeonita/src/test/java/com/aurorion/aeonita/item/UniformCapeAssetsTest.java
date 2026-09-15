package com.aurorion.aeonita.item;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.GsonHelper;
import org.junit.jupiter.api.Test;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.loading.json.raw.Model;
import software.bernie.geckolib.loading.json.typeadapter.KeyFramesAdapter;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.loading.object.BakedModelFactory;
import software.bernie.geckolib.loading.object.GeometryTree;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

        // O CPM exporta cada 'group' como um cubo de tamanho zero. Eles nao desenham nada, mas
        // o GeckoLib assa seis quads por cubo desses e paga os vertices por frame assim mesmo.
        for (GeoBone bone : baked.topLevelBones()) {
            assertNoEmptyCubes(bone);
        }
    }

    private static void assertNoEmptyCubes(GeoBone bone) {
        bone.getCubes().forEach(cube -> {
            var size = cube.size();
            assertFalse(size.x() == 0 && size.y() == 0 && size.z() == 0,
                    "cubo de tamanho zero sobrou no bone " + bone.getName());
        });
        bone.getChildBones().forEach(UniformCapeAssetsTest::assertNoEmptyCubes);
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
