package com.aurorion.essentials.death;

import com.aurorion.core.character.CharacterData;
import com.aurorion.core.death.DeathClaims;
import com.aurorion.core.level.SafeSpot;
import com.aurorion.essentials.AurorionEssentials;
import com.aurorion.essentials.fakename.CharacterTarget;
import com.aurorion.essentials.fakename.LegacyColorCodes;
import com.aurorion.essentials.mixin.PlayerListSaveInvoker;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** All paths require OP 2 on dispatch AND on the callback after asynchronous IO. */
@EventBusSubscriber(modid = AurorionEssentials.MOD_ID)
public final class DeathHistoryCommand {
    private DeathHistoryCommand() { }

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("deathhistory")
                .requires(source -> source.hasPermission(2))
                .executes(c -> help(c.getSource()))
                .then(Commands.argument("player", StringArgumentType.string()).suggests(NAMES)
                        .executes(c -> list(c, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(c -> list(c, page(c)))))
                // Duas formas para ver e teleportar: por id (o que os botoes do chat clicam) e por
                // pessoa + numero da linha (o que da para digitar no console, sem UUID nenhuma).
                .then(Commands.literal("view")
                        .then(Commands.argument("id", UuidArgument.uuid())
                                .executes(c -> view(c, UuidArgument.getUuid(c, "id"), 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(c -> view(c, UuidArgument.getUuid(c, "id"), page(c)))))
                        .then(Commands.argument("player", StringArgumentType.string()).suggests(NAMES)
                                .then(Commands.argument("morte", IntegerArgumentType.integer(1))
                                        .executes(c -> byIndex(c, id -> view(c, id, 1))))))
                .then(Commands.literal("tp")
                        .then(Commands.argument("id", UuidArgument.uuid())
                                .executes(c -> teleport(c, UuidArgument.getUuid(c, "id"))))
                        .then(Commands.argument("player", StringArgumentType.string()).suggests(NAMES)
                                .then(Commands.argument("morte", IntegerArgumentType.integer(1))
                                        .executes(c -> byIndex(c, id -> teleport(c, id))))))
                // Devolver ao dono e pegar para si: o que caiu na morte, num passo so, sem sobrescrever
                // nada. Mesmas duas formas de apontar a morte, e o mesmo 'confirm [duplicar]'.
                .then(recovery("devolver", true))
                .then(recovery("pegar", false))
                .then(Commands.literal("restore").then(Commands.argument("id", UuidArgument.uuid())
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.literal("confirm").executes(c -> restore(c, -1, false))
                                        .then(Commands.literal(DUPLICATE).executes(c -> restore(c, -1, true)))))))
                .then(Commands.literal("give").then(Commands.argument("id", UuidArgument.uuid())
                        .then(Commands.argument("item", IntegerArgumentType.integer(0))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .then(Commands.literal("confirm").executes(c -> restore(c, item(c), false))
                                                .then(Commands.literal(DUPLICATE).executes(c -> restore(c, item(c), true)))))))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> recovery(String name, boolean toOwner) {
        return Commands.literal(name)
                .then(Commands.argument("id", UuidArgument.uuid())
                        .then(Commands.literal("confirm")
                                .executes(c -> recover(c, UuidArgument.getUuid(c, "id"), toOwner, false))
                                .then(Commands.literal(DUPLICATE)
                                        .executes(c -> recover(c, UuidArgument.getUuid(c, "id"), toOwner, true)))))
                .then(Commands.argument("player", StringArgumentType.string()).suggests(NAMES)
                        .then(Commands.argument("morte", IntegerArgumentType.integer(1))
                                .then(Commands.literal("confirm")
                                        .executes(c -> byIndex(c, id -> recover(c, id, toOwner, false)))
                                        .then(Commands.literal(DUPLICATE)
                                                .executes(c -> byIndex(c, id -> recover(c, id, toOwner, true)))))));
    }

    /**
     * Segunda confirmacao, exigida so quando o espolio da morte ja voltou por outro caminho (o
     * Relicario do Limbo). A palavra diz o que acontece, para ninguem digitar sem ler.
     */
    private static final String DUPLICATE = "duplicar";

    private static int page(CommandContext<CommandSourceStack> c) { return IntegerArgumentType.getInteger(c, "page"); }
    private static int item(CommandContext<CommandSourceStack> c) { return IntegerArgumentType.getInteger(c, "item"); }

