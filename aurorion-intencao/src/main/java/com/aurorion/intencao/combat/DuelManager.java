package com.aurorion.intencao.combat;

import com.aurorion.core.character.CharacterData;
import com.aurorion.core.character.CharacterFinale;
import com.aurorion.intencao.AurorionIntencao;
import com.aurorion.intencao.config.IntencaoConfig;
import com.aurorion.intencao.network.DuelPayloads;
import com.aurorion.intencao.server.IntencaoData;
import com.aurorion.intencao.server.Plane;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Os duelos abertos, o desafio e o campo de não fuga, no estilo de um MMO: alguém desafia com o limite
 * de vida (primeiro sangue, uma porcentagem ou até a morte), o outro aceita, quem está por perto é
 * avisado, e um campo prende os dois até o fim. O mestre não precisa fazer nada; pode pausar, retomar ou
 * parar.
 *
 * <p>Também conduz o combate dentro de uma batalha de intenção ({@code /intencao combate}), com os lados da
 * sessão e o veredito do Plano no fim.
 *
 * <p>Custo: com nenhum duelo aberto o tick é uma checagem de mapa vazio. Com duelos, o campo e o anel de
 * partículas rodam a cada 10 ticks por duelo; nada varre o mundo.
 */
@EventBusSubscriber(modid = AurorionIntencao.MOD_ID)
public final class DuelManager {
    private record Challenge(UUID challenger, int limit, long expires) {
    }

    private static final Map<UUID, Duel> DUELS = new HashMap<>();
    private static final Map<UUID, Duel> BY_PLAYER = new HashMap<>();
    /** Desafiado → desafio. */
    private static final Map<UUID, Challenge> CHALLENGES = new HashMap<>();
    private static final Map<UUID, Long> LAST_CHALLENGE = new HashMap<>();
    /** Quando a dupla (ids em ordem) ganhou XP de duelo pela última vez, em ticks do servidor. */
    private static final Map<String, Long> PAIR_XP = new HashMap<>();
    private static final List<String> LIMITS = List.of("primeiro_sangue", "75", "50", "25", "10", "morte");

    private DuelManager() {
    }

    public static boolean inDuel(UUID player) {
        return BY_PLAYER.containsKey(player);
    }

