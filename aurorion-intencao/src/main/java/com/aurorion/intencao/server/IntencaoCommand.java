package com.aurorion.intencao.server;

import com.aurorion.intencao.AurorionIntencao;
import com.aurorion.intencao.config.IntencaoConfig;
import com.aurorion.intencao.network.Tone;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * O que o mestre usa durante a mesa. Staff (nivel em {@code permissionLevel}, padrao 2).
 *
 * <ul>
 *   <li>{@code /intencao iniciar <tema> <jogadores> <rivais>} — os dois grupos aceitam nome ou
 *       seletor ({@code @a[team=...]}); a ordem define os lados da arena.</li>
 *   <li>{@code /intencao encerrar <vencedor>} — veredito; a sessao e a do vencedor.</li>
 *   <li>{@code /intencao empate <participante>} — as vontades se anulam.</li>
 *   <li>{@code /intencao clima <participante> calmo|tenso|ruptura} — o tom da cena.</li>
 *   <li>{@code /intencao listar} e {@code /intencao recuperar <jogadores>} (quem ficou preso).</li>
 * </ul>
 *
 * <p>Participantes sao lidos como perfil, e nao como entidade, no encerrar/empate/clima: o mestre
 * pode dar o veredito com alguem que caiu da conexao no meio da cena.
 */
@EventBusSubscriber(modid = AurorionIntencao.MOD_ID)
public final class IntencaoCommand {
    private IntencaoCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> tone = Commands.literal("clima");
        var target = Commands.argument("participante", GameProfileArgument.gameProfile());
        for (Tone value : Tone.values()) {
            target.then(Commands.literal(value.key()).executes(context -> tone(context, value)));
        }
        tone.then(target);

        event.getDispatcher().register(Commands.literal("intencao")
                .requires(source -> source.hasPermission(IntencaoConfig.PERMISSION_LEVEL.get()))
                .then(Commands.literal("iniciar")
                        .then(Commands.argument("tema", ResourceLocationArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(Theme.THEMES.ids(), builder))
                                .then(Commands.argument("jogadores", EntityArgument.players())
                                        .then(Commands.argument("rivais", EntityArgument.players())
                                                .executes(IntencaoCommand::start)))))
                .then(Commands.literal("encerrar")
                        .then(Commands.argument("vencedor", GameProfileArgument.gameProfile())
                                .executes(context -> end(context, "vencedor", true))))
                .then(Commands.literal("empate")
                        .then(Commands.argument("participante", GameProfileArgument.gameProfile())
                                .executes(context -> end(context, "participante", false))))
                .then(tone)
                .then(Commands.literal("listar").executes(IntencaoCommand::list))
                .then(Commands.literal("recuperar")
                        .then(Commands.argument("jogadores", EntityArgument.players())
                                .executes(IntencaoCommand::recover))));
    }

    private static int start(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ResourceLocation id = ResourceLocationArgument.getId(context, "tema");
        Theme theme = Theme.THEMES.get(id);
        // "tribunal" chega como minecraft:tribunal; o mais provavel e que seja nosso.
        if (theme == null && id.getNamespace().equals(ResourceLocation.DEFAULT_NAMESPACE)) {
            theme = Theme.THEMES.get(AurorionIntencao.id(id.getPath()));
        }
        if (theme == null) {
            context.getSource().sendFailure(Plane.tr("erro.tema", id.toString()));
            return 0;
        }
        // Os dois grupos sao os dois lados. Quem aparece nos dois fica no primeiro.
        List<ServerPlayer> players = new ArrayList<>();
        List<Integer> sides = new ArrayList<>();
        for (ServerPlayer player : EntityArgument.getPlayers(context, "jogadores")) {
            if (!players.contains(player)) {
                players.add(player);
                sides.add(0);
            }
        }
        for (ServerPlayer player : EntityArgument.getPlayers(context, "rivais")) {
            if (!players.contains(player)) {
                players.add(player);
                sides.add(1);
            }
        }
        if (!sides.contains(1)) {
            context.getSource().sendFailure(Plane.tr("erro.sem_rival"));
            return 0;
        }
        Component failure = Plane.start(context.getSource().getServer(), theme, players, sides);
        if (failure != null) {
            context.getSource().sendFailure(failure);
            return 0;
        }
        int count = players.size();
        context.getSource().sendSuccess(() -> Plane.tr("comando.iniciada", count), true);
        return count;
    }

    private static int end(CommandContext<CommandSourceStack> context, String argument, boolean hasWinner)
            throws CommandSyntaxException {
        GameProfile profile = single(GameProfileArgument.getGameProfiles(context, argument));
        IntencaoData.Session session = profile == null ? null
                : IntencaoData.get(context.getSource().getServer()).sessionOf(profile.getId());
        if (session == null) {
            context.getSource().sendFailure(Plane.tr("erro.sem_sessao"));
            return 0;
        }
        if (!Plane.verdict(context.getSource().getServer(), session, hasWinner ? profile.getId() : null)) {
            context.getSource().sendFailure(Plane.tr("erro.ja_encerrada"));
            return 0;
        }
        String name = profile.getName();
        context.getSource().sendSuccess(() -> hasWinner ? Plane.tr("comando.vencedor", name) : Plane.tr("comando.empate"), true);
        return 1;
    }

    private static int tone(CommandContext<CommandSourceStack> context, Tone tone) throws CommandSyntaxException {
        GameProfile profile = single(GameProfileArgument.getGameProfiles(context, "participante"));
        IntencaoData.Session session = profile == null ? null
                : IntencaoData.get(context.getSource().getServer()).sessionOf(profile.getId());
        if (session == null || session.state != IntencaoData.State.ACTIVE) {
            context.getSource().sendFailure(Plane.tr("erro.sem_sessao"));
            return 0;
        }
        Plane.setTone(context.getSource().getServer(), session, tone);
        context.getSource().sendSuccess(() -> Plane.tr("comando.clima", Plane.tr("clima." + tone.key())), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        IntencaoData data = IntencaoData.get(context.getSource().getServer());
        if (!data.hasSessions()) {
            context.getSource().sendSuccess(() -> Plane.tr("comando.nenhuma"), false);
            return 0;
        }
        long now = Plane.now(context.getSource().getServer());
        for (IntencaoData.Session session : data.sessions()) {
            long minutes = Math.max(0, now - session.startedAt) / 1200;
            Component line = Component.literal("#" + Plane.shortId(session.id) + " ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.join(" × ", session.names)).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("  " + session.theme.getPath() + " · ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Plane.tr("clima." + session.tone.key()).withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(" · " + minutes + " min").withStyle(ChatFormatting.DARK_GRAY));
            context.getSource().sendSuccess(() -> line, false);
        }
        return data.sessions().size();
    }

    private static int recover(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int done = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(context, "jogadores")) {
            if (Plane.recover(player)) done++;
        }
        int count = done;
        context.getSource().sendSuccess(() -> Plane.tr("comando.recuperados", count), true);
        return count;
    }

    @Nullable
    private static GameProfile single(Collection<GameProfile> profiles) {
        return profiles.isEmpty() ? null : profiles.iterator().next();
    }
}
