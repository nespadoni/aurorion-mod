package com.aurorion.personagem.alt;

import com.aurorion.core.character.AltData;
import com.aurorion.core.rate.ActionCooldown;
import com.aurorion.personagem.AurorionPersonagem;
import com.mojang.authlib.GameProfile;
import net.minecraft.Util;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.server.players.UserWhiteListEntry;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Only profile lookup runs off-thread. SavedData, permissions and registration stay on the server. */
final class AltCreation {
    private static final int MAX_CANDIDATES = 16;
    private static final int MAX_LOCAL_NAMES = 1000;
    private static final ActionCooldown REQUESTS = new ActionCooldown(30_000);
    /** Sorteia nome e UUID do alt novo: nada previsivel a partir da conta. */
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<UUID, Request> PENDING = new HashMap<>();
    private static MinecraftServer ownerServer;
    private static ThreadPoolExecutor executor;

    private static final class Request {
        final ServerPlayer player;
        final MinecraftServer server;
        final GameProfileCache cache;
        final List<String> names;
        final Path playerData;
        final boolean authenticated;
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        Future<?> task;

        Request(ServerPlayer player, List<String> names) {
            this.player = player;
            server = player.server;
            cache = server.getProfileCache();
            this.names = List.copyOf(names);
            playerData = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
            authenticated = server.usesAuthentication();
        }

        boolean realProfile(GameProfile profile) {
            if (authenticated) return true;
            UUID offline = UUIDUtil.createOfflinePlayerUUID(profile.getName());
            return !offline.equals(profile.getId()) || Files.exists(playerData.resolve(offline + ".dat"));
        }
    }

    private AltCreation() { }

