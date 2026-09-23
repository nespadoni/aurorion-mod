package com.aurorion.essentials.death;

import com.aurorion.core.death.DeathClaims;
import com.aurorion.core.level.SafeSpot;
import com.aurorion.essentials.AurorionEssentials;
import com.aurorion.essentials.mixin.PlayerListSaveInvoker;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.time.Instant;
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
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(c -> list(c, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(c -> list(c, page(c)))))
                .then(Commands.literal("view").then(Commands.argument("id", UuidArgument.uuid())
                        .executes(c -> view(c, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(c -> view(c, page(c))))))
                .then(Commands.literal("tp").then(Commands.argument("id", UuidArgument.uuid()).executes(DeathHistoryCommand::teleport)))
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

    /**
     * Segunda confirmacao, exigida so quando o espolio da morte ja voltou por outro caminho (o
     * Relicario do Limbo). A palavra diz o que acontece, para ninguem digitar sem ler.
     */
    private static final String DUPLICATE = "duplicar";

    private static int page(CommandContext<CommandSourceStack> c) { return IntegerArgumentType.getInteger(c, "page"); }
    private static int item(CommandContext<CommandSourceStack> c) { return IntegerArgumentType.getInteger(c, "item"); }

    private static int help(CommandSourceStack source) {
        say(source, "/deathhistory <jogador|UUID> [pagina] — mortes salvas, inclusive offline\n"
                + "/deathhistory view <id> [pagina] — consulta somente leitura\n"
                + "/deathhistory tp <id> — local da morte\n"
                + "/deathhistory give <id> <indice> <destinatario> confirm — recupera uma pilha\n"
                + "/deathhistory restore <id> <destinatario> confirm — substitui inventario, ender chest, Curios, efeitos, XP e fome; cria backup primeiro.\n"
                + "Recuperacoes nao recolhem drops do mundo. Cada item pode ser recuperado uma unica vez por registro.\n"
                + "Itens mantidos na morte (Fio da Volta, Relicario) nunca sao restaurados: o jogador ja ficou com eles.\n"
                + "Se o Relicario ja chamou o espolio de volta, restore/give exigem 'confirm " + DUPLICATE + "'.");
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c, int page) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = c.getSource();
        String query = StringArgumentType.getString(c, "player");
        UUID account;
        try { account = UUID.fromString(query); }
        catch (IllegalArgumentException ignored) {
            ServerPlayer online = source.getServer().getPlayerList().getPlayerByName(query);
            if (online != null) account = online.getUUID();
            else {
                var cache = source.getServer().getProfileCache();
                var profile = cache == null ? java.util.Optional.<com.mojang.authlib.GameProfile>empty() : cache.get(query);
                if (profile.isEmpty()) { say(source, "Jogador desconhecido; use o UUID da conta."); return 0; }
                account = profile.get().getId();
            }
        }
        UUID owner = account;
        DeathHistoryStore repository = DeathHistoryEvents.store(source.getServer());
        async(source, () -> repository.list(owner), entries -> {
            if (entries.isEmpty()) { say(source, "Nenhuma morte salva para este jogador."); return; }
            DeathClaims claims = DeathClaims.get(source.getServer());
            int pages = (entries.size() + 7) / 8;
            if (page > pages) { say(source, "Pagina inexistente. Total: " + pages); return; }
            say(source, "Historico de mortes — pagina " + page + "/" + pages);
            for (int i = (page - 1) * 8; i < Math.min(entries.size(), page * 8); i++) {
                CompoundTag row = entries.getCompound(i);
                String id = row.getUUID("Id").toString();
                // Com varios personagens na mesma conta, o nome do personagem e o que separa as linhas.
                String character = row.contains("CharacterName") ? "[" + row.getString("CharacterName") + "] " : "";
                var message = Component.literal(character + Instant.ofEpochMilli(row.getLong("Time")) + " | " + row.getString("Cause"))
                        .withStyle(ChatFormatting.GRAY)
                        .append(DeathHistoryEvents.button(" [Ver]", "/deathhistory view " + id, id))
                        .append(DeathHistoryEvents.button(" [TP]", "/deathhistory tp " + id, row.getString("Dimension")));
                if (row.contains("CaptureError")) message.append(Component.literal(" [snapshot parcial]").withStyle(ChatFormatting.RED));
                else if (!row.getBoolean("CuriosComplete")) message.append(Component.literal(" [Curios incompleto]").withStyle(ChatFormatting.RED));
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

    private static int view(CommandContext<CommandSourceStack> c, int page) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer admin = c.getSource().getPlayerOrException();
        UUID id = UuidArgument.getUuid(c, "id");
        read(c.getSource(), id, snapshot -> {
            if (snapshot.contains("CaptureError")) {
                say(c.getSource(), "Snapshot parcial: " + snapshot.getString("CaptureError"));
                return;
            }
            ListTag entries = snapshot.getList("Items", Tag.TAG_COMPOUND);
            int pages = Math.max(1, (entries.size() + 53) / 54);
            if (page > pages) throw new IllegalStateException("Pagina inexistente. Total: " + pages);
            SimpleContainer display = new SimpleContainer(54);
            for (int slot = 0; slot < 54; slot++) {
                int index = (page - 1) * 54 + slot;
                if (index >= entries.size()) break;
                CompoundTag entry = entries.getCompound(index);
                ItemStack stack = DeathSnapshot.item(entry, admin);
                display.setItem(slot, stack.copy());
                if (!stack.isEmpty()) {
                    var line = Component.literal("#" + index + " " + entry.getString("Group") + "/" + entry.getInt("Slot") + " — ")
                            .append(stack.getHoverName()).append(Component.literal(" x" + stack.getCount()));
                    if (DeathSnapshot.keptOnDeath(entry, stack)) line.append(Component.literal(" (mantido na morte)")
                            .withStyle(ChatFormatting.DARK_GRAY));
                    c.getSource().sendSuccess(() -> line, false);
                }
            }
            admin.openMenu(new SimpleMenuProvider((window, inventory, player) ->
                    new ChestMenu(MenuType.GENERIC_9x6, window, inventory, display, 6) {
                        @Override public boolean stillValid(Player player) { return player.hasPermissions(2); }
                        @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
                        @Override public void clicked(int slot, int button, ClickType type, Player player) {
                            if (!player.hasPermissions(2)) player.closeContainer();
                            else broadcastFullState();
                        }
                    }, Component.literal("Morte: " + snapshot.getString("Name") + " " + page + "/" + pages)));
            say(c.getSource(), "Registro " + id + " | " + snapshot.getString("Dimension") + " | XP " + snapshot.getInt("XpLevel")
                    + " | Efeitos " + snapshot.getList("Effects", Tag.TAG_COMPOUND).size());
            DeathClaims.Claim claim = DeathClaims.get(c.getSource().getServer()).find(id);
            if (claim != null) c.getSource().sendSuccess(() -> Component.literal(claimWarning(claim))
                    .withStyle(ChatFormatting.GOLD), false);
            for (Tag raw : snapshot.getList("Effects", Tag.TAG_COMPOUND)) say(c.getSource(), "Efeito: " + raw);
            if (page < pages) c.getSource().sendSuccess(() -> DeathHistoryEvents.button("[Proxima pagina]",
                    "/deathhistory view " + id + " " + (page + 1), "Continuar inventario"), false);
        });
        return 1;
    }

    private static int teleport(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer admin = c.getSource().getPlayerOrException();
        read(c.getSource(), UuidArgument.getUuid(c, "id"), snapshot -> {
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

    private static int restore(CommandContext<CommandSourceStack> c, int item, boolean duplicate) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
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
                catch (ReflectiveOperationException e) { throw new IllegalStateException("Curios incompativel; nenhuma alteracao.", e); }
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
                    if (!sameInventory(backup, now)) throw new IllegalStateException("Inventario/XP mudou durante o backup. Restauracao abortada; consulte a reserva.");
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
                    try { repository.submit(() -> {
                        try { repository.finish(id, item, outcome); }
                        catch (Exception e) { AurorionEssentials.LOGGER.error("Restore journal completion failed", e); }
                    }); } catch (RuntimeException e) { AurorionEssentials.LOGGER.error("Restore journal queue full", e); }
                }
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
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Curios indisponivel durante a restauracao.", e); }
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
        DeathHistoryStore repository = DeathHistoryEvents.store(source.getServer());
        try {
            repository.submit(() -> {
                try {
                    T value = operation.get();
                    source.getServer().execute(() -> {
                        if (!authorized(source)) return;
                        try { result.accept(value); }
                        catch (RuntimeException e) { failure(source, e); }
                    });
                } catch (Exception e) { source.getServer().execute(() -> { if (authorized(source)) failure(source, e); }); }
            });
        } catch (RuntimeException e) { failure(source, e); }
    }

    private static void read(CommandSourceStack source, UUID id, Consumer<CompoundTag> result) {
        DeathHistoryStore repository = DeathHistoryEvents.store(source.getServer());
        async(source, () -> repository.read(id), result);
    }

    private static void failure(CommandSourceStack source, Exception error) {
        source.sendFailure(Component.literal("Death history: " + error.getMessage()));
        AurorionEssentials.LOGGER.warn("Death history operation failed", error);
    }
    private static void say(CommandSourceStack source, String text) { source.sendSuccess(() -> Component.literal(text), false); }
}
