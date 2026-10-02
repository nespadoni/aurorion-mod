package com.aurorion.essentials.tab;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Confere no bytecode do NeoForge desta build o que o {@code PlayerInfoEntryMixin} supoe. Ele mora no
 * {@code aurorion_essentials.mixins.json}, que e obrigatorio: um alvo perdido derruba o servidor no boot.
 * Mesma familia do {@code PrivacyMixinTargetTest}; os alvos sao do Minecraft, entao nunca e pulado.
 */
class TabMixinTargetTest {
    private static final String ENTRY = "net/minecraft/network/protocol/game/ClientboundPlayerInfoUpdatePacket$Entry";
    private static final String CONSTRUCTOR = "(Ljava/util/UUID;Lcom/mojang/authlib/GameProfile;ZILnet/minecraft/world/level/GameType;"
            + "Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/RemoteChatSession$Data;)V";
    /** this=0, UUID=1, GameProfile=2, boolean=3, int=4, GameType=5, Component=6. */
    private static final int DISPLAY_NAME = 6;

    /**
     * O mixin troca o nome no {@code LOAD} do parametro e le {@code profileId()}/{@code profile()} do
     * proprio record. Isso so funciona se o parametro for lido uma vez, e depois de os dois campos ja
     * estarem gravados.
     */
    @Test void theDisplayNameIsLoadedOnceAfterTheProfileFieldsAreSet() throws IOException {
        MethodNode constructor = constructor();
        boolean profileId = false, profile = false;
        int loads = 0;
        for (AbstractInsnNode instruction : constructor.instructions) {
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD) {
                if (field.name.equals("profileId")) profileId = true;
                if (field.name.equals("profile")) profile = true;
            }
            if (instruction instanceof VarInsnNode load && load.getOpcode() == Opcodes.ALOAD && load.var == DISPLAY_NAME) {
                loads++;
                assertTrue(profileId && profile, "displayName e lido antes de profileId/profile estarem gravados");
            }
        }
        assertEquals(1, loads, "o construtor do Entry passou a ler o displayName mais de uma vez");
    }

    private static MethodNode constructor() throws IOException {
        String resource = ENTRY + ".class";
        try (InputStream bytecode = TabMixinTargetTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(bytecode, resource + " nao esta no classpath de teste");
            ClassNode node = new ClassNode();
            new ClassReader(bytecode).accept(node, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
            return node.methods.stream()
                    .filter(method -> method.name.equals("<init>") && method.desc.equals(CONSTRUCTOR))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("o construtor canonico do Entry mudou de assinatura"));
        }
    }
}
