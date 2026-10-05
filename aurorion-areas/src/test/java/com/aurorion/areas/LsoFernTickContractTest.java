package com.aurorion.areas;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Confere, contra o jar do LSO instalado, o formato do {@code randomTick} das samambaias que o
 * {@code FernTickGuardMixin} protege. O mixin usa {@code require = 0} para nao travar o boot; sem
 * este teste, uma versao nova do LSO tiraria a protecao em silencio e o crash do Crop Critters voltaria.
 *
 * <pre>{@code
 * AURORION_LSO_JAR=".../mods/legendarysurvivaloverhaul-1.21.1-2.4.7.2.jar" ./gradlew :aurorion-areas:test
 * }</pre>
 */
class LsoFernTickContractTest {
    private static final String CROP_BLOCK = "net/minecraft/world/level/block/CropBlock";
    private static final String RANDOM_TICK_DESC = "(Lnet/minecraft/world/level/block/state/BlockState;"
            + "Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V";
    private static final List<String> FERNS = List.of(
            "sfiomn/legendarysurvivaloverhaul/common/blocks/SunFernBlock",
            "sfiomn/legendarysurvivaloverhaul/common/blocks/IceFernBlock");

    @Test void bothFernsStillGrowThroughCropBlockRandomTick() throws IOException {
        try (ZipFile jar = lsoJar()) {
            for (String fern : FERNS) {
                ClassNode type = read(jar, fern);
                assertEquals(CROP_BLOCK, type.superName, fern + " deixou de estender CropBlock");
                MethodNode tick = type.methods.stream()
                        .filter(m -> m.name.equals("randomTick") && m.desc.equals(RANDOM_TICK_DESC))
                        .findFirst().orElse(null);
                assertNotNull(tick, fern + ".randomTick sumiu ou mudou de assinatura — o mixin ficou sem alvo");
                assertEquals(1, superRandomTickCalls(tick),
                        fern + ".randomTick nao chama mais super.randomTick uma vez — o ponto do mixin mudou");
            }
        }
    }

    private static long superRandomTickCalls(MethodNode method) {
        long calls = 0;
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL
                    && call.owner.equals(CROP_BLOCK) && call.name.equals("randomTick")
                    && call.desc.equals(RANDOM_TICK_DESC)) {
                calls++;
            }
        }
        return calls;
    }

    private static ClassNode read(ZipFile jar, String name) throws IOException {
        var entry = jar.getEntry(name + ".class");
        assertNotNull(entry, name + " nao esta no jar do LSO");
        try (var input = jar.getInputStream(entry)) {
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    private static ZipFile lsoJar() throws IOException {
        String path = System.getenv("AURORION_LSO_JAR");
        assumeTrue(path != null && !path.isBlank(),
                "Defina AURORION_LSO_JAR para validar a trava das samambaias contra a versao instalada do LSO.");
        return new ZipFile(path);
    }
}
