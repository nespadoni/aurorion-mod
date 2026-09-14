package com.aurorion.limbo.command;

import com.aurorion.limbo.AurorionLimbo;
import com.aurorion.limbo.exile.ExileRecord;
import com.aurorion.limbo.exile.ForgottenDoor;
import com.aurorion.limbo.exile.LimboData;
import com.aurorion.limbo.exile.LimboManager;
import com.aurorion.limbo.network.LimboNetwork;
import com.aurorion.limbo.report.AuditLog;
import com.aurorion.vidas.lives.LivesManager;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /limbo} — a janela da staff, e a metade do controle que e <b>puxada</b> por RCON.
 *
 * <h2>Por que a saida nao e traduzida, contra a regra do resto do ecossistema</h2>
 *
 * <p>Todo o resto do Aurorion manda {@code Component.translatable} e deixa o cliente montar a frase.
 * Aqui nao, e por dois motivos que so valem para este comando:
 *
 * <ul>
 *   <li><b>Quem le e um bot.</b> A saida deste comando existe para ser parseada por um programa do
 *       outro lado de uma conexao RCON. Formato estavel vale mais que idioma certo.</li>
 *   <li><b>RCON nao tem cliente.</b> Nao existe tela para resolver a traducao, entao o servidor
 *       resolveria com o idioma dele — que e o da hospedagem, quase nunca o do servidor.</li>
 * </ul>
 *
 * <p>Entao a saida e {@code chave=valor} separado por espaco: le a olho nu, e {@code split} do outro
 * lado. Os nomes das chaves sao contrato com o bot — chave nova se adiciona, chave velha nao se
 * renomeia.
 *
 * <h2>O que RCON faz, e o que ele nao faz</h2>
 *
 * <p>RCON e de mao unica, e a mao e a de fora: o bot pergunta, o servidor responde. E otimo para
 * "quem esta no Limbo agora" — e incapaz de "me avise no instante em que alguem atravessou a Porta",
 * porque o servidor nao abre conexao RCON com ninguem. Essa outra metade e o webhook
 * ({@code DiscordSink}), e as duas leem exatamente os mesmos dados.
 */
@EventBusSubscriber(modid = AurorionLimbo.MOD_ID)
public final class LimboCommand {
    private static final int STAFF_LEVEL = 2;

    /** Versao do formato de saida. O bot pode usar para nao quebrar quando as chaves mudarem. */
    private static final String FORMAT = "v1";

    private static final int AUDIT_DEFAULT = 10;

