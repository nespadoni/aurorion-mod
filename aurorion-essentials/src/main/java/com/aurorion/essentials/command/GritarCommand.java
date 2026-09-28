package com.aurorion.essentials.command;

import com.aurorion.essentials.AurorionEssentials;
import com.aurorion.essentials.fakename.CharacterTarget;
import com.aurorion.essentials.voice.ShoutRegistry;
import com.aurorion.essentials.voice.VoiceConfig;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * {@code /gritar} — a staff aumenta o alcance da voz (Simple Voice Chat) de alguem enquanto quiser.
 *
 * <pre>
 * /gritar &lt;pessoa&gt; &lt;distancia&gt;   liga (ou troca o alcance), em blocos
 * /gritar &lt;pessoa&gt; desligar       volta a voz ao normal
 * /gritar lista                   quem esta gritando agora
 * </pre>
 *
 * <p>{@code <pessoa>} e o nome do personagem, o nick ou a UUID ({@link CharacterTarget}). Da para
 * ligar em quem esta offline: vale quando a pessoa entrar. Sussurro nao e afetado. O alcance fica so
 * em memoria ({@link ShoutRegistry}) e zera ao reiniciar o servidor.
 *
 * <p>Nivel 2+: e ferramenta de narracao da staff, nao do jogador.
 */
@EventBusSubscriber(modid = AurorionEssentials.MOD_ID)
public final class GritarCommand {
    private static final String OFF = "desligar";

    private GritarCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("gritar")
                .requires(source -> source.hasPermission(2))
                .executes(c -> help(c.getSource()))
                .then(Commands.literal("lista").executes(c -> list(c.getSource())))
                .then(Commands.argument("pessoa", StringArgumentType.string()).suggests(CharacterTarget.SUGGESTIONS)
                        .then(Commands.literal(OFF).executes(GritarCommand::off))
                        .then(Commands.argument("distancia", FloatArgumentType.floatArg(1.0F))
                                .executes(GritarCommand::on))));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ShoutRegistry.clearAll();
    }

    private static int on(CommandContext<CommandSourceStack> c) {
        CommandSourceStack source = c.getSource();
        UUID account = target(c);
        if (account == null) return 0;

        float distance = FloatArgumentType.getFloat(c, "distancia");
        double max = VoiceConfig.MAX_SHOUT_DISTANCE.get();
        if (distance > max) {
            source.sendFailure(Component.literal("O alcance maximo e " + blocks((float) max) + " blocos "
                    + "(maxShoutDistance em config/aurorion/essentials-voice-server.toml)."));
            return 0;
        }

        ShoutRegistry.set(account, distance);
        String name = CharacterTarget.displayName(source.getServer(), account);
        // true: fica no log e aparece para a staff online, como os outros comandos de moderacao.
        source.sendSuccess(() -> Component.literal(name + " esta gritando: a voz alcanca " + blocks(distance)
                + " blocos. Para parar: /gritar " + StringArgumentType.escapeIfRequired(name) + " " + OFF), true);
        warnIfNoVoiceChat(source);
        notifyTarget(source.getServer(), account, "Voce esta gritando: sua voz alcanca " + blocks(distance) + " blocos.");
        return 1;
    }

    private static int off(CommandContext<CommandSourceStack> c) {
        CommandSourceStack source = c.getSource();
        UUID account = target(c);
        if (account == null) return 0;

        String name = CharacterTarget.displayName(source.getServer(), account);
        if (!ShoutRegistry.clear(account)) {
            source.sendFailure(Component.literal(name + " nao estava gritando."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(name + " parou de gritar: a voz voltou ao alcance normal."), true);
        notifyTarget(source.getServer(), account, "Sua voz voltou ao alcance normal.");
        return 1;
    }

    private static int list(CommandSourceStack source) {
        Map<UUID, Float> shouting = ShoutRegistry.all();
        if (shouting.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Ninguem esta gritando."), false);
            return 0;
        }
        MinecraftServer server = source.getServer();
        StringBuilder text = new StringBuilder("Gritando agora:");
        shouting.forEach((account, distance) -> {
            boolean online = server.getPlayerList().getPlayer(account) != null;
            text.append("\n- ").append(CharacterTarget.displayName(server, account))
                    .append(": ").append(blocks(distance)).append(" blocos")
                    .append(online ? "" : " (offline)");
        });
        source.sendSuccess(() -> Component.literal(text.toString()), false);
        return shouting.size();
    }

    private static int help(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "/gritar <pessoa> <distancia> — aumenta o alcance da voz dessa pessoa, em blocos, ate desligar.\n"
                        + "/gritar <pessoa> " + OFF + " — volta a voz ao normal.\n"
                        + "/gritar lista — quem esta gritando agora.\n"
                        + "<pessoa> e o nome do personagem, o nick ou a UUID; nome com espaco entre aspas: "
                        + "/gritar \"Bella Noob\" 96. Sussurro nao muda. Tudo zera ao reiniciar o servidor."), false);
        return 1;
    }

    @Nullable
    private static UUID target(CommandContext<CommandSourceStack> c) {
        String query = StringArgumentType.getString(c, "pessoa");
        UUID account = CharacterTarget.resolve(c.getSource().getServer(), query);
        if (account == null) {
            c.getSource().sendFailure(Component.literal("Nao achei ninguem chamado \"" + query + "\". Aceita nome "
                    + "de personagem, nick da conta ou UUID; nome com espaco vai entre aspas."));
        }
        return account;
    }

    private static void notifyTarget(MinecraftServer server, UUID account, String message) {
        ServerPlayer player = server.getPlayerList().getPlayer(account);
        if (player != null) player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.GOLD));
    }

    private static void warnIfNoVoiceChat(CommandSourceStack source) {
        if (!ModList.get().isLoaded("voicechat")) {
            source.sendFailure(Component.literal("Aviso: o Simple Voice Chat nao esta instalado; o grito nao tem efeito."));
        }
    }

    /** 96.0 vira "96"; 12.5 continua "12.5". */
    private static String blocks(float distance) {
        return distance == Math.rint(distance) ? String.valueOf((int) distance) : String.valueOf(distance);
    }
}