    // --- Achar a pessoa -------------------------------------------------------------------------
    //
    // Os passos 1 a 5 (UUID, personagem online, nick online, personagem offline, nick offline) sao
    // os de CharacterTarget, compartilhados com os outros comandos de staff. Este comando acrescenta
    // o passo 6: um nome usado em alguma morte salva (indice do DeathHistoryStore) — pega ate quem ja
    // trocou de nome depois de morrer. A consulta por nick offline roda em um executor separado;
    // depois, o passo 6 e a leitura do historico usam a mesma ida a fila de IO do historico.

    /** Nome com espaco precisa de aspas: o Tab ja sugere com elas para nao dar trabalho. */
    private static final SuggestionProvider<CommandSourceStack> NAMES = CharacterTarget.SUGGESTIONS;

    /** A conta e a lista de mortes dela, do jeito que o historico as guarda. */
    private record Found(UUID owner, ListTag entries) { }

    /** Um registro e os recibos de recuperacao dele, lidos na mesma ida a fila de IO. */
    private record Loaded(CompoundTag snapshot, CompoundTag journal) { }

    /**
     * Resolve {@code player} (argumento do comando) e entrega a lista de mortes. O passo que le disco
     * roda na fila de IO; o resultado volta na thread do servidor.
     */
    private static void lookup(CommandContext<CommandSourceStack> c, Consumer<Found> then) {
        CommandSourceStack source = c.getSource();
        String query = StringArgumentType.getString(c, "player");
        DeathHistoryStore repository = DeathHistoryEvents.store(source.getServer());
        CharacterTarget.resolve(source, query, known -> async(source, () -> {
            UUID owner = known != null ? known : repository.findByName(query);
            return owner == null ? null : new Found(owner, repository.list(owner));
        }, found -> {
            if (found == null) {
                say(source, "Nao achei ninguem chamado \"" + query + "\". Aceita nome de personagem, nick da "
                        + "conta ou UUID; nome com espaco vai entre aspas.");
                return;
            }
            if (found.entries().isEmpty()) { say(source, "Nenhuma morte salva para este jogador."); return; }
            then.accept(found);
        }));
    }

    /** O que {@code view}, {@code tp}, {@code devolver} e {@code pegar} fazem depois de saber o id. */
    private interface WithDeath {
        void run(UUID id) throws CommandSyntaxException;
    }

    /** Pelo numero da linha: 1 e a morte mais recente. */
    private static int byIndex(CommandContext<CommandSourceStack> c, WithDeath then) {
        int wanted = IntegerArgumentType.getInteger(c, "morte");
        lookup(c, found -> {
            if (wanted > found.entries().size()) {
                say(c.getSource(), "Essa pessoa tem " + found.entries().size() + " morte(s) salva(s).");
                return;
            }
            try {
                then.run(found.entries().getCompound(wanted - 1).getUUID("Id"));
            } catch (CommandSyntaxException e) {
                // A causa hoje: 'view'/'tp'/'pegar' pedem um jogador, e quem chamou foi o console.
                c.getSource().sendFailure(Component.literal(e.getMessage()));
            }
        });
        return 1;
    }

    /** O nome que a linha da lista mostra: personagem na hora da morte, ou o nick da conta. */
    private static Component nameOf(CompoundTag row) {
        if (row.contains("FakeName")) return LegacyColorCodes.parse(row.getString("FakeName"));
        if (row.contains("CharacterName")) return Component.literal(row.getString("CharacterName"));
        return Component.literal(row.getString("Name"));
    }