    private LimboCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        // Raiz separada, sem exigencia de permissao, de proposito: e ela que a escolha do dialogo do
        // ADM roda (commands: ["oraculo"]), e o dialogo acontece na mao de jogador comum. A trava nao
        // e de permissao, e de posicao — LimboNetwork.openOracle so responde perto de um Oraculo.
        event.getDispatcher().register(Commands.literal("oraculo")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayer();
                    if (player == null) return 0;
                    LimboNetwork.openOracle(player);
                    return 1;
                }));

        event.getDispatcher().register(Commands.literal("limbo")
                .requires(source -> source.hasPermission(STAFF_LEVEL))
                .executes(LimboCommand::report)
                .then(Commands.literal("finale")
                        .then(Commands.literal("previa").executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            com.aurorion.limbo.finale.FinaleManager.preview(player);
                            return 1;
                        })))
                .then(Commands.literal("relatorio")
                        .executes(LimboCommand::report))
                .then(Commands.literal("esquecidos")
                        .executes(LimboCommand::forgotten))
                .then(Commands.literal("auditoria")
                        .executes(context -> audit(context, AUDIT_DEFAULT))
                        .then(Commands.argument("linhas", IntegerArgumentType.integer(1, 200))
                                .executes(context -> audit(context,
                                        IntegerArgumentType.getInteger(context, "linhas")))))
                .then(Commands.literal("tentativa")
                        .then(Commands.argument("jogador", GameProfileArgument.gameProfile())
                                .executes(LimboCommand::markAttempt)))
                .then(Commands.literal("prazo")
                        .then(Commands.argument("jogador", GameProfileArgument.gameProfile())
                                .then(Commands.argument("horas", IntegerArgumentType.integer(0, 24 * 14))
                                        .executes(context -> setDeadline(context,
                                                IntegerArgumentType.getInteger(context, "horas")))))));
    }

    /**
     * O estado inteiro do Limbo, uma linha por pessoa.
     *
     * <p>Sempre imprime a linha de cabecalho, mesmo com o Limbo vazio: um bot que receba resposta
     * vazia nao consegue distinguir "ninguem la" de "o comando falhou".
     */
    private static int report(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        LimboData data = LimboData.get(server);
        Map<UUID, ExileRecord> active = data.active();

        source.sendSuccess(() -> literal("limbo " + FORMAT
                + " exilados=" + active.size()
                + " agora=" + System.currentTimeMillis() / 1000L), false);

        for (Map.Entry<UUID, ExileRecord> entry : active.entrySet()) {
            UUID id = entry.getKey();
            ExileRecord record = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(id);

            StringBuilder line = new StringBuilder("exilado")
                    .append(" nome=").append(record.lastName())
                    .append(" uuid=").append(id)
                    .append(" personagem=").append(com.aurorion.core.character.CharacterData.get(server).current(id).id())
                    .append(" online=").append(player != null ? "sim" : "nao")
                    .append(" vidas=").append(LivesManager.livesOf(server, id))
                    .append(" retorno_pendente=").append(LivesManager.isExiled(server, id) ? "nao" : "sim")
                    .append(" prazo_s=").append(record.remainingMillis() / 1000L)
                    .append(" vencido=").append(record.remainingMillis() <= 0L ? "sim" : "nao")
                    .append(" resgate_tentado=").append(record.rescueAttempted() ? "sim" : "nao")
                    .append(" esquecido=").append(data.forgottenExits(id));

            if (!record.doorArmed()) {
                line.append(" porta=fechada");
            } else if (record.doorPos() == null) {
                // So da para medir a caminhada de quem esta online: a estatistica vive no jogador.
                String walked = player != null
                        ? String.valueOf(record.walkedSince(ForgottenDoor.walkedCm(player)))
                        : "?";
                line.append(" porta=armada andou=").append(walked).append('/').append(record.doorTarget());
            } else {
                line.append(" porta=aberta em=").append(record.doorPos().toShortString().replace(", ", ","));
            }

            String text = line.toString();
            source.sendSuccess(() -> literal(text), false);
        }
        com.aurorion.core.character.CharacterData.get(server).characters().forEach((account, character) -> {
            if (!character.dead()) return;
            source.sendSuccess(() -> literal("personagem uuid=" + account
                    + " personagem=" + character.id() + " estado=morto morto_em=" + character.diedAt()
                    + " epilogo_pendente=" + (com.aurorion.limbo.finale.FinaleData.get(server).record(account) != null ? "sim" : "nao")), false);
        });
        return active.size();
    }

    /** Quem ja saiu sozinho, e quantas vezes. E daqui que sai o gancho de RP. */
    private static int forgotten(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Map<UUID, Integer> all = LimboData.get(source.getServer()).forgotten();

        source.sendSuccess(() -> literal("limbo " + FORMAT + " esquecidos=" + all.size()), false);

        all.forEach((id, count) -> source.sendSuccess(() -> literal("esquecido"
                + " uuid=" + id
                + " nome=" + nameOf(source.getServer(), id)
                + " vezes=" + count), false));

        return all.size();
    }

    private static int audit(CommandContext<CommandSourceStack> context, int lines) {
        CommandSourceStack source = context.getSource();
        var tail = AuditLog.tail(source.getServer(), lines);

        source.sendSuccess(() -> literal("limbo " + FORMAT + " auditoria=" + tail.size()
                + " arquivo=" + AuditLog.path(source.getServer())), false);

        // As linhas ja sao JSON: saem como estao, para o bot poder dar parse direto no que leu.
        for (String line : tail) {
            source.sendSuccess(() -> literal(line), false);
        }
        return tail.size();
    }

    /**
     * Marca que alguem tentou buscar esta pessoa, o que desliga a Porta do Esquecido para ela.
     *
     * <p>Hoje isto e manual porque o ritual de resgate ainda nao existe. Quando existir, ele chama
     * {@code LimboManager.markRescueAttempt} e este comando vira o que deveria ser: a correcao na mao
     * para quando a staff souber de uma tentativa que o sistema nao viu.
     */
    private static int markAttempt(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        int changed = 0;

        for (GameProfile profile : profiles(context)) {
            if (LimboManager.markRescueAttempt(source.getServer(), profile.getId())) {
                changed++;
                source.sendSuccess(() -> literal("tentativa registrada nome=" + profile.getName()), true);
            } else {
                source.sendSuccess(() -> literal("sem efeito nome=" + profile.getName()
                        + " motivo=nao_esta_no_limbo_ou_ja_marcado"), false);
            }
        }
        return changed;
    }

    /** Ajuste de prazo na mao. Zero encerra o personagem imediatamente. */
    private static int setDeadline(CommandContext<CommandSourceStack> context, int hours)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        LimboData data = LimboData.get(source.getServer());
        int changed = 0;

        for (GameProfile profile : profiles(context)) {
            ExileRecord record = data.record(profile.getId());
            if (record == null) {
                source.sendSuccess(() -> literal("sem efeito nome=" + profile.getName()
                        + " motivo=nao_esta_no_limbo"), false);
                continue;
            }

            if (!LimboManager.setDeadline(source.getServer(), profile.getId(), hours * 60L * 60L * 1000L)) {
                source.sendFailure(literal("sem efeito motivo=personagem_morto_ou_prazo_vencido"));
                continue;
            }
            changed++;

            source.sendSuccess(() -> literal("prazo ajustado nome=" + profile.getName()
                    + " prazo_s=" + record.remainingMillis() / 1000L), true);
        }
        return changed;
    }

    private static Collection<GameProfile> profiles(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        return GameProfileArgument.getGameProfiles(context, "jogador");
    }

    private static String nameOf(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        String remembered = LimboData.get(server).forgottenName(id);
        if (remembered != null) return remembered;
        return server.getProfileCache() == null
                ? "?"
                : server.getProfileCache().get(id).map(GameProfile::getName).orElse("?");
    }

    private static Component literal(String text) {
        return Component.literal(text);
    }
}
