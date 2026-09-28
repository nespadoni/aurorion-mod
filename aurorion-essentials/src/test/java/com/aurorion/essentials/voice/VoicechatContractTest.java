package com.aurorion.essentials.voice;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * As partes internas do Simple Voice Chat que o {@code VoicechatPlayerNameMixin} e o
 * {@link VoiceNames} usam, conferidas contra o jar que esta no classpath de teste (o de
 * {@code mod-servidor-referencia/}). Se uma atualizacao do Voice Chat mudar isso, o build quebra aqui
 * em vez de os menus dele voltarem a mostrar o nick em silencio.
 */
class VoicechatContractTest {
    @Test
    void theStateIsCreatedInOnePlaceWithAWritableName() throws IOException {
        ClassNode manager = read("de/maxhenkel/voicechat/voice/server/PlayerStateManager");
        assertMethod(manager, "defaultDisconnectedState",
                "(Lnet/minecraft/server/level/ServerPlayer;)Lde/maxhenkel/voicechat/voice/common/PlayerState;", true);
        assertMethod(manager, "getState", "(Ljava/util/UUID;)Lde/maxhenkel/voicechat/voice/common/PlayerState;", false);
        assertMethod(manager, "broadcastState",
                "(Lnet/minecraft/server/level/ServerPlayer;Lde/maxhenkel/voicechat/voice/common/PlayerState;)V", false);

        ClassNode state = read("de/maxhenkel/voicechat/voice/common/PlayerState");
        assertMethod(state, "setName", "(Ljava/lang/String;)V", false);
    }

    private static void assertMethod(ClassNode type, String name, String descriptor, boolean isStatic) {
        assertTrue(type.methods.stream().anyMatch(method -> method.name.equals(name) && method.desc.equals(descriptor)
                        && ((method.access & Opcodes.ACC_STATIC) != 0) == isStatic),
                type.name + " sem " + name + descriptor);
    }

    private static ClassNode read(String internalName) throws IOException {
        try (var input = VoicechatContractTest.class.getResourceAsStream("/" + internalName + ".class")) {
            assertNotNull(input, internalName + " fora do classpath de teste");
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }
}
