package com.aurorion.personagem.command;

import com.aurorion.core.character.AltData;
import com.aurorion.core.character.CharacterData;
import com.aurorion.core.character.CharacterName;
import com.aurorion.personagem.AurorionPersonagem;
import com.aurorion.personagem.alt.AltLogin;
import com.aurorion.personagem.creation.CreationManager;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
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
    /** Desvincular alt de alguem: dono do servidor (console ou nivel 4). */
    private static final int ADMIN_LEVEL = 4;

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
                .then(Commands.literal("liberar")
                        .requires(source -> source.hasPermission(STAFF_LEVEL))
                        .then(Commands.argument("jogador", GameProfileArgument.gameProfile())
                                .executes(PersonagemCommand::authorize)))
                .then(Commands.literal("revogar")
                        .requires(source -> source.hasPermission(STAFF_LEVEL))
                        .then(Commands.argument("jogador", GameProfileArgument.gameProfile())
                                .executes(PersonagemCommand::revoke)))
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
                                .executes(PersonagemCommand::cancel)))
                // Sem requires de proposito: quem esta no alt nao e OP e precisa conseguir voltar. Quem
                // pode trocar e decidido pelo cadastro de alts, dentro do AltLogin.
                .then(Commands.literal("trocar")
                        .executes(context -> AltLogin.switchCharacter(context.getSource().getPlayerOrException()) ? 1 : 0))
                .then(Commands.literal("alt")
                        .then(Commands.literal("criar")
                                .requires(source -> source.hasPermission(STAFF_LEVEL))
                                .executes(PersonagemCommand::createAlt))
                        .then(Commands.literal("ver")
                                .requires(source -> source.hasPermission(STAFF_LEVEL))
                                .executes(context -> showAlt(context, context.getSource().getPlayerOrException().getUUID(),
                                        context.getSource().getPlayerOrException().getGameProfile().getName()))
                                .then(Commands.argument("jogador", GameProfileArgument.gameProfile())
                                        .executes(context -> {
                                            GameProfile profile = single(context);
                                            return showAlt(context, profile.getId(), profile.getName());
                                        })))
                        .then(Commands.literal("remover")
                                .requires(source -> source.hasPermission(ADMIN_LEVEL))
                                .then(Commands.argument("jogador", GameProfileArgument.gameProfile())
                                        .executes(PersonagemCommand::removeAlt)))));
    }

    // --- Segundo personagem da staff ------------------------------------------------------------

    /** Cria o alt de quem chamou. So staff, e so a partir da conta principal. */
    private static int createAlt(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        AltData alts = AltData.get(player.server);

        if (alts.isAlt(player.getUUID())) {
            context.getSource().sendFailure(Component.literal("Você está no segundo personagem. Volte para a conta principal com /personagem trocar."));
            return 0;
        }
        if (!AltLogin.canCreate(player)) {
            context.getSource().sendFailure(Component.literal("Esta conta já tem um segundo personagem. Veja com /personagem alt ver."));
            return 0;
        }

        AltData.Alt alt = AltLogin.create(player);
        if (alt == null) {
            context.getSource().sendFailure(Component.literal("Nenhum nome de perfil livre para o segundo personagem. Chame quem mantém o servidor."));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("Segundo personagem criado (perfil " + alt.altName()
                + "). Ele não tem OP. Use /personagem trocar para entrar nele — na primeira vez você escolhe o nome."), true);
        return 1;
    }

    private static int showAlt(CommandContext<CommandSourceStack> context, UUID account, String accountName) {
        MinecraftServer server = context.getSource().getServer();
        AltData alts = AltData.get(server);

        UUID owner = alts.ownerOf(account);
        if (owner != null) {
            String ownerName = AltLogin.accountName(server, owner);
            context.getSource().sendSuccess(() -> Component.literal(accountName + " é o segundo personagem de "
                    + ownerName + "."), false);
            return 1;
        }

        AltData.Alt alt = alts.byOwner(account);
        if (alt == null) {
            context.getSource().sendSuccess(() -> Component.literal(accountName + " não tem segundo personagem."), false);
            return 0;
        }

        String text = accountName + " — segundo personagem: " + AltLogin.characterName(server, alt.altId(), alt.altName())
                + "\nperfil: " + alt.altName()
                + "\nid: " + alt.altId()
                + "\npróximo login entra como: " + (alt.active() ? "segundo personagem" : "conta principal");
        context.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    /**
     * Desfaz o vinculo. Os arquivos do alt ficam no mundo: recriar devolve o mesmo personagem. Aceita
     * tanto a conta principal quanto o proprio alt como alvo.
     */
    private static int removeAlt(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        GameProfile profile = single(context);
        MinecraftServer server = context.getSource().getServer();
        AltData alts = AltData.get(server);

        UUID owner = alts.isAlt(profile.getId()) ? alts.ownerOf(profile.getId()) : profile.getId();
        AltData.Alt removed = owner == null ? null : alts.remove(owner);
        if (removed == null) {
            context.getSource().sendFailure(Component.literal(profile.getName() + " não tem segundo personagem."));
            return 0;
        }

        ServerPlayer online = server.getPlayerList().getPlayer(removed.altId());
        if (online != null) {
            online.connection.disconnect(Component.literal("O segundo personagem desta conta foi desligado pela staff."));
        }
        AurorionPersonagem.LOGGER.info("Alt {} ({}) desvinculado por {}.",
                removed.altName(), removed.altId(), context.getSource().getTextName());
        context.getSource().sendSuccess(() -> Component.literal("Segundo personagem " + removed.altName()
                + " desvinculado. Os dados dele continuam no mundo."), true);
        return 1;
    }

    private static int create(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        CreationManager.submit(player,
                StringArgumentType.getString(context, "nome"),
                StringArgumentType.getString(context, "sobrenome"));
        return 1;
    }

    /**
     * Abre a porta para outra historia. E a unica forma de sair de uma morte definitiva.
     *
     * <p>Vale por <b>uma</b> historia: publicar a identidade nova consome a autorizacao, entao a
     * proxima morte precisa de outra conversa com a staff. Sem isto, morrer seria um contratempo de
     * dois minutos em vez de um fim.
     */
    private static int authorize(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        GameProfile profile = single(context);
        CharacterData data = CharacterData.get(context.getSource().getServer());

        if (!data.isDead(profile.getId())) {
            context.getSource().sendFailure(
                    Component.literal(profile.getName() + " não tem personagem morto para substituir."));
            return 0;
        }

        if (!data.authorize(profile.getId())) {
            context.getSource().sendFailure(Component.literal(profile.getName() + " já estava liberado."));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal(profile.getName()
                + " pode criar outro personagem. A tela aparece no próximo login."), true);
        return 1;
    }

    private static int revoke(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        GameProfile profile = single(context);

        if (!CharacterData.get(context.getSource().getServer()).revokeAuthorization(profile.getId())) {
            context.getSource().sendFailure(Component.literal(profile.getName() + " não estava liberado."));
            return 0;
        }

        context.getSource().sendSuccess(() ->
                Component.literal("Liberação de " + profile.getName() + " cancelada."), true);
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

        if (character.dead()) {
            line.append("\nmorreu: ").append(WHEN.format(Instant.ofEpochMilli(character.diedAt())))
                    .append("\nliberado pela staff: ").append(data.isAuthorized(account) ? "sim" : "não");
        }

        CharacterData.Pending pending = data.pending(account);
        if (pending != null) line.append("\ntroca em andamento: ").append(pending.next().fullName());

        MinecraftServer server = context.getSource().getServer();
        AltData alts = AltData.get(server);
        UUID altOwner = alts.ownerOf(account);
        if (altOwner != null) {
            line.append("\nsegundo personagem de: ").append(AltLogin.accountName(server, altOwner));
        } else if (alts.byOwner(account) != null) {
            AltData.Alt alt = alts.byOwner(account);
            line.append("\nsegundo personagem: ").append(AltLogin.characterName(server, alt.altId(), alt.altName()));
        }

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
        } else {
            // Offline nao ha a quem aplicar o nome exibido agora: o login seguinte aplica
            // (CreationManager.onJoin). Antes ficava o nome antigo sobre a cabeca para sempre.
            data.markRenamed(profile.getId());
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
