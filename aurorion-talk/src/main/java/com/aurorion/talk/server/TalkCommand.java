package com.aurorion.talk.server;

import com.aurorion.talk.AurorionTalk;
import com.aurorion.talk.config.TalkServerConfig;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /aurorion fala} — o alcance da fala, ajustado pelo jogo.
 *
 * <pre>
 * /aurorion fala                          como esta agora
 * /aurorion fala alcance &lt;blocos&gt;         quem esta ate essa distancia ouve
 * /aurorion fala proximidade ligar|desligar  desligar volta ao chat global do vanilla
 * /aurorion fala espiar ligar|desligar       a staff recebe tambem as falas de longe
 * </pre>
 *
 * <p>Comando, e nao so o TOML: a staff ajusta o alcance conforme o evento (uma assembleia pede mais
 * que uma conversa de taverna) e nao deve precisar abrir arquivo para isso. O valor e gravado no
 * mesmo TOML, entao sobrevive ao reinicio. O espiar, de proposito, nao e gravado — ver
 * {@link ProximityChat}.
 *
 * <p>Nivel {@link ProximityChat#STAFF_PERMISSION}: muda o que o servidor inteiro ouve.
 */
@EventBusSubscriber(modid = AurorionTalk.MOD_ID)
public final class TalkCommand {
    private TalkCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("aurorion")
                .then(Commands.literal("fala").requires(source -> source.hasPermission(ProximityChat.STAFF_PERMISSION))
                        .executes(c -> status(c.getSource()))
                        .then(Commands.literal("alcance")
                                .then(Commands.argument("blocos", IntegerArgumentType.integer(
                                                TalkServerConfig.MIN_RADIUS, TalkServerConfig.MAX_RADIUS))
                                        .executes(TalkCommand::setRadius)))
                        .then(Commands.literal("proximidade")
                                .then(Commands.literal("ligar").executes(c -> setEnabled(c.getSource(), true)))
                                .then(Commands.literal("desligar").executes(c -> setEnabled(c.getSource(), false))))
                        .then(Commands.literal("espiar")
                                .then(Commands.literal("ligar").executes(c -> setSpying(c.getSource(), true)))
                                .then(Commands.literal("desligar").executes(c -> setSpying(c.getSource(), false))))));
    }

    private static int status(CommandSourceStack source) {
        String mode = TalkServerConfig.PROXIMITY_ENABLED.get()
                ? "por proximidade, alcance de " + TalkServerConfig.PROXIMITY_RADIUS.get() + " blocos"
                : "GLOBAL (proximidade desligada) — todo mundo le tudo";
        String spy = source.getEntity() instanceof ServerPlayer player
                ? (ProximityChat.isSpying(player.getUUID()) ? " Voce esta espiando as falas de longe." : " Voce nao esta espiando.")
                : "";

        source.sendSuccess(() -> Component.literal("Fala " + mode + "." + spy + "\n"
                + "/aurorion fala alcance <blocos> (" + TalkServerConfig.MIN_RADIUS + "-" + TalkServerConfig.MAX_RADIUS + ")\n"
                + "/aurorion fala proximidade ligar|desligar\n"
                + "/aurorion fala espiar ligar|desligar — so nesta sessao do servidor"), false);
        return 1;
    }

    private static int setRadius(CommandContext<CommandSourceStack> c) {
        int radius = IntegerArgumentType.getInteger(c, "blocos");
        TalkServerConfig.PROXIMITY_RADIUS.set(radius);
        TalkServerConfig.SPEC.save();

        // true: fica no log e aparece para a staff online, como os outros comandos de moderacao.
        c.getSource().sendSuccess(() -> Component.literal("Alcance da fala: " + radius + " blocos."
                + (TalkServerConfig.PROXIMITY_ENABLED.get() ? "" : " (A proximidade esta desligada; vale quando ligar.)")), true);
        return radius;
    }

    private static int setEnabled(CommandSourceStack source, boolean enabled) {
        TalkServerConfig.PROXIMITY_ENABLED.set(enabled);
        TalkServerConfig.SPEC.save();

        source.sendSuccess(() -> Component.literal(enabled
                ? "Fala por proximidade ligada: so ouve quem esta a ate " + TalkServerConfig.PROXIMITY_RADIUS.get() + " blocos."
                : "Fala por proximidade DESLIGADA: o chat voltou a ser global."), true);
        return 1;
    }

    private static int setSpying(CommandSourceStack source, boolean spying) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        boolean changed = ProximityChat.setSpying(player.getUUID(), spying);

        String state = spying
                ? "Espiando: voce recebe tambem as falas de longe, marcadas com [longe]."
                : "Espiar desligado: voce so ouve quem esta perto.";
        // true no ligar: os outros OPs ficam sabendo que alguem esta lendo a cena alheia.
        source.sendSuccess(() -> Component.literal(changed ? state : state + " (Ja estava assim.)"), spying && changed);
        return 1;
    }
}
