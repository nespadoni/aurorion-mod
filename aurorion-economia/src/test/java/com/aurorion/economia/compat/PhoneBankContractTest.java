package com.aurorion.economia.compat;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Confere, contra o jar instalado do telefone, tudo que a ponte do banco alcanca por reflexao ou
 * por mixin.
 *
 * <p>Existe porque o custo de errar aqui e alto e silencioso: reflexao e {@code @Pseudo} nao quebram
 * a compilacao, quebram em producao. Com {@code AURORION_PHONE_JAR} apontando para o jar do pack,
 * uma atualizacao do telefone que mude qualquer uma destas assinaturas derruba o build.</p>
 */
class PhoneBankContractTest {
    private static final String STORE = "com/mattupolis/phone/server/bank/PhoneBankServerStore";
    private static final String PLAYER = "Lnet/minecraft/server/level/ServerPlayer;";

    @Test
    void theBankStoreStillHasEveryHookTheBridgeUses() throws IOException {
        try (ZipFile phone = phoneJar()) {
            ClassNode store = read(phone, STORE);

            assertMethod(store, "getSnapshot", "(" + PLAYER + ")L" + STORE + "$BankSnapshot;");
            assertMethod(store, "transfer",
                    "(" + PLAYER + "Ljava/lang/String;JLjava/lang/String;)L" + STORE + "$ActionResult;");
            assertMethod(store, "recordTransfer", "(" + PLAYER + PLAYER + "JLjava/lang/String;)V");
            assertMethod(store, "syncToPlayer", "(" + PLAYER + ")V");

            assertMethod(read(phone, STORE + "$BankSnapshot"), "<init>", "(ZLjava/lang/String;JLjava/lang/String;)V");
            assertMethod(read(phone, STORE + "$ActionResult"), "<init>", "(ZLjava/lang/String;)V");
        }
    }

    /**
     * A sobrecarga de tres argumentos tem que continuar delegando para a de quatro — e o que faz um
     * hook so cobrir as duas.
     */
    @Test
    void theShortTransferOverloadStillDelegatesToTheLongOne() throws IOException {
        try (ZipFile phone = phoneJar()) {
            var method = read(phone, STORE).methods.stream()
                    .filter(m -> m.name.equals("transfer")
                            && m.desc.equals("(" + PLAYER + "Ljava/lang/String;J)L" + STORE + "$ActionResult;"))
                    .findFirst().orElseThrow();

            boolean delegates = false;
            for (var instruction : method.instructions) {
                if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode call
                        && call.name.equals("transfer")
                        && call.desc.equals("(" + PLAYER + "Ljava/lang/String;JLjava/lang/String;)L" + STORE + "$ActionResult;")) {
                    delegates = true;
                }
            }
            assertTrue(delegates, "transfer(player, alvo, quantia) precisa delegar para a versao com nota");
        }
    }

    /**
     * O app so precisa de mixin porque o telefone recusa sempre. Se um dia ele ganhar economia de
     * verdade, este teste falha e avisa que a ponte virou concorrente em vez de suporte.
     */
    @Test
    void theBankIsStillAStubWithoutTheBridge() throws IOException {
        try (ZipFile phone = phoneJar()) {
            assertTrue(constantPoolContains(read(phone, STORE),
                    "Bank transfers are disabled because no economy integration is installed."),
                    "o telefone deixou de recusar transferencia por conta propria");
        }
    }

    private static boolean constantPoolContains(ClassNode node, String needle) {
        for (var method : node.methods) {
            for (var instruction : method.instructions) {
                if (instruction instanceof org.objectweb.asm.tree.LdcInsnNode ldc
                        && needle.equals(ldc.cst)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void assertMethod(ClassNode node, String name, String descriptor) {
        assertTrue(node.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(descriptor)),
                node.name + "." + name + descriptor);
    }

    private static ClassNode read(ZipFile phone, String internalName) throws IOException {
        var entry = phone.getEntry(internalName + ".class");
        assertNotNull(entry, internalName);
        try (var input = phone.getInputStream(entry)) {
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static ZipFile phoneJar() throws IOException {
        String path = System.getenv("AURORION_PHONE_JAR");
        assumeTrue(path != null && !path.isBlank(),
                "Defina AURORION_PHONE_JAR para validar a versao instalada do telefone.");
        return new ZipFile(path);
    }

}