    private static int help(CommandSourceStack source) {
        say(source, "/deathhistory <pessoa> [pagina] — mortes salvas, inclusive de quem esta offline.\n"
                + "  <pessoa> e o nome do personagem, o nick da conta ou a UUID. Nome com espaco entre\n"
                + "  aspas: /deathhistory \"Bella Noob\". O Tab sugere os nomes conhecidos.\n"
                + "  <morte> abaixo e o numero da linha da lista (1 = a mais recente); <id> e o do registro.\n"
                + "/deathhistory view <pessoa> <morte> | view <id> [aba] — abre a morte numa tela: inventario,\n"
                + "  Curios/Accessories e ender chest. No modo criativo, clicar num item pega o item.\n"
                + "/deathhistory tp <pessoa> <morte> | tp <id> — vai ao local da morte\n"
                + "/deathhistory devolver <pessoa> <morte> confirm | devolver <id> confirm — devolve ao dono o que\n"
                + "  caiu na morte, cada item no slot de origem quando ele esta vazio. Nao sobrescreve nada.\n"
                + "/deathhistory pegar <pessoa> <morte> confirm | pegar <id> confirm — o mesmo, para o seu inventario.\n"
                + "/deathhistory give <id> <indice> <destinatario> confirm — recupera uma pilha (indice no tooltip)\n"
                + "/deathhistory restore <id> <destinatario> confirm — substitui inventario, ender chest, Curios,\n"
                + "  Accessories, efeitos, XP e fome; cria backup primeiro.\n"
                + "Nada recolhe drops do mundo. Cada item sai uma unica vez por registro, por qualquer caminho;\n"
                + "devolver/pegar de novo entrega so o que faltou (por falta de espaco, por exemplo).\n"
                + "Itens mantidos na morte (Fio da Volta, Relicario) e o ender chest nunca sao devolvidos.\n"
                + "Se o Relicario ja chamou o espolio de volta, os comandos exigem 'confirm " + DUPLICATE + "'.");
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c, int page) {
        CommandSourceStack source = c.getSource();
        lookup(c, found -> {
            ListTag entries = found.entries();
            UUID owner = found.owner();
            DeathClaims claims = DeathClaims.get(source.getServer());
            int pages = (entries.size() + 7) / 8;
            if (page > pages) { say(source, "Pagina inexistente. Total: " + pages); return; }
            say(source, "Historico de mortes — pagina " + page + "/" + pages + " | conta " + owner);
            for (int i = (page - 1) * 8; i < Math.min(entries.size(), page * 8); i++) {
                CompoundTag row = entries.getCompound(i);
                String id = row.getUUID("Id").toString();
                // O numero e o mesmo que 'view <pessoa> <n>' aceita: a lista e o indice do comando.
                var message = Component.literal("#" + (i + 1) + " ").withStyle(ChatFormatting.DARK_GRAY)
                        // O nome de quem morreu, como ele aparecia na hora — nao como aparece hoje.
                        .append(nameOf(row))
                        .append(Component.literal(" " + Instant.ofEpochMilli(row.getLong("Time")) + " | "
                                + row.getString("Cause")).withStyle(ChatFormatting.GRAY))
                        .append(DeathHistoryEvents.button(" [Ver]", "/deathhistory view " + id, id))
                        .append(DeathHistoryEvents.button(" [TP]", "/deathhistory tp " + id, row.getString("Dimension")));
                if (row.contains("CaptureError")) message.append(Component.literal(" [snapshot parcial]").withStyle(ChatFormatting.RED));
                else {
                    if (!row.getBoolean("CuriosComplete")) message.append(Component.literal(" [Curios incompleto]").withStyle(ChatFormatting.RED));
                    if (row.contains("AccessoriesComplete") && !row.getBoolean("AccessoriesComplete"))
                        message.append(Component.literal(" [Accessories incompleto]").withStyle(ChatFormatting.RED));
                }
                DeathClaims.Claim claim = claims.find(row.getUUID("Id"));
                if (claim != null) message.append(Component.literal(" [espolio ja voltou: " + claim.stacks() + " pilha(s)]")
                        .withStyle(ChatFormatting.GOLD));
                source.sendSuccess(() -> message, false);
            }
            if (page < pages) source.sendSuccess(() -> DeathHistoryEvents.button("[Proxima pagina]",
                    "/deathhistory " + owner + " " + (page + 1), "Continuar historico"), false);
        });
        return 1;
    }

