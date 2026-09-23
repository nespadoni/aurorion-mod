package com.aurorion.essentials.privacy;

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
 * Confere, no bytecode real do NeoForge desta build, que os {@code @Redirect} de privacidade
 * ainda tem para onde apontar (seis no total: tres do {@code die}, entrada, saida e conquista).
 *
 * <p>Por que isto existe: um {@code @Redirect} que perde o alvo nao degrada — com
 * {@code defaultRequire: 1} no {@code aurorion_essentials.mixins.json}, ele <b>derruba o servidor no
 * boot</b>. Num servidor de 80 pessoas, descobrir isso pela janela de dez minutos entre subir o jar
 * e o servidor nao voltar e o pior momento possivel. Aqui a mesma falha vira um teste vermelho.
 *
 * <p>E a mesma familia de checagem do {@code PhoneMixinContractTest} deste modulo, com uma diferenca
 * a favor: os alvos sao do proprio Minecraft, que ja esta no classpath de teste, entao nao depende de
 * jar externo nenhum e nunca e pulado.
 *
 * <p>O que este teste <b>nao</b> cobre: outro mod do pack redirecionando a mesma instrucao (dois
 * {@code @Redirect} no mesmo ponto conflitam). Isso so se ve no pack montado — no pack atual, uma
 * varredura dos 306 jars nao achou nenhum mixin concorrente nestes pontos.
 */
class PrivacyMixinTargetTest {
    private static final String PLAYER_LIST = "net/minecraft/server/players/PlayerList";
    private static final String BROADCAST = "broadcastSystemMessage";
    private static final String BROADCAST_DESCRIPTOR = "(Lnet/minecraft/network/chat/Component;Z)V";
    private static final String TEAM_DESCRIPTOR =
            "(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/network/chat/Component;)V";

    @Test void theDeathMessageBroadcastIsStillInsideServerPlayerDie() throws IOException {
        assertEquals(1, broadcastCalls("net.minecraft.server.level.ServerPlayer", "die"),
                "ServerPlayer#die precisa ter exatamente um broadcastSystemMessage para o DeathMessageMixin"
                        + " redirecionar. Zero = alvo sumiu; mais de um = o @Redirect pegaria os dois.");
    }

    /** As duas rotas de time do {@code die}: jogador em time com {@code deathMessageVisibility}. */
    @Test void theTeamDeathBroadcastsAreStillInsideServerPlayerDie() throws IOException {
        assertEquals(1, calls("net.minecraft.server.level.ServerPlayer", "die", "broadcastSystemToTeam", TEAM_DESCRIPTOR),
                "alvo do DeathMessageMixin (so o time do morto)");
        assertEquals(1, calls("net.minecraft.server.level.ServerPlayer", "die", "broadcastSystemToAllExceptTeam",
                        TEAM_DESCRIPTOR),
                "alvo do DeathMessageMixin (todos menos o time do morto)");
    }

    @Test void theJoinMessageBroadcastIsStillInsidePlaceNewPlayer() throws IOException {
        assertEquals(1, broadcastCalls("net.minecraft.server.players.PlayerList", "placeNewPlayer"),
                "alvo do JoinLeaveMessageMixin");
    }

    @Test void theLeaveMessageBroadcastIsStillInsideRemovePlayerFromWorld() throws IOException {
        assertEquals(1, broadcastCalls("net.minecraft.server.network.ServerGamePacketListenerImpl",
                        "removePlayerFromWorld"),
                "alvo do LeaveMessageMixin — o vanilla ja moveu esta chamada de PlayerList#remove para ca uma vez");
    }

    /**
     * A conquista e o alvo mais fragil dos quatro: a chamada mora num metodo sintetico de lambda, e o
     * numero no nome ({@code lambda$award$2}) muda se alguem acrescentar uma lambda antes dela em
     * {@code award}.
     */
    @Test void theAdvancementBroadcastIsStillInThatExactLambda() throws IOException {
        assertEquals(1, broadcastCalls("net.minecraft.server.PlayerAdvancements", "lambda$award$2"),
                "alvo do AdvancementAnnounceMixin: o nome do metodo sintetico mudou de numero");
    }

    // --- Leitura do bytecode --------------------------------------------------------------------

    @Test void singlePlayerSaveRemainsAvailableForDeathHistory() throws IOException {
        MethodNode save = methodOf("net.minecraft.server.players.PlayerList", "save");
        assertNotNull(save, "PlayerListSaveInvoker precisa de PlayerList#save");
        assertEquals("(Lnet/minecraft/server/level/ServerPlayer;)V", save.desc);
    }

    private static int broadcastCalls(String className, String methodName) throws IOException {
        return calls(className, methodName, BROADCAST, BROADCAST_DESCRIPTOR);
    }

    private static int calls(String className, String methodName, String target, String descriptor) throws IOException {
        MethodNode method = methodOf(className, methodName);
        assertNotNull(method, className + "#" + methodName + " nao existe mais nesta versao");

        int found = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && PLAYER_LIST.equals(call.owner)
                    && target.equals(call.name)
                    && descriptor.equals(call.desc)) {
                found++;
            }
        }
        return found;
    }

    private static MethodNode methodOf(String className, String methodName) throws IOException {
        String resource = className.replace('.', '/') + ".class";
        try (InputStream bytecode = PrivacyMixinTargetTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(bytecode, "classe fora do classpath de teste: " + className);
            ClassNode node = new ClassNode();
            // SKIP_FRAMES/SKIP_DEBUG: so interessam as instrucoes de chamada.
            new ClassReader(bytecode).accept(node, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
            for (MethodNode method : node.methods) {
                if (method.name.equals(methodName)) return method;
            }
            return null;
        }
    }
}
