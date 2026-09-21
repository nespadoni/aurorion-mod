package com.aurorion.profissoes.compat;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifica, contra o jar do FoodSpoil instalado, o que o remendo de descongelamento
 * ({@link FoodThaw}) precisa que continue verdadeiro.
 *
 * <p>Mesmo desenho do {@code PhoneMixinContractTest} do {@code aurorion-essentials}: o jar nao e
 * versionado no repo, entao sem {@code AURORION_FOODSPOIL_JAR} apontando para ele estes testes passam
 * <b>pulados</b> em vez de falharem. Para rodar:
 *
 * <pre>{@code
 * AURORION_FOODSPOIL_JAR=".../mods/foodspoil-neoforge-1.21.1-1.1.7.jar" ./gradlew :aurorion-profissoes:test
 * }</pre>
 */
class FoodSpoilContractTest {
    private static final String FOOD_DATA = "com/elcuruxa/foodspoil/data/FoodData";
    private static final String FREEZING_HANDLER = "com/elcuruxa/foodspoil/events/FreezingHandler";

    /** Sem este metodo, o {@code FoodThawMixin} nao tem onde injetar e o servidor nao sobe. */
    @Test void theStateSetterOurPatchHooksStillExists() throws IOException {
        try (ZipFile jar = foodSpoilJar()) {
            MethodNode setState = method(read(jar, FOOD_DATA), "setState");
            assertNotNull(setState, "FoodData.setState sumiu — o FoodThawMixin ficou sem alvo");
            assertEquals("(Lnet/minecraft/world/item/ItemStack;Lcom/elcuruxa/foodspoil/data/FreshnessState;)V",
                    setState.desc, "a assinatura de setState mudou");
        }
    }

    /** Escrevemos e lemos estas chaves de NBT direto; renomear qualquer uma quebra o remendo em silencio. */
    @Test void theNbtKeysOurPatchWritesAreStillTheSame() throws IOException {
        try (ZipFile jar = foodSpoilJar()) {
            List<String> constants = stringConstants(read(jar, FOOD_DATA));
            for (String key : List.of("FoodState", "SnapshotFreshness", "SnapshotTime")) {
                assertTrue(constants.contains(key), "FoodData nao usa mais a chave NBT '" + key + "'");
            }
        }
    }

    /**
     * O remendo existe porque o descongelamento <b>nao</b> reancora o snapshot. Se uma versao nova do
     * FoodSpoil passar a fazer isso sozinha, este teste falha — e essa falha e a boa noticia: e o
     * aviso de que {@code fixFoodSpoilThaw} pode ser desligado e o remendo, apagado.
     *
     * <p>Um teste que verifica que um bug de terceiro continua existindo parece estranho, mas o
     * contrario e pior: um remendo que ninguem lembra de tirar vira codigo que sobrevive ao problema
     * que o justificava.
     */
    @Test void theThawPathStillDoesNotReanchorTheSnapshotOnItsOwn() throws IOException {
        try (ZipFile jar = foodSpoilJar()) {
            MethodNode thawing = method(read(jar, FREEZING_HANDLER), "processContainerThawing");
            assertNotNull(thawing, "processContainerThawing sumiu — reveja o FoodThaw contra a versao nova");
            assertFalse(calls(thawing, "saveSnapshot"),
                    "O FoodSpoil passou a salvar snapshot ao descongelar: o bug parece corrigido na origem."
                            + " Confira em jogo e, se for o caso, desligue fixFoodSpoilThaw e apague o FoodThawMixin.");
        }
    }

    // --- Leitura do jar --------------------------------------------------------------------------

    private static boolean calls(MethodNode method, String name) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.name.equals(name)) return true;
        }
        return false;
    }

    private static List<String> stringConstants(ClassNode type) {
        return type.methods.stream()
                .flatMap(method -> java.util.Arrays.stream(method.instructions.toArray()))
                .filter(LdcInsnNode.class::isInstance)
                .map(instruction -> ((LdcInsnNode) instruction).cst)
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }

    private static MethodNode method(ClassNode type, String name) {
        return type.methods.stream().filter(candidate -> candidate.name.equals(name)).findFirst().orElse(null);
    }

    private static ClassNode read(ZipFile jar, String name) throws IOException {
        var entry = jar.getEntry(name + ".class");
        assertNotNull(entry, name + " nao esta no jar do FoodSpoil");
        try (var input = jar.getInputStream(entry)) {
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static ZipFile foodSpoilJar() throws IOException {
        String path = System.getenv("AURORION_FOODSPOIL_JAR");
        assumeTrue(path != null && !path.isBlank(),
                "Defina AURORION_FOODSPOIL_JAR para validar o remendo contra a versao instalada do FoodSpoil.");
        return new ZipFile(path);
    }
}
