package com.aurorion.essentials.streamer;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Falha na build se o alvo client-only mudar de assinatura, antes de instalar no pack. */
class StreamerMixinTargetTest {
    @Test void systemChatEntryPointHasTheExactMixinSignature() throws IOException {
        try (InputStream bytecode = getClass().getClassLoader()
                .getResourceAsStream("net/minecraft/client/multiplayer/chat/ChatListener.class")) {
            assertNotNull(bytecode, "ChatListener precisa estar no classpath do teste");
            ClassNode node = new ClassNode();
            new ClassReader(bytecode).accept(node, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
            long matches = node.methods.stream().filter(method -> method.name.equals("handleSystemMessage")
                    && method.desc.equals("(Lnet/minecraft/network/chat/Component;Z)V")).count();
            assertEquals(1L, matches, "StreamerChatListenerMixin depende apenas do caminho SYSTEM, nao do chat assinado");
        }
    }
}