    // --- Comandos ----------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("duelo")
                .then(Commands.literal("desafiar").then(Commands.argument("jogador", EntityArgument.player())
                        .then(Commands.argument("limite", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(LIMITS, b))
                                .executes(DuelManager::challenge))))
                .then(Commands.literal("aceitar").executes(DuelManager::accept))
                .then(Commands.literal("recusar").executes(DuelManager::refuse))
                .then(Commands.literal("desistir").executes(DuelManager::surrender))
                .then(Commands.literal("parar").requires(s -> s.hasPermission(IntencaoConfig.PERMISSION_LEVEL.get()))
                        .then(Commands.argument("jogador", EntityArgument.player()).executes(c -> staff(c, 0))))
                .then(Commands.literal("pausar").requires(s -> s.hasPermission(IntencaoConfig.PERMISSION_LEVEL.get()))
                        .then(Commands.argument("jogador", EntityArgument.player()).executes(c -> staff(c, 1))))
                .then(Commands.literal("retomar").requires(s -> s.hasPermission(IntencaoConfig.PERMISSION_LEVEL.get()))
                        .then(Commands.argument("jogador", EntityArgument.player()).executes(c -> staff(c, 2)))));
        event.getDispatcher().register(Commands.literal("intencao")
                .requires(s -> s.hasPermission(IntencaoConfig.PERMISSION_LEVEL.get()))
                .then(Commands.literal("combate").then(Commands.argument("participante", EntityArgument.player())
                        .then(Commands.argument("limite", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(LIMITS.subList(0, LIMITS.size() - 1), b))
                                .executes(DuelManager::planeCombat)))));
    }

    /** {@code -1} primeiro sangue, {@code 0} morte, 1–99 porcentagem; {@code -2} inválido. */
    static int parseLimit(String text) {
        if (text.equalsIgnoreCase("primeiro_sangue")) return -1;
        if (text.equalsIgnoreCase("morte")) return 0;
        try {
            int value = Integer.parseInt(text.replace("%", ""));
            return value >= 1 && value <= 99 ? value : -2;
        } catch (NumberFormatException e) {
            return -2;
        }
    }

    static String describe(int limit) {
        return limit < 0 ? "primeiro sangue" : limit == 0 ? "ATÉ A MORTE" : "até " + limit + "% da vida";
    }

    private static int challenge(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer challenger = context.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "jogador");
        int limit = parseLimit(StringArgumentType.getString(context, "limite"));
        String refusal = refusal(challenger, target, limit);
        if (refusal != null) {
            context.getSource().sendFailure(Component.literal(refusal));
            return 0;
        }
        long nowTick = now(challenger.server);
        Challenge pending = CHALLENGES.get(target.getUUID());
        if (pending != null && pending.expires() >= nowTick) {
            // Um desafio por vez: trocar os termos de um convite aberto deixaria aceitar "até a morte" sem querer.
            context.getSource().sendFailure(Component.literal("Essa pessoa já tem um desafio esperando resposta."));
            return 0;
        }
        Long last = LAST_CHALLENGE.get(challenger.getUUID());
        if (last != null && nowTick - last < IntencaoConfig.CHALLENGE_COOLDOWN.get() * 20L) {
            context.getSource().sendFailure(Component.literal("Espere um pouco antes de desafiar de novo."));
            return 0;
        }
        LAST_CHALLENGE.put(challenger.getUUID(), nowTick);
        long expires = nowTick + IntencaoConfig.CHALLENGE_SECONDS.get() * 20L;
        CHALLENGES.put(target.getUUID(), new Challenge(challenger.getUUID(), limit, expires));
        String challengerName = Plane.nameOf(challenger.server, challenger);
        MutableComponent message = Component.literal("⚔ " + challengerName + " desafia você para um duelo (" + describe(limit) + "). ")
                .withStyle(limit == 0 ? ChatFormatting.DARK_RED : ChatFormatting.GOLD)
                .append(button("[Aceitar]", "/duelo aceitar", ChatFormatting.GREEN,
                        limit == 0 ? "Até a morte: quem zerar a vida da ficha perde o personagem para sempre." : "Aceitar o duelo"))
                .append(" ")
                .append(button("[Recusar]", "/duelo recusar", ChatFormatting.RED, "Recusar o duelo"));
        // Com o mod, o convite é o cartão no meio da visão; o chat fica só para quem joga sem ele.
        if (modded(target)) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(target,
                    new DuelPayloads.Invite(challengerName, describe(limit), limit == 0, IntencaoConfig.CHALLENGE_SECONDS.get()));
        } else {
            target.sendSystemMessage(message);
        }
        context.getSource().sendSuccess(() -> Component.literal("Desafio enviado (" + describe(limit) + ").").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    @Nullable
    private static String refusal(ServerPlayer challenger, ServerPlayer target, int limit) {
        if (!IntencaoConfig.DUEL_ENABLED.get()) return "Duelos estão desligados no servidor.";
        if (limit == -2) return "Limite inválido: primeiro_sangue, um número de 1 a 99, ou morte.";
        if (limit == 0 && (!IntencaoConfig.DEATH_ALLOWED.get() || !CharacterFinale.installed())) return "Duelo até a morte não está disponível neste servidor.";
        if (challenger == target) return "Você não pode duelar consigo mesmo.";
        if (inDuel(challenger.getUUID()) || inDuel(target.getUUID())) return "Um dos dois já está num duelo.";
        if (Plane.inPlane(challenger) || Plane.inPlane(target)) return "Não dá para duelar dentro do Plano da Intenção (o mestre usa /intencao combate).";
        if (challenger.level() != target.level() || challenger.distanceTo(target) > IntencaoConfig.CHALLENGE_RANGE.get())
            return "Chegue mais perto (até " + IntencaoConfig.CHALLENGE_RANGE.get() + " blocos).";
        if (!challenger.isAlive() || !target.isAlive() || challenger.isSpectator() || target.isSpectator()) return "Os dois precisam estar de pé.";
        var characters = CharacterData.get(challenger.server);
        if (characters.isDead(challenger.getUUID()) || characters.isDead(target.getUUID())) return "Personagem morto não duela.";
        return null;
    }

    private static int accept(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = context.getSource().getPlayerOrException();
        Challenge challenge = CHALLENGES.remove(target.getUUID());
        if (challenge == null || challenge.expires() < now(target.server)) {
            context.getSource().sendFailure(Component.literal("Não há desafio para aceitar."));
            return 0;
        }
        ServerPlayer challenger = target.server.getPlayerList().getPlayer(challenge.challenger());
        if (challenger == null) {
            context.getSource().sendFailure(Component.literal("Quem desafiou já não está aqui."));
            return 0;
        }
        String refusal = refusal(challenger, target, challenge.limit());
        if (refusal != null) {
            context.getSource().sendFailure(Component.literal(refusal));
            return 0;
        }
        List<Combatant> fighters = List.of(
                Combatant.of(challenger, Plane.nameOf(challenger.server, challenger), 0, 0xE04848),
                Combatant.of(target, Plane.nameOf(target.server, target), 1, 0x4878E0));
        Vec3 center = challenger.position().add(target.position()).scale(.5);
        Duel duel = new Duel(fighters, challenge.limit(), challenger.level().dimension(), center, IntencaoConfig.FIELD_RADIUS.get(), null);
        open(challenger.server, duel);
        return 1;
    }

    private static int refuse(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = context.getSource().getPlayerOrException();
        Challenge challenge = CHALLENGES.remove(target.getUUID());
        if (challenge == null) return 0;
        ServerPlayer challenger = target.server.getPlayerList().getPlayer(challenge.challenger());
        if (challenger != null) challenger.displayClientMessage(Component.literal(Plane.nameOf(target.server, target) + " recusou o duelo.").withStyle(ChatFormatting.GRAY), true);
        context.getSource().sendSuccess(() -> Component.literal("Duelo recusado.").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int surrender(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Duel duel = BY_PLAYER.get(player.getUUID());
        if (duel == null) return 0;
        duel.surrender(player.server, duel.indexOf(player.getUUID()), now(player.server));
        return 1;
    }

    private static int staff(CommandContext<CommandSourceStack> context, int what) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "jogador");
        Duel duel = BY_PLAYER.get(player.getUUID());
        if (duel == null) {
            context.getSource().sendFailure(Component.literal("Essa pessoa não está num duelo."));
            return 0;
        }
        long now = now(player.server);
        switch (what) {
            case 0 -> {
                close(duel);
                for (Combatant c : duel.fighters) {
                    ServerPlayer p = player.server.getPlayerList().getPlayer(c.id);
                    if (p != null) p.sendSystemMessage(Component.literal("O mestre encerrou o duelo. Ninguém venceu.").withStyle(ChatFormatting.GRAY));
                }
                sendEnd(player.server, duel);
            }
            case 1 -> duel.pause(player.server, now, true);
            default -> duel.pause(player.server, now, false);
        }
        context.getSource().sendSuccess(() -> Component.literal("Feito.").withStyle(ChatFormatting.GRAY), true);
        return 1;
    }

    /** Combate com a ficha dentro de uma batalha de intenção: os lados e os nomes da sessão. */
    private static int planeCombat(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer member = EntityArgument.getPlayer(context, "participante");
        int limit = parseLimit(StringArgumentType.getString(context, "limite"));
        if (limit == -2 || limit == 0) {
            context.getSource().sendFailure(Component.literal("Limite: primeiro_sangue ou 1 a 99 (até a morte só no /duelo)."));
            return 0;
        }
        MinecraftServer server = member.server;
        IntencaoData.Session session = IntencaoData.get(server).sessionOf(member.getUUID());
        if (session == null || session.state != IntencaoData.State.ACTIVE) {
            context.getSource().sendFailure(Component.literal("Essa pessoa não está numa batalha de intenção aberta."));
            return 0;
        }
        List<Combatant> fighters = new ArrayList<>();
        for (int i = 0; i < session.members.size(); i++) {
            ServerPlayer p = server.getPlayerList().getPlayer(session.members.get(i));
            if (p == null || inDuel(p.getUUID())) continue;
            fighters.add(Combatant.of(p, session.names.get(i), session.sides.get(i), session.colors.get(i)));
        }
        if (fighters.stream().map(c -> c.side).distinct().count() < 2) {
            context.getSource().sendFailure(Component.literal("Os dois lados precisam ter alguém online."));
            return 0;
        }
        open(server, new Duel(fighters, limit, Plane.DIMENSION, Vec3.atCenterOf(session.center), 0, session.id));
        context.getSource().sendSuccess(() -> Component.literal("Combate com a ficha aberto (" + describe(limit) + ").").withStyle(ChatFormatting.GRAY), true);
        return 1;
    }

    private static MutableComponent button(String text, String command, ChatFormatting color, String hover) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(color).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
    }

    // --- Ciclo -------------------------------------------------------------------------------------

    private static void open(MinecraftServer server, Duel duel) {
        DUELS.put(duel.id, duel);
        for (Combatant c : duel.fighters) {
            BY_PLAYER.put(c.id, duel);
            CHALLENGES.remove(c.id);
        }
        duel.start(server, now(server));
        for (Combatant c : duel.fighters) {
            ServerPlayer p = server.getPlayerList().getPlayer(c.id);
            if (p == null) continue;
            if (!modded(p)) {
                title(p, Component.literal("DUELO").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                        Component.literal(describe(duel.limit)).withStyle(duel.limit == 0 ? ChatFormatting.DARK_RED : ChatFormatting.YELLOW));
            }
            p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, .6f, 1.4f);
        }
        syncArena(server, duel);
        for (Combatant c : duel.fighters) lightning(server, duel, c.id);
    }

    /** XP de duelo: lutado de verdade (dano causado, ninguém desistiu ou fugiu) e a dupla fora do intervalo. */
    private static boolean xpAllowed(MinecraftServer server, Duel duel) {
        if (duel.fighters.stream().anyMatch(c -> c.out)) return false;
        if (duel.fighters.stream().mapToInt(c -> c.dealt).sum() <= 0) return false;
        String pair = duel.fighters.stream().map(c -> c.id.toString()).sorted().reduce((x, y) -> x + "|" + y).orElse("");
        long nowTick = now(server), wait = IntencaoConfig.XP_PAIR_MINUTES.get() * 1200L;
        Long last = PAIR_XP.get(pair);
        if (last != null && nowTick - last < wait) return false;
        PAIR_XP.put(pair, nowTick);
        if (PAIR_XP.size() > 4096) PAIR_XP.values().removeIf(t -> nowTick - t > wait);
        return true;
    }

    private static String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    /** Com o mod no cliente? (O AutoModpack leva a todos; o chat fica de reserva para quem não tem.) */
    static boolean modded(ServerPlayer player) {
        return player.connection.hasChannel(DuelPayloads.Arena.TYPE);
    }

    /**
     * Um raio de efeito em cima do duelista (abertura, crítico, derrota), desenhado no cliente de quem vê a
     * arena. O raio do vanilla pisca o céu da dimensão inteira, troveja para todos e mexe em cobre e para-raios.
     */
    static void lightning(MinecraftServer server, Duel duel, UUID player) {
        ServerPlayer p = server.getPlayerList().getPlayer(player);
        if (p == null || p.level().dimension() != duel.dimension) return;
        DuelPayloads.Bolt bolt = new DuelPayloads.Bolt(p.getX(), p.getY(), p.getZ(), duel.limit == 0);
        for (UUID id : duel.viewers) {
            ServerPlayer viewer = server.getPlayerList().getPlayer(id);
            if (viewer != null && modded(viewer)) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(viewer, bolt);
        }
    }

    /**
     * A arena para quem está perto e ainda não a recebeu: os duelistas e a plateia no raio de aviso. Um pacote
     * por pessoa; o cliente desenha o campo e os efeitos sozinho. Chamado ao abrir e a cada 2 s (quem chega).
     */
    private static void syncArena(MinecraftServer server, Duel duel) {
        ServerLevel level = server.getLevel(duel.dimension);
        if (level == null || duel.radius <= 0) return;
        double radius = IntencaoConfig.WARN_RADIUS.get();
        DuelPayloads.Arena arena = arena(duel, true, "");
        for (ServerPlayer p : level.players()) {
            if (duel.viewers.contains(p.getUUID())) continue;
            boolean fighter = duel.indexOf(p.getUUID()) >= 0;
            if (!fighter && p.position().distanceToSqr(duel.center) > radius * radius) continue;
            duel.viewers.add(p.getUUID());
            if (modded(p)) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, arena);
            else if (!fighter) p.sendSystemMessage(Component.literal("⚔ Duelo perto de você: " + duel.fighters.get(0).name + " × "
                    + duel.fighters.get(1).name + " (" + describe(duel.limit) + ").").withStyle(ChatFormatting.GOLD));
        }
    }

    private static DuelPayloads.Arena arena(Duel duel, boolean open, String headline) {
        return new DuelPayloads.Arena(duel.id.getMostSignificantBits(), duel.id.getLeastSignificantBits(), duel.center.x, duel.center.y,
                duel.center.z, duel.radius, duel.limit == 0, open, headline);
    }

    /** O fim para quem viu a arena: o cliente apaga o campo e mostra o resultado à plateia. */
    private static void endArena(MinecraftServer server, Duel duel, String headline) {
        if (duel.arenaClosed) return;
        duel.arenaClosed = true;
        DuelPayloads.Arena arena = arena(duel, false, headline);
        for (UUID id : duel.viewers) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            if (modded(p)) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, arena);
            else if (duel.indexOf(id) < 0) p.sendSystemMessage(Component.literal("⚔ " + headline).withStyle(ChatFormatting.GOLD));
        }
    }

    private static void close(Duel duel) {
        duel.phase = Duel.Phase.ENDED;
        DUELS.remove(duel.id);
        for (Combatant c : duel.fighters) BY_PLAYER.remove(c.id, duel);
    }

    /**
     * Fim pelo motor do duelo. {@code winnerSide} {@code -1}: ninguém de pé (empate). No duelo até a morte,
     * quem zerou a vida da ficha — e quem fugiu, com {@code fugaMata} — perde o personagem: a cena de fim
     * do Limbo, sem passar por ele.
     */
    static void finish(MinecraftServer server, Duel duel, int winnerSide) {
        close(duel);
        String winners = String.join(", ", duel.fighters.stream().filter(c -> c.side == winnerSide).map(c -> c.name).toList());
        Component result = winnerSide < 0
                ? Component.literal("⚔ O duelo terminou sem vencedor.").withStyle(ChatFormatting.GRAY)
                : Component.literal("⚔ " + winners + " venceu o duelo (" + describe(duel.limit) + ").").withStyle(ChatFormatting.GOLD);
        String headline = clip(winnerSide < 0 ? "O duelo terminou sem vencedor." : winners + " venceu o duelo (" + describe(duel.limit) + ").", 150);
        boolean xpAllowed = xpAllowed(server, duel);
        for (Combatant c : duel.fighters) {
            ServerPlayer p = server.getPlayerList().getPlayer(c.id);
            boolean won = c.side == winnerSide;
            if (winnerSide >= 0 && !won) lightning(server, duel, c.id);
            if (p == null) continue;
            int xp = !xpAllowed || c.out ? 0 : winnerSide < 0 ? IntencaoConfig.XP_LOSS.get() : won ? IntencaoConfig.XP_WIN.get() : IntencaoConfig.XP_LOSS.get();
            if (xp > 0) p.giveExperiencePoints(xp);
            if (modded(p)) {
                List<String[]> rows = List.of(
                        new String[]{"Dano causado", String.valueOf(c.dealt)},
                        new String[]{"Dano recebido", String.valueOf(c.taken)},
                        new String[]{"Golpes que entraram", String.valueOf(c.hits)},
                        new String[]{"Defesas", String.valueOf(c.defended)},
                        new String[]{"Críticos", String.valueOf(c.crits)},
                        new String[]{"XP ganho", "+" + xp});
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,
                        new DuelPayloads.Result(winnerSide < 0 ? 0 : won ? 1 : 2, headline, rows));
            } else {
                p.sendSystemMessage(result);
                title(p, Component.literal(winnerSide < 0 ? "EMPATE" : won ? "VITÓRIA" : "DERROTA")
                                .withStyle(won ? ChatFormatting.GOLD : ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                        Component.literal(winnerSide < 0 ? "" : winners));
            }
        }
        endArena(server, duel, headline);
        sendEnd(server, duel);
        if (duel.planeSession != null) {
            IntencaoData.Session session = IntencaoData.get(server).session(duel.planeSession);
            if (session != null) {
                UUID winner = duel.fighters.stream().filter(c -> c.side == winnerSide).map(c -> c.id).findFirst().orElse(null);
                Plane.verdict(server, session, winner);
            }
            return;
        }
        ServerLevel level = server.getLevel(duel.dimension);
        if (duel.limit == 0) {
            for (Combatant c : duel.fighters) {
                if (c.side == winnerSide) continue;
                boolean fled = c.out && !c.surrendered && c.hp > 0;
                if (c.hp <= 0 || fled && IntencaoConfig.FLEE_KILLS.get()) {
                    AurorionIntencao.LOGGER.info("Duelo ate a morte: o personagem de {} chegou ao fim.", c.id);
                    if (level != null) announce(level, duel.center, Component.literal("☠ " + c.name + " caiu no duelo e não vai se levantar.")
                            .withStyle(ChatFormatting.DARK_RED), duel);
                    CharacterFinale.end(server, c.id);
                }
            }
        }
    }

    /** O HUD do duelo some nos clientes (estado com fase de fim já mandado; isto fecha a tela de ação). */
    private static void sendEnd(MinecraftServer server, Duel duel) {
        // Encerrado pelo mestre ou por erro: o campo também some para a plateia.
        endArena(server, duel, "O duelo foi encerrado.");
        for (Combatant c : duel.fighters) {
            ServerPlayer p = server.getPlayerList().getPlayer(c.id);
            if (p != null) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,
                    new DuelPayloads.State(List.of(), -1, -1, 0, 0, Duel.Phase.ENDED.ordinal(), 0, "", false));
        }
    }

    private static void title(ServerPlayer player, Component title, Component subtitle) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 15));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    /** Quem está perto (e não está no duelo) lê o aviso: no cartão com o mod, no chat sem ele. */
    private static void announce(ServerLevel level, Vec3 center, Component message, Duel duel) {
        double radius = IntencaoConfig.WARN_RADIUS.get();
        for (ServerPlayer p : level.players()) {
            if (duel.indexOf(p.getUUID()) >= 0 || p.position().distanceToSqr(center) > radius * radius) continue;
            if (modded(p)) p.displayClientMessage(message, true);
            else p.sendSystemMessage(message);
        }
    }

    // --- Rede --------------------------------------------------------------------------------------

    public static void handleInput(DuelPayloads.Input input, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            Duel duel = BY_PLAYER.get(player.getUUID());
            if (duel != null) duel.input(player.server, player, input, now(player.server));
        });
    }

    // --- Eventos -----------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!CHALLENGES.isEmpty() && server.getTickCount() % 100 == 0) {
            long now = now(server);
            CHALLENGES.values().removeIf(c -> c.expires() < now);
        }
        if (DUELS.isEmpty()) return;
        long now = now(server);
        for (Duel duel : List.copyOf(DUELS.values())) {
            try {
                duel.tick(server, now);
                if (duel.radius > 0 && server.getTickCount() % 10 == 0) field(server, duel);
            } catch (RuntimeException failure) {
                AurorionIntencao.LOGGER.error("Duelo {} falhou; encerrado sem vencedor", duel.id, failure);
                close(duel);
                sendEnd(server, duel);
            }
        }
    }

    /** O campo de não fuga: quem passa da borda volta para dentro; o anel aparece para quem está perto. */
    private static void field(MinecraftServer server, Duel duel) {
        ServerLevel level = server.getLevel(duel.dimension);
        if (level == null) return;
        double r = duel.radius;
        // O anel e os efeitos são do cliente (pacote Arena): nada de partícula por tick saindo do servidor.
        if (server.getTickCount() % 40 == 0) syncArena(server, duel);
        for (Combatant c : duel.fighters) {
            if (duel.phase == Duel.Phase.ENDED) return;
            ServerPlayer p = server.getPlayerList().getPlayer(c.id);
            if (p == null || c.out) continue;
            if (p.level() != level) {
                duel.left(server, c.id, now(server));
                continue;
            }
            Vec3 offset = p.position().subtract(duel.center);
            double flat = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
            if (flat <= r) continue;
            double scale = (r - 1.5) / flat;
            if (p.isPassenger()) p.stopRiding();
            p.teleportTo(duel.center.x + offset.x * scale, p.getY(), duel.center.z + offset.z * scale);
            p.displayClientMessage(Component.literal("O campo do duelo não deixa você sair.").withStyle(ChatFormatting.RED), true);
        }
    }

    /** Durante o duelo ninguém apanha de verdade nem bate de verdade: a luta é na ficha. */
    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (BY_PLAYER.isEmpty()) return;
        Entity attacker = event.getSource().getEntity();
        if (attacker == null) return;
        // A luta é na ficha: duelista não fere nem é ferido por quem está no mesmo duelo, e não fere ninguém de
        // fora. Mob, lava, queda e terceiros continuam valendo: duelo não é imortalidade.
        if (BY_PLAYER.containsKey(attacker.getUUID())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Duel duel = BY_PLAYER.get(player.getUUID());
            if (duel != null) duel.left(player.server, player.getUUID(), now(player.server));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        CHALLENGES.remove(id);
        LAST_CHALLENGE.remove(id);
        Duel duel = BY_PLAYER.get(id);
        if (duel != null && event.getEntity() instanceof ServerPlayer player) duel.left(player.server, id, now(player.server));
    }

    /** O servidor vai parar: os duelos acabam sem vencedor antes de o logout de cada um contar como fuga. */
    @SubscribeEvent
    public static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        for (Duel duel : List.copyOf(DUELS.values())) {
            close(duel);
            sendEnd(event.getServer(), duel);
        }
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        DUELS.clear();
        BY_PLAYER.clear();
        CHALLENGES.clear();
        LAST_CHALLENGE.clear();
        PAIR_XP.clear();
    }

    private static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }
}
