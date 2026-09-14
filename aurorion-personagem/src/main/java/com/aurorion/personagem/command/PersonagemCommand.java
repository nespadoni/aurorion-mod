package com.aurorion.personagem.command;

import com.aurorion.core.character.CharacterData;
import com.aurorion.core.character.CharacterName;
import com.aurorion.personagem.AurorionPersonagem;
import com.aurorion.personagem.creation.CreationManager;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.UUID;

/**
 * {@code /personagem} — a resposta de quem nao tem cliente com o mod, e a janela da staff.
 *
 * <h2>Por que a raiz nao exige permissao</h2>
 *
 * <p>{@code criar} e {@code continuar} sao a unica saida de quem esta retido no login com um cliente
 * vanilla. Exigir permissao ali fecharia o servidor para essas pessoas. O que protege o comando nao e
 * nivel de OP: e o estado — ele so faz alguma coisa para quem o servidor esta esperando responder, e
 * o {@code CreationManager} reconfere tudo de novo.
 *
 * <p>Os ramos que mexem na vida dos outros ({@code ver} de terceiro, {@code renomear}, {@code cancelar})
 * exigem nivel 2, como o resto da staff no ecossistema.
 */
@EventBusSubscriber(modid = AurorionPersonagem.MOD_ID)
public final class PersonagemCommand {
    private static final int STAFF_LEVEL = 2;

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private PersonagemCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("personagem")
                .then(Commands.literal("criar")
                        .then(Commands.argument("nome", StringArgumentType.string())
                                .then(Commands.argument("sobrenome", StringArgumentType.string())
                                        .executes(PersonagemCommand::create))))
                .then(Commands.literal("continuar")
                        .executes(PersonagemCommand::resume))
                .then(Commands.literal("ver")
                        .executes(context -> show(context, context.getSource().getPlayerOrException().getUUID(),
                                context.getSource().getPlayerOrException().getGameProfile().getName()))
                        .then(Commands.argument("jogador", GameProfileArgument.gameProfile())
                                .requires(source -> source.hasPermission(STAFF_LEVEL))
                                .executes(context -> {
                                    GameProfile profile = single(context);
                                    return show(context, profile.getId(), profile.getName());
                                })))
                .then(Commands.literal("renomear")
                        .requires(source -> source.hasPermission(STAFF_LEVEL))
                        .then(Commands.argument("jogador", GameProfileArgument.gameProfile())
                                .then(Commands.argument("nome", StringArgumentType.string())
                                        .then(Commands.argument("sobrenome", StringArgumentType.string())
                                                .executes(PersonagemCommand::rename)))))
                .then(Commands.literal("cancelar")
                        .requires(source -> source.hasPermission(STAFF_LEVEL))
                        .then(Commands.argument("jogador", GameProfileArgument.gameProfile())
                                .executes(PersonagemCommand::cancel))));
    }

    private static int create(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CreationManager.submit(player,
                StringArgumentType.getString(context, "nome"),
                StringArgumentType.getString(context, "sobrenome"));
        return 1;
    }

    /** Retoma uma reserva interrompida. O nome ja esta escrito no diario; nao ha o que digitar. */
    private static int resume(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CharacterData.Pending pending = CharacterData.get(player.server).pending(player.getUUID());

        if (pending == null) {
            context.getSource().sendFailure(Component.literal("Não há criação pela metade nesta conta."));
            return 0;
        }
        CreationManager.submit(player, pending.next().firstName(), pending.next().lastName());
        return 1;
    }

    private static int show(CommandContext<CommandSourceStack> context, UUID account, String accountName) {
        CharacterData data = CharacterData.get(context.getSource().getServer());
        CharacterData.Character character = data.find(account);

        if (character == null) {
            context.getSource().sendSuccess(() ->
                    Component.literal(accountName + " ainda não tem personagem neste mundo."), false);
            return 0;
        }

        StringBuilder line = new StringBuilder(accountName).append(" — ")
                .append(character.named() ? character.fullName() : "sem nome")
                .append(character.dead() ? " (morto)" : " (vivo)")
                .append("\nid: ").append(character.id())
                .append("\ncriado: ").append(WHEN.format(Instant.ofEpochMilli(character.createdAt())));

        if (character.dead()) line.append("\nmorreu: ").append(WHEN.format(Instant.ofEpochMilli(character.diedAt())));

        CharacterData.Pending pending = data.pending(account);
        if (pending != null) line.append("\nreserva pendente: ").append(pending.next().fullName());

        String text = line.toString();
        context.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int rename(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        GameProfile profile = single(context);
        CharacterData data = CharacterData.get(context.getSource().getServer());

        if (data.find(profile.getId()) == null) {
            context.getSource().sendFailure(Component.literal(profile.getName() + " não tem personagem."));
            return 0;
        }

        CharacterName name;
        try {
            name = new CharacterName(StringArgumentType.getString(context, "nome"),
                    StringArgumentType.getString(context, "sobrenome"));
        } catch (IllegalArgumentException invalid) {
            context.getSource().sendFailure(Component.literal(invalid.getMessage()));
            return 0;
        }

        CharacterData.Character renamed;
        try {
            renamed = data.rename(profile.getId(), name);
        } catch (IllegalStateException taken) {
            context.getSource().sendFailure(Component.literal("Esse nome já pertence a outro personagem."));
            return 0;
        }

        ServerPlayer online = context.getSource().getServer().getPlayerList().getPlayer(profile.getId());
        if (online != null) {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                    new com.aurorion.core.character.CharacterNamedEvent(online, renamed, false));
        }

        context.getSource().sendSuccess(() ->
                Component.literal(profile.getName() + " agora é " + renamed.fullName() + "."), true);
        return 1;
    }

    /**
     * Devolve o nome reservado por uma criacao que travou, para a pessoa poder escolher outro.
     *
     * <p>Nao ressuscita ninguem de proposito: a conta continua morta e a tela reaparece no proximo
     * login. Um comando que apenas tirasse a marca de morte devolveria inventario, casa e progressao
     * da historia anterior — que e exatamente o que a morte definitiva encerra.
     */
    private static int cancel(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        GameProfile profile = single(context);
        CharacterData.Pending abandoned =
                CharacterData.get(context.getSource().getServer()).abandonReplacement(profile.getId());

        if (abandoned == null) {
            context.getSource().sendFailure(Component.literal(profile.getName() + " não tem reserva pendente."));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("Reserva de " + abandoned.next().fullName()
                + " cancelada. A conta segue sem personagem e a tela volta no próximo login."), true);
        return 1;
    }

    private static GameProfile single(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "jogador");
        if (profiles.size() != 1) {
            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                    Component.literal("Informe um jogador só.")).create();
        }
        return profiles.iterator().next();
    }
}
