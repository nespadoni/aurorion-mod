package com.aurorion.profissoes.compat;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Confere, contra o jar do Quality Food instalado, o que o {@link QualityNone} e o
 * {@code QualityUtilsMixin} precisam que continue verdadeiro.
 *
 * <p>Mesmo desenho do {@link FoodSpoilContractTest}: sem {@code AURORION_QUALITY_FOOD_JAR} os testes
 * passam pulados. Para rodar:
 *
 * <pre>{@code
 * AURORION_QUALITY_FOOD_JAR=".../mods/quality_food-1.21.1-2.3.6.jar" ./gradlew :aurorion-profissoes:test
 * }</pre>
 */
class QualityFoodContractTest {
    private static final String QUALITY = "de/cadentem/quality_food/core/codecs/Quality";
    private static final String QUALITY_UTILS = "de/cadentem/quality_food/util/QualityUtils";
    private static final String ICON_RENDERER = "de/cadentem/quality_food/mixin/client/GuiGraphicsMixin";

    /** Sem este metodo, o {@code QualityUtilsMixin} nao tem onde injetar e o jogo nao sobe. */
    @Test void theGetterOurMixinNormalizesStillExists() throws IOException {
        try (ZipFile jar = qualityFoodJar()) {
            ClassNode utils = read(jar, QUALITY_UTILS);
            assertTrue(utils.methods.stream().anyMatch(m -> m.name.equals("getQuality")
                            && m.desc.equals("(Lnet/minecraft/world/item/ItemStack;)L" + QUALITY + ";")),
                    "QualityUtils.getQuality(ItemStack) sumiu ou mudou de assinatura");
            assertTrue(utils.methods.stream().anyMatch(m -> m.name.equals("applyQuality")
                            && m.desc.equals("(Lnet/minecraft/world/item/ItemStack;Ljava/util/Collection;"
                            + "Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/RegistryAccess;)V")),
                    "QualityUtils.applyQuality(ItemStack, Collection, Player, RegistryAccess) sumiu");
        }
    }

    @Test void theConstantsWeCanonicalizeStillExist() throws IOException {
        try (ZipFile jar = qualityFoodJar()) {
            ClassNode quality = read(jar, QUALITY);
            for (String field : new String[]{"NONE", "PLAYER_PLACED"}) {
                assertTrue(quality.fields.stream().anyMatch(f -> f.name.equals(field)), "Quality." + field + " sumiu");
            }
        }
    }

    /**
     * O motivo do remendo: o icone so e pulado quando a qualidade e o NONE <b>pela referencia</b>. Se uma
     * versao nova passar a comparar por {@code equals} (ou pelo tipo), este teste falha — e a falha e a
     * boa noticia: o {@code aurorion$canonicalNone} pode sair.
     */
    @Test void theIconRendererStillComparesNoneByReference() throws IOException {
        try (ZipFile jar = qualityFoodJar()) {
            MethodNode render = read(jar, ICON_RENDERER).methods.stream()
                    .filter(m -> m.name.contains("renderIcon")).findFirst().orElse(null);
            assertNotNull(render, "O desenho do icone de qualidade mudou de lugar; reveja o QualityNone");
            boolean byReference = false;
            for (AbstractInsnNode instruction : render.instructions) {
                if (instruction instanceof FieldInsnNode field && field.owner.equals(QUALITY) && field.name.equals("NONE")
                        && field.getNext() != null && field.getNext().getOpcode() == Opcodes.IF_ACMPNE) {
                    byReference = true;
                }
            }
            assertTrue(byReference, "O Quality Food deixou de comparar NONE por referencia: o bug parece corrigido"
                    + " na origem. Confira em jogo e, se for o caso, apague o aurorion$canonicalNone.");
        }
    }

    private static ClassNode read(ZipFile jar, String name) throws IOException {
        var entry = jar.getEntry(name + ".class");
        assertNotNull(entry, name + " nao esta no jar do Quality Food");
        try (var input = jar.getInputStream(entry)) {
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static ZipFile qualityFoodJar() throws IOException {
        String path = System.getenv("AURORION_QUALITY_FOOD_JAR");
        assumeTrue(path != null && !path.isBlank(),
                "Defina AURORION_QUALITY_FOOD_JAR para validar o remendo contra a versao instalada do Quality Food.");
        return new ZipFile(path);
    }
}