    /**
     * Abre a tela da morte. {@code tab} escolhe a aba inicial: 1 inventario, 2 Curios e acessorios,
     * 3 ender chest (era o numero da pagina de 54 slots antes da tela com abas).
     */
    private static int view(CommandContext<CommandSourceStack> c, UUID id, int tab) throws CommandSyntaxException {
        CommandSourceStack source = c.getSource();
        ServerPlayer admin = source.getPlayerOrException();
        DeathHistoryStore repository = DeathHistoryEvents.store(source.getServer());
        async(source, () -> new Loaded(repository.read(id), repository.journal(id)), loaded -> {
            CompoundTag snapshot = loaded.snapshot();
            if (snapshot.contains("CaptureError")) {
                say(source, "Snapshot parcial: " + snapshot.getString("CaptureError"));
                return;
            }
            if (admin.hasDisconnected()) return;
            DeathHistoryMenu.Section section = tab <= 1 ? DeathHistoryMenu.Section.INVENTORY
                    : tab == 2 ? DeathHistoryMenu.Section.ACCESSORIES : DeathHistoryMenu.Section.ENDER;
            admin.openMenu(new SimpleMenuProvider((window, inventory, player) ->
                    new DeathHistoryMenu(window, inventory, admin, id, snapshot, loaded.journal(), section),
                    Component.literal("Morte: " + DeathHistoryMenu.displayName(snapshot))));

            say(source, "Registro " + id + " | " + snapshot.getString("Dimension") + " | XP " + snapshot.getInt("XpLevel")
                    + " | " + snapshot.getString("Cause"));
            DeathClaims.Claim claim = DeathClaims.get(source.getServer()).find(id);
            if (claim != null) source.sendSuccess(() -> Component.literal(claimWarning(claim)).withStyle(ChatFormatting.GOLD), false);
            String confirm = claim != null ? " confirm " + DUPLICATE : " confirm";
            MutableComponent actions = Component.literal("Acoes:").withStyle(ChatFormatting.GRAY)
                    .append(suggest(" [Devolver ao dono]", "/deathhistory devolver " + id + confirm,
                            "Devolve o que caiu na morte, no slot de origem quando vazio. Precisa da pessoa online."))
                    .append(suggest(" [Pegar para mim]", "/deathhistory pegar " + id + confirm,
                            "Coloca o que caiu na morte no seu inventario, para entregar em maos."))
                    .append(DeathHistoryEvents.button(" [TP]", "/deathhistory tp " + id, "Ir ao local da morte"));
            source.sendSuccess(() -> actions, false);
            say(source, admin.isCreative()
                    ? "Criativo: clique num item da tela para pega-lo. O indice (#n) do give esta no tooltip."
                    : "Somente consulta. O indice (#n) do give esta no tooltip; no criativo da para pegar clicando.");
        });
        return 1;
    }

