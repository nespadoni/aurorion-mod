package com.aurorion.essentials.voice;

import com.aurorion.essentials.fakename.FakeName;
import com.aurorion.essentials.fakename.FakeNameRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

/**
 * O nome que o Simple Voice Chat mostra de cada jogador: o do personagem, nao o nick da Mojang.
 *
 * <p>O Voice Chat manda a todos os clientes um "estado" de cada jogador com o nome dele, e o cliente
 * usa esse nome so para exibir: lista de grupos, membros do grupo, tela de ajustar volume e o cache de
 * nomes dele. Esse nome vinha de {@code GameProfile#getName()} — o nick real, que o fakename existe
 * para esconder. O {@code VoicechatPlayerNameMixin} troca o nome na criacao do estado; esta classe
 * reenvia o estado quando o nome do personagem muda com a pessoa ja online.
 *
 * <p>Sem tipo nenhum do Voice Chat aqui, para o {@code FakeNameManager} poder chamar sempre: quem cita
 * o Voice Chat e o {@link VoiceNames}, carregado so quando o mod esta instalado.
 */
public final class VoiceNameSync {
    private static final boolean VOICECHAT = ModList.get() != null && ModList.get().isLoaded("voicechat");

    private VoiceNameSync() {
    }

    /** O nome do personagem, sem cores; o nick quando a pessoa nao tem um. */
    public static String displayName(ServerPlayer player) {
        FakeName fake = FakeNameRegistry.get(player.getUUID());
        return fake != null && !fake.plain().isBlank() ? fake.plain() : player.getGameProfile().getName();
    }

    /** Depois de o nome do personagem mudar: atualiza o que o Voice Chat mostra para todos. */
    public static void refresh(ServerPlayer player) {
        if (VOICECHAT) VoiceNames.refresh(player);
    }
}