    static boolean start(ServerPlayer player) {
        if (!AltLogin.canCreate(player)) return false;
        if (ownerServer != player.server) {
            reset();
            ownerServer = player.server;
            executor = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16), task -> {
                Thread thread = new Thread(task, "aurorion-alt-lookup");
                thread.setDaemon(true);
                return thread;
            });
            executor.allowCoreThreadTimeOut(true);
        }
        if (PENDING.containsKey(player.getUUID())) {
            player.sendSystemMessage(Component.literal("A criação do seu personagem alternativo já está em andamento."));
            return false;
        }
        if (!REQUESTS.acquire(player.getUUID(), Util.getMillis())) {
            player.sendSystemMessage(Component.literal("Aguarde 30 segundos entre pedidos de criação de personagem alternativo."));
            return false;
        }
        AltData alts = AltData.get(player.server);
        // Um alt desvinculado volta como era: mesmo UUID, mesmo perfil, mesmo personagem. Sem isso o
        // UUID aleatorio se perderia — ele nao e recalculavel a partir da conta.
        AltData.Alt restored = restore(player, alts);
        if (restored != null) return true;

        // Nome sorteado, e nao "<nick>_alt": o nome de perfil vai para todo cliente na lista de
        // jogadores, e nao pode dizer de quem o alt e.
        var names = new ArrayList<String>();
        for (int attempt = 0; attempt < MAX_LOCAL_NAMES && names.size() < MAX_CANDIDATES; attempt++) {
            String name = AltData.randomProfileName(RANDOM);
            if (!names.contains(name) && !localNameTaken(player.server, alts, name)) names.add(name);
        }
        if (names.isEmpty() || player.server.getProfileCache() == null) {
            player.sendSystemMessage(Component.literal("Nenhum nome de perfil disponível, ou serviço de perfis indisponível."));
            return false;
        }
        Request request = new Request(player, names);
        PENDING.put(player.getUUID(), request);
        if (!submit(request, 0)) return false;
        player.sendSystemMessage(Component.literal("Procurando um nome de perfil para o personagem alternativo. Você será avisado ao concluir."));
        return true;
    }

    private static boolean submit(Request request, int start) {
        try {
            request.task = executor.submit(() -> lookup(request, start));
            return true;
        } catch (RejectedExecutionException closedOrFull) {
            finish(request, "O serviço de criação está ocupado. Tente novamente em instantes.");
            return false;
        }
    }

    private static void lookup(Request request, int start) {
        try {
            for (int index = start; index < request.names.size(); index++) {
                if (Thread.currentThread().isInterrupted() || System.nanoTime() - request.deadline >= 0) break;
                String name = request.names.get(index);
                // Only the cache and detached path/authentication snapshot are accessed off-thread.
                Optional<GameProfile> profile = request.cache.get(name);
                if (profile.filter(request::realProfile).isPresent()) continue;
                int candidate = index;
                request.server.execute(() -> complete(request, candidate, profile));
                return;
            }
            request.server.execute(() -> finish(request, "Não foi possível encontrar um nome disponível nesta tentativa. Tente novamente mais tarde."));
        } catch (RuntimeException failure) {
            try {
                request.server.execute(() -> finish(request, "A consulta de perfis falhou. Tente novamente mais tarde."));
            } catch (RuntimeException stopping) {
                // Shutdown clears PENDING; an old worker cannot register into the next world.
            }
        }
    }

    private static void complete(Request request, int candidate, Optional<GameProfile> profile) {
        if (!current(request)) return;
        if (!AltLogin.canCreate(request.player) || System.nanoTime() - request.deadline >= 0) {
            finish(request, "A criação perdeu a validade. Confira sua permissão e tente novamente.");
            return;
        }
        AltData alts = AltData.get(request.server);
        String name = request.names.get(candidate);
        if (localNameTaken(request.server, alts, name) || profile.filter(request::realProfile).isPresent()) {
            submit(request, candidate + 1);
            return;
        }
        AltData.Alt alt;
        try {
            alt = alts.createUnlinked(request.player.getUUID(), name, RANDOM);
        } catch (RuntimeException failure) {
            AurorionPersonagem.LOGGER.warn("Falha ao registrar alt: {}", failure.getClass().getSimpleName());
            finish(request, "Não foi possível registrar o personagem alternativo. Avise a equipe.");
            return;
        }
        // The character already exists: a cache/whitelist failure must not leave a stale request.
        PENDING.remove(request.player.getUUID(), request);
        if (!register(request.server, request.player, alt)) return;
        AurorionPersonagem.LOGGER.info("{} criou o alt {} ({}).", request.player.getGameProfile().getName(), name, alt.altId());
        request.player.sendSystemMessage(Component.literal("Personagem alternativo criado (perfil " + name
                + "). Ele não tem OP. Use /personagem trocar " + name + " para entrar nele — na primeira vez você escolhe o nome."));
    }

    /** Revincula o alt aposentado mais recente da conta, na thread do servidor. Null se nao ha. */
    private static AltData.Alt restore(ServerPlayer player, AltData alts) {
        AltData.Alt alt = alts.restoreRetired(player.getUUID());
        if (alt == null) return null;
        if (!register(player.server, player, alt)) return alt;
        AurorionPersonagem.LOGGER.info("{} revinculou o alt {} ({}).", player.getGameProfile().getName(), alt.altName(), alt.altId());
        player.sendSystemMessage(Component.literal("Seu personagem alternativo anterior foi revinculado (perfil " + alt.altName()
                + "). Use /personagem trocar " + alt.altName() + " para entrar nele."));
        return alt;
    }

    /** Cache de perfis e whitelist. @return false (ja avisado) se algo falhou depois do cadastro. */
    private static boolean register(MinecraftServer server, ServerPlayer owner, AltData.Alt alt) {
        GameProfile altProfile = new GameProfile(alt.altId(), alt.altName());
        try {
            GameProfileCache cache = server.getProfileCache();
            if (cache != null) cache.add(altProfile);
            var players = server.getPlayerList();
            if (players.isUsingWhitelist() && players.isWhiteListed(owner.getGameProfile())) {
                players.getWhiteList().add(new UserWhiteListEntry(altProfile));
            }
            return true;
        } catch (RuntimeException failure) {
            AurorionPersonagem.LOGGER.warn("Alt {} ({}) registrado, mas cache/whitelist falhou: {}",
                    alt.altName(), alt.altId(), failure.getClass().getSimpleName());
            owner.sendSystemMessage(Component.literal("O personagem " + alt.altName()
                    + " foi cadastrado, mas houve uma falha no cache ou na whitelist. Avise a equipe antes de entrar nele."));
            return false;
        }
    }

    private static boolean localNameTaken(MinecraftServer server, AltData alts, String name) {
        return alts.nameReserved(name) || server.getPlayerList().getPlayerByName(name) != null;
    }

    private static boolean current(Request request) {
        if (ownerServer != request.server || PENDING.get(request.player.getUUID()) != request) return false;
        if (request.server.getPlayerList().getPlayer(request.player.getUUID()) == request.player) return true;
        PENDING.remove(request.player.getUUID(), request);
        return false;
    }

    private static void finish(Request request, String message) {
        if (!current(request)) return;
        PENDING.remove(request.player.getUUID(), request);
        request.player.sendSystemMessage(Component.literal(message));
    }

    static void logout(UUID player) {
        Request request = PENDING.remove(player);
        if (request != null && request.task != null) request.task.cancel(true);
        if (executor != null) executor.purge();
    }

    static void reset() {
        if (executor != null) executor.shutdownNow();
        executor = null;
        ownerServer = null;
        PENDING.clear();
        REQUESTS.clear();
    }
}
