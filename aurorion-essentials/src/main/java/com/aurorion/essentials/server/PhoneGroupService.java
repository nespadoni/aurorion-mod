package com.aurorion.essentials.server;

import com.aurorion.core.rate.ActionCooldown;
import com.aurorion.essentials.AurorionEssentials;
import com.aurorion.essentials.compat.PhoneGroupBook;
import com.aurorion.essentials.compat.PhoneGroupData;
import com.aurorion.essentials.fakename.FakeName;
import com.aurorion.essentials.network.PhoneGroupPayloads;
import com.aurorion.essentials.network.PhoneGroupPayloads.Action;
import com.aurorion.essentials.network.PhoneGroupPayloads.Member;
import com.aurorion.essentials.network.PhoneGroupPayloads.Messages;
import com.aurorion.essentials.network.PhoneGroupPayloads.Notice;
import com.aurorion.essentials.network.PhoneGroupPayloads.Send;
import com.aurorion.essentials.network.PhoneGroupPayloads.Sync;
import com.aurorion.essentials.network.PhoneGroupPayloads.View;
import com.mojang.authlib.GameProfile;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Grupos de conversa do app Mensagens, no servidor: valida cada pedido, entrega a mensagem e guarda
 * para quem esta offline.
 *
 * <h2>Custo</h2>
 * <ul>
 *   <li><b>Sem tick.</b> Tudo acontece na chegada de um pacote, no login e no reset.</li>
 *   <li><b>Sem broadcast.</b> Uma mensagem vira no maximo 15 pacotes (os outros membros), o mesmo
 *       objeto reaproveitado para todos. Quem esta offline recebe na fila (50 linhas, 7 dias).</li>
 *   <li><b>Sem disco no caminho da mensagem.</b> O {@link PhoneGroupData} so e marcado sujo quando o
 *       grupo muda ou a fila de alguem muda; quem grava e o autosave do mundo.</li>
 *   <li><b>Limite por jogador.</b> Uma mensagem a cada {@value #SEND_INTERVAL_MS} ms e uma acao de grupo
 *       a cada {@value #ACTION_INTERVAL_MS} ms ({@link ActionCooldown}, o mesmo da economia).</li>
 * </ul>
 *
 * <p>So conversa com cliente que tem o canal: quem nao atualizou o essentials nao recebe nada (e as
 * mensagens ficam na fila dele) em vez de cair com pacote desconhecido.
 */
@EventBusSubscriber(modid = AurorionEssentials.MOD_ID)
public final class PhoneGroupService {
    /** O cliente espera 700 ms entre envios; a folga aqui absorve o jitter da rede. */
    static final long SEND_INTERVAL_MS = 500L;
    static final long ACTION_INTERVAL_MS = 800L;

    private static final ActionCooldown SEND = new ActionCooldown(SEND_INTERVAL_MS);
    private static final ActionCooldown ACTION = new ActionCooldown(ACTION_INTERVAL_MS);

    private PhoneGroupService() {
    }

    // ---- eventos -------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getServer() == null) return;
        if (!hasChannel(player)) return;
        MinecraftServer server = player.getServer();
        PhoneGroupBook book = PhoneGroupData.get(server).book();
        // Primeiro os grupos (o cliente cria as conversas), depois o que chegou enquanto estava fora.
        List<View> views = new ArrayList<>();
        for (PhoneGroupBook.Group group : book.groupsOf(player.getUUID())) views.add(view(server, group));
        send(player, new Sync(true, views, List.of()));
        for (Messages page : Messages.split(book.drain(player.getUUID(), System.currentTimeMillis()))) send(player, page);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SEND.clear();
        ACTION.clear();
    }

    /**
     * Reset de personagem: a conta sai de todos os grupos e perde o que estava na fila. Chamado pelo
     * {@link EssentialsCharacterReset} antes de o nome do personagem antigo ser apagado — o aviso aos
     * outros membros sai com o nome de quem morreu. Idempotente, e nunca derruba o reset.
     */
    public static void forgetAccount(MinecraftServer server, UUID account) {
        try {
            PhoneGroupBook book = PhoneGroupData.get(server).book();
            String nick = nickOf(server, account);
            String name = nameOf(server, account);
            long now = System.currentTimeMillis();
            for (PhoneGroupBook.Group group : book.removeAccount(account)) {
                deliver(server, book, group, new PhoneGroupBook.Line(group.id(), PhoneGroupBook.LEFT, nick, name, "", now), null);
                syncMembers(server, group);
            }
            ServerPlayer player = server.getPlayerList().getPlayer(account);
            if (player != null && hasChannel(player)) send(player, new Sync(true, List.of(), List.of()));
        } catch (RuntimeException e) {
            AurorionEssentials.LOGGER.warn("Nao consegui tirar dos grupos do telefone a conta {} no reset.", account, e);
        }
    }

    // ---- pacotes -------------------------------------------------------------------------------

    public static void handleSend(ServerPlayer sender, Send payload) {
        MinecraftServer server = sender.getServer();
        if (server == null) return;
        String text = PhoneGroupBook.cleanText(payload.text());
        if (text == null) {
            notice(sender, PhoneGroupBook.Result.INVALID_TEXT);
            return;
        }
        if (!SEND.acquire(sender.getUUID(), Util.getMillis())) {
            notice(sender, PhoneGroupBook.Result.RATE_LIMITED);
            return;
        }
        PhoneGroupBook book = PhoneGroupData.get(server).book();
        PhoneGroupBook.Group group = book.get(payload.group());
        if (group == null || !group.has(sender.getUUID())) {
            notice(sender, group == null ? PhoneGroupBook.Result.NOT_FOUND : PhoneGroupBook.Result.NOT_MEMBER);
            // O cliente achava que ainda estava no grupo: corrige a conversa dele.
            send(sender, new Sync(false, List.of(), List.of(payload.group())));
            return;
        }
        PhoneGroupBook.Line line = new PhoneGroupBook.Line(group.id(), PhoneGroupBook.TEXT,
                sender.getGameProfile().getName(), nameOf(server, sender.getUUID()), text, System.currentTimeMillis());
        // Quem enviou ja pos a mensagem na propria conversa.
        deliver(server, book, group, line, sender.getUUID());
    }

    public static void handleAction(ServerPlayer actor, Action payload) {
        MinecraftServer server = actor.getServer();
        if (server == null) return;
        if (!ACTION.acquire(actor.getUUID(), Util.getMillis())) {
            notice(actor, PhoneGroupBook.Result.RATE_LIMITED);
            return;
        }
        PhoneGroupBook book = PhoneGroupData.get(server).book();
        PhoneGroupBook.Result result = switch (payload.action()) {
            case PhoneGroupPayloads.CREATE -> create(server, book, actor, payload);
            case PhoneGroupPayloads.ADD -> add(server, book, actor, payload);
            case PhoneGroupPayloads.REMOVE -> remove(server, book, actor, payload);
            case PhoneGroupPayloads.LEAVE -> leave(server, book, actor, payload.group());
            default -> PhoneGroupBook.Result.NOT_FOUND;
        };
        notice(actor, result);
    }

    private static PhoneGroupBook.Result create(MinecraftServer server, PhoneGroupBook book, ServerPlayer owner, Action payload) {
        List<UUID> invited = new ArrayList<>();
        boolean unknown = resolveAll(server, payload.nicks(), invited);
        PhoneGroupBook.Created created = book.create(UUID.randomUUID(), owner.getUUID(), payload.name(), invited,
                System.currentTimeMillis());
        if (created.result() != PhoneGroupBook.Result.OK || created.group() == null) return created.result();

        PhoneGroupBook.Group group = created.group();
        PhoneGroupBook.Line line = new PhoneGroupBook.Line(group.id(), PhoneGroupBook.CREATED,
                owner.getGameProfile().getName(), nameOf(server, owner.getUUID()), "", System.currentTimeMillis());
        // Sync antes da linha: a conversa nasce com o nome certo no celular de cada um.
        syncMembers(server, group);
        deliver(server, book, group, line, null);
        if (!created.skipped().isEmpty()) return PhoneGroupBook.Result.TARGET_TOO_MANY_GROUPS;
        return unknown ? PhoneGroupBook.Result.UNKNOWN_PLAYER : PhoneGroupBook.Result.OK;
    }

    private static PhoneGroupBook.Result add(MinecraftServer server, PhoneGroupBook book, ServerPlayer actor, Action payload) {
        PhoneGroupBook.Group group = book.get(payload.group());
        if (group == null) return PhoneGroupBook.Result.NOT_FOUND;
        List<UUID> targets = new ArrayList<>();
        boolean unknown = resolveAll(server, payload.nicks(), targets);
        PhoneGroupBook.Result first = unknown ? PhoneGroupBook.Result.UNKNOWN_PLAYER : PhoneGroupBook.Result.OK;
        List<PhoneGroupBook.Line> lines = new ArrayList<>();
        long now = System.currentTimeMillis();
        String actorNick = actor.getGameProfile().getName();
        String actorName = nameOf(server, actor.getUUID());
        for (UUID target : targets) {
            PhoneGroupBook.Result result = book.add(group.id(), actor.getUUID(), target);
            if (result != PhoneGroupBook.Result.OK) {
                if (first == PhoneGroupBook.Result.OK) first = result;
                // Sem permissao ou grupo cheio: os proximos dariam o mesmo erro.
                if (result != PhoneGroupBook.Result.ALREADY_MEMBER && result != PhoneGroupBook.Result.TARGET_TOO_MANY_GROUPS) break;
                continue;
            }
            lines.add(new PhoneGroupBook.Line(group.id(), PhoneGroupBook.ADDED, actorNick, actorName, displayOf(server, target), now));
        }
        if (lines.isEmpty()) return first;
        syncMembers(server, group);
        for (PhoneGroupBook.Line line : lines) deliver(server, book, group, line, null);
        return first;
    }

    private static PhoneGroupBook.Result remove(MinecraftServer server, PhoneGroupBook book, ServerPlayer actor, Action payload) {
        PhoneGroupBook.Group group = book.get(payload.group());
        if (group == null) return PhoneGroupBook.Result.NOT_FOUND;
        if (payload.nicks().isEmpty()) return PhoneGroupBook.Result.UNKNOWN_PLAYER;
        UUID target = resolve(server, payload.nicks().getFirst());
        if (target == null) return PhoneGroupBook.Result.UNKNOWN_PLAYER;
        PhoneGroupBook.Result result = book.remove(group.id(), actor.getUUID(), target);
        if (result != PhoneGroupBook.Result.OK) return result;

        PhoneGroupBook.Line line = new PhoneGroupBook.Line(group.id(), PhoneGroupBook.REMOVED, actor.getGameProfile().getName(),
                nameOf(server, actor.getUUID()), displayOf(server, target), System.currentTimeMillis());
        syncMembers(server, group);
        deliver(server, book, group, line, null);
        // Quem saiu tambem fica sabendo, e a conversa dele deixa de aceitar mensagens.
        deliverTo(server, book, target, line);
        syncGone(server, target, group.id());
        return result;
    }

    private static PhoneGroupBook.Result leave(MinecraftServer server, PhoneGroupBook book, ServerPlayer member, UUID groupId) {
        PhoneGroupBook.Result result = book.leave(groupId, member.getUUID());
        if (result != PhoneGroupBook.Result.OK) {
            if (result == PhoneGroupBook.Result.NOT_FOUND || result == PhoneGroupBook.Result.NOT_MEMBER) {
                send(member, new Sync(false, List.of(), List.of(groupId)));
            }
            return result;
        }
        send(member, new Sync(false, List.of(), List.of(groupId)));
        PhoneGroupBook.Group group = book.get(groupId);
        if (group == null) return result;
        PhoneGroupBook.Line line = new PhoneGroupBook.Line(groupId, PhoneGroupBook.LEFT, member.getGameProfile().getName(),
                nameOf(server, member.getUUID()), "", System.currentTimeMillis());
        syncMembers(server, group);
        deliver(server, book, group, line, null);
        return result;
    }

    // ---- entrega -------------------------------------------------------------------------------

    /** Online com canal recebe agora; o resto vai para a fila. Um pacote so, reaproveitado. */
    private static void deliver(MinecraftServer server, PhoneGroupBook book, PhoneGroupBook.Group group,
                                PhoneGroupBook.Line line, @Nullable UUID except) {
        Messages live = null;
        for (UUID member : group.members()) {
            if (member.equals(except)) continue;
            ServerPlayer online = server.getPlayerList().getPlayer(member);
            if (online != null && hasChannel(online)) {
                if (live == null) live = new Messages(List.of(PhoneGroupPayloads.Line.of(line)));
                send(online, live);
            } else {
                book.enqueue(member, line);
            }
        }
    }

    private static void deliverTo(MinecraftServer server, PhoneGroupBook book, UUID member, PhoneGroupBook.Line line) {
        ServerPlayer online = server.getPlayerList().getPlayer(member);
        if (online != null && hasChannel(online)) send(online, new Messages(List.of(PhoneGroupPayloads.Line.of(line))));
        else book.enqueue(member, line);
    }

    /** O estado novo do grupo para os membros online. Offline recebe no proximo login (Sync completo). */
    private static void syncMembers(MinecraftServer server, PhoneGroupBook.Group group) {
        Sync sync = null;
        for (UUID member : group.members()) {
            ServerPlayer online = server.getPlayerList().getPlayer(member);
            if (online == null || !hasChannel(online)) continue;
            if (sync == null) sync = new Sync(false, List.of(view(server, group)), List.of());
            send(online, sync);
        }
    }

    private static void syncGone(MinecraftServer server, UUID member, UUID groupId) {
        ServerPlayer online = server.getPlayerList().getPlayer(member);
        if (online != null && hasChannel(online)) send(online, new Sync(false, List.of(), List.of(groupId)));
    }

    private static View view(MinecraftServer server, PhoneGroupBook.Group group) {
        List<Member> members = new ArrayList<>(group.members().size());
        for (UUID member : group.members()) members.add(new Member(nickOf(server, member), nameOf(server, member)));
        return new View(group.id(), group.name(), members);
    }

    // ---- identidade ----------------------------------------------------------------------------

    /** Nicks para contas, sem repetir; {@code true} se algum nick nao corresponde a ninguem conhecido. */
    private static boolean resolveAll(MinecraftServer server, List<String> nicks, List<UUID> out) {
        boolean unknown = false;
        Set<UUID> seen = new LinkedHashSet<>();
        for (String nick : nicks) {
            UUID account = resolve(server, nick);
            if (account == null) unknown = true;
            else if (seen.add(account)) out.add(account);
        }
        return unknown;
    }

    /**
     * Conta de um nick: online, ou do cache de perfis do servidor ({@code usercache.json}) — o mesmo
     * que o {@link PhoneNameDirectorySync} usa. Quem nunca entrou no servidor nao e encontrado.
     */
    @Nullable
    private static UUID resolve(MinecraftServer server, String nick) {
        if (nick == null || nick.isBlank()) return null;
        String clean = nick.trim();
        ServerPlayer online = server.getPlayerList().getPlayerByName(clean);
        if (online != null) return online.getUUID();
        GameProfileCache profiles = server.getProfileCache();
        if (profiles == null) return null;
        return profiles.get(clean).map(GameProfile::getId).orElse(null);
    }

    private static String nickOf(MinecraftServer server, UUID account) {
        ServerPlayer online = server.getPlayerList().getPlayer(account);
        if (online != null) return online.getGameProfile().getName();
        GameProfileCache profiles = server.getProfileCache();
        if (profiles == null) return "";
        return profiles.get(account).map(GameProfile::getName).orElse("");
    }

    /** Nome do personagem (sem cor), ou vazio — o cliente cai no nick, que o telefone ja troca pelo nome. */
    private static String nameOf(MinecraftServer server, UUID account) {
        String raw = FakeNameData.get(server).getRaw(account);
        return raw == null || raw.isBlank() ? "" : FakeName.parse(raw).plain();
    }

    private static String displayOf(MinecraftServer server, UUID account) {
        String name = nameOf(server, account);
        return name.isEmpty() ? nickOf(server, account) : name;
    }

    // ---- rede ----------------------------------------------------------------------------------

    private static void notice(ServerPlayer player, PhoneGroupBook.Result result) {
        send(player, Notice.of(result));
    }

    private static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (hasChannel(player)) PacketDistributor.sendToPlayer(player, payload);
    }

    private static boolean hasChannel(ServerPlayer player) {
        return player.connection != null && player.connection.hasChannel(Sync.TYPE);
    }
}
