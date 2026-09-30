package com.aurorion.servicos.server;

import com.aurorion.core.character.CharacterData;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;

import java.util.UUID;

/**
 * Nome e nick de quem aparece no app.
 *
 * <p>O <b>nome</b> e o do personagem: o exibido de quem esta online (que o essentials troca pelo nome
 * do personagem), ou o gravado no {@link CharacterData} para quem esta offline. O <b>nick</b> nunca
 * aparece na tela — so viaja para o celular abrir a conversa, porque o telefone roteia mensagem pelo
 * nick da conta.
 */
final class ServicosNames {
    private static final int MAX_NAME = 48;

    private ServicosNames() {
    }

    static String name(MinecraftServer server, UUID account) {
        ServerPlayer online = server.getPlayerList().getPlayer(account);
        String name = null;
        if (online != null) {
            name = online.getDisplayName().getString();
        } else {
            CharacterData.Character character = CharacterData.get(server).find(account);
            if (character != null && character.named()) name = character.fullName();
        }
        if (name == null || name.isBlank()) name = nick(server, account);
        if (name.isBlank()) name = "Desconhecido";
        return name.length() <= MAX_NAME ? name : name.substring(0, MAX_NAME);
    }

    static String nick(MinecraftServer server, UUID account) {
        ServerPlayer online = server.getPlayerList().getPlayer(account);
        if (online != null) return online.getGameProfile().getName();
        GameProfileCache profiles = server.getProfileCache();
        if (profiles == null) return "";
        return profiles.get(account).map(GameProfile::getName).orElse("");
    }
}