    private static int teleport(CommandContext<CommandSourceStack> c, UUID id) throws CommandSyntaxException {
        ServerPlayer admin = c.getSource().getPlayerOrException();
        read(c.getSource(), id, snapshot -> {
            ServerLevel level = admin.server.getLevel(ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(snapshot.getString("Dimension"))));
            if (level == null) throw new IllegalStateException("Dimensao da morte indisponivel.");
            double x = snapshot.getDouble("X"), y = snapshot.getDouble("Y"), z = snapshot.getDouble("Z");
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalStateException("Coordenadas invalidas no snapshot.");
            BlockPos at = BlockPos.containing(x, y, z);
            if (!level.getWorldBorder().isWithinBounds(at)) throw new IllegalStateException("Local fora da borda atual.");
            if (!admin.isSpectator()) {
                BlockPos safe = SafeSpot.nearestVertical(level, at, 16);
                if (safe == null) throw new IllegalStateException("Sem lugar seguro na coluna da morte. Use modo espectador para visitar o ponto exato.");
                x = safe.getX() + .5; y = safe.getY(); z = safe.getZ() + .5;
            }
            admin.stopRiding();
            admin.teleportTo(level, x, y, z, Set.of(), snapshot.getFloat("Yaw"), snapshot.getFloat("Pitch"));
            admin.setDeltaMovement(Vec3.ZERO); admin.fallDistance = 0;
        });
        return 1;
    }

    // --- Devolver / pegar -----------------------------------------------------------------------

    /**
     * Entrega o que caiu na morte: ao dono ({@code devolver}) ou a quem pediu ({@code pegar}).
     *
     * <p>Mesmo desenho do {@code restore}: le o registro, planeja na thread do servidor, reserva os
     * recibos na fila de IO (com backup do destinatario), confere que o inventario nao mudou e so entao
     * entrega. Como so ocupa slot vazio, desfazer e esvaziar de novo o que foi ocupado.
     */
    private static int recover(CommandContext<CommandSourceStack> c, UUID id, boolean toOwner, boolean duplicate)
            throws CommandSyntaxException {
        CommandSourceStack source = c.getSource();
        ServerPlayer self = toOwner ? null : source.getPlayerOrException();
        String kind = toOwner ? "devolver" : "pegar";
        DeathHistoryStore repository = DeathHistoryEvents.store(source.getServer());
        async(source, () -> new Loaded(repository.read(id), repository.journal(id)), loaded -> {
            CompoundTag snapshot = loaded.snapshot();
            if (snapshot.contains("CaptureError")) throw new IllegalStateException("Registro sem itens: " + snapshot.getString("CaptureError"));
            if (snapshot.getInt("DataVersion") != net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion())
                throw new IllegalStateException("Snapshot de outra versao do Minecraft. Requer migracao antes de recuperar.");
            DeathClaims.Claim claim = DeathClaims.get(source.getServer()).find(id);
            if (claim != null && !duplicate) {
                source.sendFailure(Component.literal(claimWarning(claim)
                        + " Entregar agora cria uma segunda copia do que voltou. Para seguir mesmo assim,"
                        + " repita o comando terminando em 'confirm " + DUPLICATE + "'."));
                return;
            }
            if (loaded.journal().contains("full") || RecoveryLedger.claimed(id, RecoveryLedger.FULL))
                throw new IllegalStateException("Este registro ja foi restaurado por completo (restore). Nada a entregar.");
            String who = DeathHistoryMenu.displayName(snapshot);
            ServerPlayer target = toOwner ? source.getServer().getPlayerList().getPlayer(snapshot.getUUID("Owner")) : self;
            if (target == null) throw new IllegalStateException(who + " (" + snapshot.getString("Name") + ") esta offline."
                    + " Devolver precisa da pessoa conectada; ou use 'pegar' e entregue em maos.");
            requireReady(target);

            Set<Integer> done = recoveredIn(loaded.journal());
            RecoveryPlan plan = RecoveryPlan.build(snapshot, target, toOwner, index -> done.contains(index) || RecoveryLedger.claimed(id, index));
            if (plan.placements.isEmpty()) {
                report(source, plan, 0, target, toOwner, snapshot);
                return;
            }
            int[] wanted = plan.entries();
            if (!RecoveryLedger.claim(id, wanted))
                throw new IllegalStateException("Outra recuperacao deste registro esta em andamento. Tente de novo em instantes.");
            CompoundTag backup;
            try {
                backup = DeathSnapshot.capture(target, "Backup antes de " + kind + " " + id);
                if (backup.sizeInBytes() > 16L * 1024 * 1024)
                    throw new IllegalStateException("Backup excede 16 MiB; nenhuma alteracao foi realizada.");
            } catch (RuntimeException e) {
                RecoveryLedger.release(id, wanted);
                throw e;
            }
            String actor = source.getTextName();
            UUID targetId = target.getUUID();
            async(source, () -> repository.reserveItems(id, wanted, backup, kind, actor, targetId), reserved -> {
                if (reserved.length == 0) {
                    say(source, "Tudo o que faltava ja tinha sido recuperado por outro caminho. Nada foi entregue.");
                    return;
                }
                Set<Integer> chosen = new HashSet<>();
                for (int entry : reserved) chosen.add(entry);
                String status = "ABORTED";
                List<Runnable> undo = new ArrayList<>();
                try {
                    requireReady(target);
                    CompoundTag now = DeathSnapshot.capture(target, "Comparacao antes de " + kind);
                    if (!sameInventory(backup, now)) throw new IllegalStateException("O inventario de " + target.getGameProfile().getName()
                            + " mudou enquanto a reserva era gravada. Nada foi entregue; repita o comando.");
                    status = "FAILED";
                    int delivered = 0;
                    for (RecoveryPlan.Placement placement : plan.placements) {
                        if (!chosen.contains(placement.entry())) continue;
                        placement.apply().run();
                        undo.add(placement.undo());
                        delivered++;
                    }
                    target.inventoryMenu.broadcastChanges();
                    ((PlayerListSaveInvoker) source.getServer().getPlayerList()).aurorion_essentials$savePlayer(target);
                    status = "APPLIED";
                    report(source, plan, delivered, target, toOwner, snapshot);
                    if (toOwner) target.sendSystemMessage(Component.literal("A staff devolveu " + delivered
                            + " item(ns) da sua morte.").withStyle(ChatFormatting.GREEN));
                    AurorionEssentials.LOGGER.info("Death history {} {} itens={} admin={} target={} backup={} duplicar={}",
                            kind, id, Arrays.toString(reserved), actor, targetId, backup.getUUID("Id"), claim != null);
                } catch (RuntimeException e) {
                    if (status.equals("FAILED")) {
                        try {
                            for (int i = undo.size() - 1; i >= 0; i--) undo.get(i).run();
                            target.inventoryMenu.broadcastChanges();
                            ((PlayerListSaveInvoker) source.getServer().getPlayerList()).aurorion_essentials$savePlayer(target);
                        } catch (Exception rollbackFailure) {
                            AurorionEssentials.LOGGER.error("Death history {} rollback failed; backup {}", kind, backup.getUUID("Id"), rollbackFailure);
                        }
                    }
                    throw e;
                } finally {
                    String outcome = status;
                    if (outcome.equals("ABORTED")) RecoveryLedger.release(id, reserved);
                    finishLater(repository, id, reserved, outcome);
                }
            }, reserved -> {
                // A volta do IO nao chegou a entregar nada: quem pediu perdeu o OP/caiu, ou a reserva
                // falhou. Os itens voltam a poder ser recuperados.
                RecoveryLedger.release(id, wanted);
                if (reserved != null && reserved.length > 0) finishLater(repository, id, reserved, "ABORTED");
            });
        });
        return 1;
    }

    private static void report(CommandSourceStack source, RecoveryPlan plan, int delivered, ServerPlayer target,
                               boolean toOwner, CompoundTag snapshot) {
        String who = DeathHistoryMenu.displayName(snapshot);
        StringBuilder text = new StringBuilder(toOwner
                ? "Devolvido(s) " + delivered + " item(ns) a " + who + " (" + target.getGameProfile().getName() + ")."
                : "Voce pegou " + delivered + " item(ns) da morte de " + who + ".");
        if (plan.held > 0) text.append(' ').append(plan.held).append(" ja estava(m) com a pessoa, no mesmo slot.");
        if (plan.recovered > 0) text.append(' ').append(plan.recovered).append(" ja tinha(m) sido recuperado(s) antes.");
        if (plan.kept > 0) text.append(' ').append(plan.kept).append(" mantido(s) na morte, nao volta(m).");
        if (plan.broken > 0) text.append(' ').append(plan.broken).append(" nao pode(m) ser lido(s) (mod removido?).");
        if (toOwner && snapshot.hasUUID("Character")) {
            CharacterData.Character now = CharacterData.get(source.getServer()).find(snapshot.getUUID("Owner"));
            if (now != null && !now.id().equals(snapshot.getUUID("Character")))
                text.append(" Atencao: o personagem atual da conta nao e o que morreu.");
        }
        source.sendSuccess(() -> Component.literal(text.toString()).withStyle(delivered > 0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
        if (!plan.leftover.isEmpty()) {
            MutableComponent missing = Component.literal("Sem espaco para " + plan.leftover.size() + " item(ns): ").withStyle(ChatFormatting.GOLD);
            for (int i = 0; i < plan.leftover.size(); i++) {
                if (i > 0) missing.append(", ");
                missing.append(plan.leftover.get(i).copy());
            }
            missing.append(". Libere espaco e repita o comando: so o que faltou sera entregue.");
            source.sendSuccess(() -> missing, false);
        }
    }

    private static Set<Integer> recoveredIn(CompoundTag journal) {
        Set<Integer> recovered = new HashSet<>();
        for (String key : journal.getAllKeys()) {
            if (!key.startsWith("item_")) continue;
            try { recovered.add(Integer.parseInt(key.substring("item_".length()))); }
            catch (NumberFormatException ignored) { }
        }
        return recovered;
    }

    private static void finishLater(DeathHistoryStore repository, UUID id, int[] items, String outcome) {
        try {
            repository.submit(() -> {
                try { repository.finishItems(id, items, outcome); }
                catch (Exception e) { AurorionEssentials.LOGGER.error("Restore journal completion failed", e); }
            });
        } catch (RuntimeException e) { AurorionEssentials.LOGGER.error("Restore journal queue full", e); }
    }

    // --- Restore / give -------------------------------------------------------------------------

    private static int restore(CommandContext<CommandSourceStack> c, int item, boolean duplicate) throws CommandSyntaxException {
        CommandSourceStack source = c.getSource();
        ServerPlayer target = EntityArgument.getPlayer(c, "target");
        UUID id = UuidArgument.getUuid(c, "id");
        read(source, id, snapshot -> {
            // Conferido na volta do IO, na thread do servidor: um Relicario usado enquanto o snapshot
            // era lido ja aparece aqui.
            DeathClaims.Claim claim = DeathClaims.get(source.getServer()).find(id);
            if (claim != null && !duplicate) {
                source.sendFailure(Component.literal(claimWarning(claim)
                        + " Restaurar agora cria uma segunda copia do que voltou. Para seguir mesmo assim,"
                        + " repita o comando terminando em 'confirm " + DUPLICATE + "'."));
                return;
            }
            requireReady(target);
            if (snapshot.getInt("DataVersion") != net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion())
                throw new IllegalStateException("Snapshot de outra versao do Minecraft. Requer migracao antes de restaurar.");
            CompoundTag backup = DeathSnapshot.capture(target, "Backup antes de restaurar " + id);
            if (backup.sizeInBytes() > 16L * 1024 * 1024)
                throw new IllegalStateException("Backup excede 16 MiB; nenhuma alteracao foi realizada.");
            Runnable action;
            Runnable rollback;
            if (item < 0) {
                try {
                    DeathSnapshot.prepareFull(snapshot, target);
                    DeathSnapshot.prepareFull(backup, target);
                }
                catch (ReflectiveOperationException e) { throw new IllegalStateException("Curios/Accessories incompativel; nenhuma alteracao.", e); }
                action = () -> applyFull(snapshot, target);
                rollback = () -> applyFull(backup, target);
            } else {
                ListTag entries = snapshot.getList("Items", Tag.TAG_COMPOUND);
                if (item >= entries.size()) throw new IllegalStateException("Indice de item inexistente.");
                ItemStack stack = DeathSnapshot.item(entries.getCompound(item), target);
                if (stack.isEmpty()) throw new IllegalStateException("Este slot estava vazio.");
                if (DeathSnapshot.keptOnDeath(entries.getCompound(item), stack))
                    throw new IllegalStateException("Este item nao cai na morte: o jogador ja ficou com ele.");
                int empty = target.getInventory().getFreeSlot();
                if (empty < 0) throw new IllegalStateException("O destinatario precisa de um slot vazio.");
                action = () -> {
                    target.getInventory().setItem(empty, stack.copy());
                    target.getInventory().setChanged(); target.inventoryMenu.broadcastChanges();
                };
                rollback = () -> {
                    target.getInventory().setItem(empty, ItemStack.EMPTY);
                    target.getInventory().setChanged(); target.inventoryMenu.broadcastChanges();
                };
            }
            // Trava na thread do servidor antes da fila de IO: a tela do criativo nao espera disco.
            int claimKey = item < 0 ? RecoveryLedger.FULL : item;
            if (!RecoveryLedger.claim(id, claimKey))
                throw new IllegalStateException(item < 0
                        ? "Ja saiu item deste registro (give/devolver/pegar/criativo); a restauracao completa duplicaria."
                        : "Este item ja foi recuperado, ou esta sendo agora.");
            DeathHistoryStore repository = DeathHistoryEvents.store(source.getServer());
            String actor = source.getTextName();
            UUID targetId = target.getUUID();
            async(source, () -> {
                repository.reserve(id, item, backup, actor, targetId);
                return true;
            }, ignored -> {
                String status = "ABORTED";
                try {
                    requireReady(target);
                    CompoundTag now = DeathSnapshot.capture(target, "Comparacao antes da restauracao");
                    if (!sameInventory(backup, now)) throw new IllegalStateException("Inventario/XP mudou durante o backup. Restauracao abortada; repita o comando.");
                    status = "FAILED";
                    action.run();
                    // Persist the destination immediately; the durable reservation prevents a duplicate retry after a crash.
                    ((PlayerListSaveInvoker) source.getServer().getPlayerList()).aurorion_essentials$savePlayer(target);
                    status = "APPLIED";
                    say(source, "Recuperacao concluida para " + target.getGameProfile().getName() + ". Backup: " + backup.getUUID("Id"));
                    AurorionEssentials.LOGGER.info("Death history restore {} item={} admin={} target={} backup={} duplicar={}",
                            id, item, source.getTextName(), target.getUUID(), backup.getUUID("Id"), claim != null);
                } catch (RuntimeException e) {
                    if (status.equals("FAILED")) {
                        try {
                            rollback.run();
                            ((PlayerListSaveInvoker) source.getServer().getPlayerList()).aurorion_essentials$savePlayer(target);
                        }
                        catch (Exception rollbackFailure) { AurorionEssentials.LOGGER.error("Restore rollback failed; backup {}", backup.getUUID("Id"), rollbackFailure); }
                    }
                    throw e;
                } finally {
                    String outcome = status;
                    if (outcome.equals("ABORTED")) RecoveryLedger.release(id, claimKey);
                    finishLater(repository, id, new int[]{item}, outcome);
                }
            }, reserved -> {
                RecoveryLedger.release(id, claimKey);
                if (reserved != null) finishLater(repository, id, new int[]{item}, "ABORTED");
            });
        });
        return 1;
    }

    private static String claimWarning(DeathClaims.Claim claim) {
        return "O espolio desta morte ja voltou (" + claim.source() + ", " + claim.stacks() + " pilha(s), "
                + Instant.ofEpochMilli(claim.claimedAt()) + ").";
    }

    private static void applyFull(CompoundTag snapshot, ServerPlayer target) {
        try { DeathSnapshot.prepareFull(snapshot, target).run(); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Curios/Accessories indisponivel durante a restauracao.", e); }
    }

    private static boolean sameInventory(CompoundTag a, CompoundTag b) {
        return Objects.equals(a.get("Items"), b.get("Items")) && a.getInt("XpLevel") == b.getInt("XpLevel")
                && a.getInt("XpTotal") == b.getInt("XpTotal") && a.getFloat("XpProgress") == b.getFloat("XpProgress");
    }

    private static void requireReady(ServerPlayer target) {
        if (!target.isAlive() || target.hasDisconnected() || target.server.getPlayerList().getPlayer(target.getUUID()) != target)
            throw new IllegalStateException("Destinatario precisa estar vivo e conectado.");
        if (target.containerMenu != target.inventoryMenu || !target.containerMenu.getCarried().isEmpty())
            throw new IllegalStateException("Destinatario precisa fechar o menu e esvaziar o cursor.");
    }

    private static boolean authorized(CommandSourceStack source) {
        if (!source.hasPermission(2)) return false;
        if (source.getEntity() instanceof ServerPlayer player) {
            return player.hasPermissions(2) && !player.hasDisconnected()
                    && source.getServer().getPlayerList().getPlayer(player.getUUID()) == player;
        }
        return true;
    }

    private interface Read<T> { T get() throws Exception; }

    private static <T> void async(CommandSourceStack source, Read<T> operation, Consumer<T> result) {
        async(source, operation, result, null);
    }

    /**
     * @param abandon chamado na thread do servidor quando o resultado nao vai ser usado: a operacao de
     *                IO falhou (com {@code null}) ou quem pediu nao pode mais agir (com o valor lido).
     *                E onde quem reservou algo antes do IO solta a reserva.
     */
    private static <T> void async(CommandSourceStack source, Read<T> operation, Consumer<T> result,
                                  @Nullable Consumer<T> abandon) {
        DeathHistoryStore repository = DeathHistoryEvents.store(source.getServer());
        try {
            repository.submit(() -> {
                try {
                    T value = operation.get();
                    source.getServer().execute(() -> {
                        if (!authorized(source)) {
                            if (abandon != null) abandon.accept(value);
                            return;
                        }
                        try { result.accept(value); }
                        catch (RuntimeException e) { failure(source, e); }
                    });
                } catch (Exception e) {
                    source.getServer().execute(() -> {
                        if (abandon != null) abandon.accept(null);
                        if (authorized(source)) failure(source, e);
                    });
                }
            });
        } catch (RuntimeException e) {
            if (abandon != null) abandon.accept(null);
            failure(source, e);
        }
    }

    private static void read(CommandSourceStack source, UUID id, Consumer<CompoundTag> result) {
        DeathHistoryStore repository = DeathHistoryEvents.store(source.getServer());
        async(source, () -> repository.read(id), result);
    }

    private static Component suggest(String text, String command, String tooltip) {
        return Component.literal(text).withStyle(s -> s.withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal(tooltip + "\nClique para preencher o comando; Enter confirma."))));
    }

    private static void failure(CommandSourceStack source, Exception error) {
        source.sendFailure(Component.literal("Death history: " + error.getMessage()));
        AurorionEssentials.LOGGER.warn("Death history operation failed", error);
    }
    private static void say(CommandSourceStack source, String text) { source.sendSuccess(() -> Component.literal(text), false); }
}
