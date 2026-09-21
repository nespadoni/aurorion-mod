package com.aurorion.areas;

import com.aurorion.areas.compat.LsoThirstCompat;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Confere, contra o jar do Legendary Survival Overhaul instalado, tudo o que a ponte de agua pura
 * alcanca por reflexao ou por {@code @Pseudo} — ou seja, tudo o que o compilador <b>nao</b> confere.
 *
 * <p>As duas formas de quebrar sao diferentes e as duas sao ruins: o {@code @Inject} do
 * {@code CanteenFillMixin} com {@code defaultRequire: 1} <b>derruba o servidor no boot</b> se perder o
 * alvo, e a reflexao do {@link LsoThirstCompat} falha em silencio, deixando a agua da Academia normal
 * sem ninguem perceber ate alguem passar sede. Aqui as duas viram teste vermelho.
 *
 * <p>Pulado sem {@code AURORION_LSO_JAR}, como o contrato do telefone no {@code aurorion-essentials}:
 *
 * <pre>{@code
 * AURORION_LSO_JAR=".../mods/legendarysurvivaloverhaul-1.21.1-2.4.7.2.jar" ./gradlew :aurorion-areas:test
 * }</pre>
 */
class LsoThirstContractTest {
    private static final String CANTEEN = "sfiomn/legendarysurvivaloverhaul/common/items/drink/CanteenItem";
    private static final String LARGE_CANTEEN = "sfiomn/legendarysurvivaloverhaul/common/items/drink/LargeCanteenItem";
    private static final String THIRST_UTIL = "sfiomn/legendarysurvivaloverhaul/api/thirst/ThirstUtil";
    private static final String HYDRATION = "sfiomn/legendarysurvivaloverhaul/api/thirst/HydrationEnum";

    /** Alvo do mixin. Perder isto nao degrada: o servidor nao sobe. */
    @Test void theFillMethodTheMixinTargetsStillExists() throws IOException {
        try (ZipFile jar = lsoJar()) {
            MethodNode fill = method(read(jar, CANTEEN), "fill");
            assertNotNull(fill, "CanteenItem.fill sumiu — o CanteenFillMixin ficou sem alvo");
            assertEquals("(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;)V", fill.desc,
                    "a assinatura de CanteenItem.fill mudou");
        }
    }

    /** Os tres metodos e os dois valores que a ponte alcanca por reflexao. */
    @Test void theThirstApiWeReachByReflectionIsStillThere() throws IOException {
        try (ZipFile jar = lsoJar()) {
            ClassNode thirst = read(jar, THIRST_UTIL);
            assertSignature(thirst, "getHydrationEnumTag",
                    "(Lnet/minecraft/world/item/ItemStack;)L" + HYDRATION + ";");
            assertSignature(thirst, "setHydrationEnumTag",
                    "(Lnet/minecraft/world/item/ItemStack;L" + HYDRATION + ";)V");
            assertSignature(thirst, "getCapacityTag", "(Lnet/minecraft/world/item/ItemStack;)I");

            List<String> constants = read(jar, HYDRATION).fields.stream().map(field -> field.name).toList();
            for (String name : List.of("NORMAL", "PURIFIED")) {
                assertTrue(constants.contains(name), "HydrationEnum." + name + " nao existe mais");
            }
        }
    }

    /**
     * O cantil grande e alcancado de graca por herdar {@code fill}. Se ele passar a sobrescrever,
     * a Academia purificaria o cantil pequeno e nao o grande — diferenca que so aparece em jogo.
     */
    @Test void theLargeCanteenStillInheritsFillInsteadOfOverridingIt() throws IOException {
        try (ZipFile jar = lsoJar()) {
            ClassNode large = read(jar, LARGE_CANTEEN);
            assertEquals(CANTEEN, large.superName, "LargeCanteenItem deixou de estender CanteenItem");
            assertNull(method(large, "fill"),
                    "LargeCanteenItem passou a sobrescrever fill — o mixin precisa alcancar os dois");
        }
    }

    /** As regras sao escritas por humano no comando; um espaco a mais vira uma regra que nunca casa. */
    @Test void theRuleNamesAreValidAreaRuleKeys() {
        for (String rule : List.of(LsoThirstCompat.PURE_WATER, LsoThirstCompat.PURE_WATER_TAP)) {
            assertTrue(rule.matches("[a-z][a-z0-9_:.-]{0,79}"), rule);
        }
        assertNotEquals(LsoThirstCompat.PURE_WATER, LsoThirstCompat.PURE_WATER_TAP);
    }

    /**
     * A tag de torneiras so vale para blocos que o LSO de fato enche: a integracao dele conhece
     * {@code KitchenSinkBlockEntity} e {@code BasinBlockEntity}, e mais nada. Uma pia de outro mod na
     * tag nao daria erro — ela simplesmente nunca encheria cantil, e a staff ficaria procurando o
     * motivo de a agua sair normal.
     */
    @Test void theShippedTapTagOnlyListsBlocksTheLsoCanActuallyFillFrom() throws IOException {
        JsonObject tag = JsonParser.parseString(Files.readString(
                Path.of("src/main/resources/data/aurorion_areas/tags/block/water_taps.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray values = tag.getAsJsonArray("values");
        assertFalse(values.isEmpty(), "a tag vem vazia: a regra agua_pura_pia nao purificaria nada");
        for (var element : values) {
            JsonObject entry = element.getAsJsonObject();
            String id = entry.get("id").getAsString();
            assertTrue(id.startsWith("refurbished_furniture:"),
                    id + " nao e do Refurbished — o LSO nao enche cantil nesse bloco");
            assertTrue(id.endsWith("_kitchen_sink"), id + " nao e pia de cozinha");
            assertFalse(entry.get("required").getAsBoolean(),
                    id + " precisa de required:false, senao um pack sem o mod recusa o datapack");
        }
    }

    // --- Leitura do jar --------------------------------------------------------------------------

    private static void assertSignature(ClassNode type, String name, String descriptor) {
        MethodNode found = method(type, name);
        assertNotNull(found, type.name + "." + name + " sumiu");
        assertEquals(descriptor, found.desc, type.name + "." + name + " mudou de assinatura");
    }

    private static MethodNode method(ClassNode type, String name) {
        return type.methods.stream().filter(candidate -> candidate.name.equals(name)).findFirst().orElse(null);
    }

    private static ClassNode read(ZipFile jar, String name) throws IOException {
        var entry = jar.getEntry(name + ".class");
        assertNotNull(entry, name + " nao esta no jar do LSO");
        try (var input = jar.getInputStream(entry)) {
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_FRAMES | ClassReader.SKIP_CODE);
            return node;
        }
    }

    private static ZipFile lsoJar() throws IOException {
        String path = System.getenv("AURORION_LSO_JAR");
        assumeTrue(path != null && !path.isBlank(),
                "Defina AURORION_LSO_JAR para validar a ponte de agua contra a versao instalada do LSO.");
        return new ZipFile(path);
    }
}
