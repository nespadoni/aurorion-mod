package com.aurorion.essentials.death;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * O alvo do {@code DeathListenerGuardMixin}, conferido no bytecode do NeoForge desta build.
 *
 * <p>O guard e de proposito {@code require = 0}: se o alvo sumir, o jogo sobe sem a rede em vez de
 * nao subir. O preco disso e que a perda passa despercebida — e a rede so faz falta no dia em que um
 * mod do pack lanca excecao no {@code LivingDeathEvent} e o servidor cai com alguem preso em zero de
 * vida (foi o que o {@code jonesbounty} fez em 23/09/2026). Este teste e quem avisa: atualizou o
 * NeoForge e ele ficou vermelho, o guard parou de existir.
 */
class DeathGuardTargetTest {
    private static final String COMMON_HOOKS = "net.neoforged.neoforge.common.CommonHooks";
    private static final String EVENT_BUS = "net/neoforged/bus/api/IEventBus";
    private static final String POST = "post";
    private static final String POST_DESCRIPTOR = "(Lnet/neoforged/bus/api/Event;)Lnet/neoforged/bus/api/Event;";

    @Test
    void theDeathEventIsStillPostedFromCommonHooks() throws IOException {
        MethodNode method = methodOf(COMMON_HOOKS, "onLivingDeath");
        assertNotNull(method, "CommonHooks#onLivingDeath nao existe mais nesta versao do NeoForge");

        int found = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && EVENT_BUS.equals(call.owner)
                    && POST.equals(call.name)
                    && POST_DESCRIPTOR.equals(call.desc)) {
                found++;
            }
        }
        assertEquals(1, found,
                "CommonHooks#onLivingDeath precisa ter exatamente um IEventBus#post para o "
                        + "DeathListenerGuardMixin envolver em try/catch. Zero = a rede de seguranca da morte "
                        + "sumiu em silencio; mais de um = o @Redirect pegaria a chamada errada.");
    }

    private static MethodNode methodOf(String className, String methodName) throws IOException {
        String resource = className.replace('.', '/') + ".class";
        try (InputStream bytecode = DeathGuardTargetTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(bytecode, "classe fora do classpath de teste: " + className);
            ClassNode node = new ClassNode();
            new ClassReader(bytecode).accept(node, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
            for (MethodNode method : node.methods) {
                if (method.name.equals(methodName)) return method;
            }
            return null;
        }
    }
}
