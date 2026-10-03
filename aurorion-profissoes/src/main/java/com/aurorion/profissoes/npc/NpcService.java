package com.aurorion.profissoes.npc;

import com.aurorion.core.text.TimeFormat;
import com.aurorion.economia.money.Money;
import com.aurorion.economia.server.Wallet;
import com.aurorion.profissoes.AurorionProfissoes;
import com.aurorion.profissoes.api.ProfessionApi;
import com.aurorion.profissoes.compat.AdmDialogues;
import com.aurorion.profissoes.compat.FoodCompat;
import com.aurorion.profissoes.compat.LsoCompat;
import com.aurorion.profissoes.config.ProfessionsConfig;
import com.aurorion.profissoes.data.Profession;
import com.aurorion.profissoes.network.NpcActionPayload;
import com.aurorion.profissoes.network.NpcScreenPayload;
import com.aurorion.profissoes.npc.NpcCatalog.*;
import com.aurorion.profissoes.npc.NpcDefinition.ActionType;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import java.time.LocalDate;
import java.util.regex.Pattern;
import java.util.*;
import static com.aurorion.profissoes.npc.NpcDefinition.TIER_ANY;
import static com.aurorion.profissoes.npc.NpcDefinition.tierLabel;

/**
 * Atendimento dos NPCs de oficio.
 *
 * <h2>Para que eles existem</h2>
 *
 * <p>O atendimento de verdade e entre personagens ({@code ServiceManager}). O NPC cobre o buraco de
 * quando <b>nao ha ninguem do oficio por perto</b>: se um jogador com a mesma profissao estiver
 * dentro de {@code fallback_radius}, os servicos do NPC ficam bloqueados e a tela diz quem procurar.
 * O NPC nunca concorre com o jogador; ele so assume o plantao vazio.
 *
 * <h2>Ordem de uma cobranca</h2>
 *
 * <p>Tudo e conferido de novo aqui — a tela so mostra. Depois: cobra, executa, e se a execucao
 * falhar (comando desconhecido, alvo invalido) devolve o pagamento. Cobrar antes evita o servico de
 * graca quando a acao tem efeito colateral e a remocao falharia depois; no mesmo tick a conferencia
 * e a cobranca nao podem divergir.
 */
public final class NpcService {
    private static final long SESSION_MILLIS = 5 * 60_000L;
    private static final double REACH_SQR = 8.0D * 8.0D;
    private record Session(UUID token, UUID entity, String npcId, int version, long deadline) {}
    private record Outcome(String message, boolean error) {
        static Outcome ok(String message) { return new Outcome(message, false); }
        static Outcome fail(String message) { return new Outcome(message, true); }
    }
    private static final class Refusal extends RuntimeException {
        Refusal(String message) { super(message, null, false, false); }
    }

    /** Nome de conta que pode ir cru num comando: o formato da Mojang. */
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    /** Acima disto, os cooldowns vencidos sao varridos antes de gravar mais um. */
    private static final int COOLDOWN_SWEEP = 4096;

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<String, Long> COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Long> LAST_ACTION = new HashMap<>();
    private static long boot = System.currentTimeMillis();

    private NpcService() {}

    public static void open(ServerPlayer player, ProfessionNpcEntity npc) {
        long now = System.currentTimeMillis();
        if (now - LAST_ACTION.getOrDefault(player.getUUID(), 0L) < 400) return;
        LAST_ACTION.put(player.getUUID(), now);
        show(player, npc, NpcScreenPayload.TAB_HOME, null);
    }

    public static void action(ServerPlayer player, NpcActionPayload payload) {
        long now = System.currentTimeMillis();
        var session = SESSIONS.get(player.getUUID());
        if (session == null || !session.token.equals(payload.token())) {
            if (!NpcActionPayload.CLOSE.equals(payload.kind()))
                player.displayClientMessage(Component.literal("Atendimento expirado. Fale com o NPC de novo."), true);
            return;
        }
        if (NpcActionPayload.CLOSE.equals(payload.kind())) { SESSIONS.remove(player.getUUID()); return; }
        if (now > session.deadline) {
            SESSIONS.remove(player.getUUID());
            player.displayClientMessage(Component.literal("Atendimento expirado. Fale com o NPC de novo."), true);
            return;
        }
        if (now - LAST_ACTION.getOrDefault(player.getUUID(), 0L) < 250) return;
        LAST_ACTION.put(player.getUUID(), now);

        var entity = player.serverLevel().getEntity(session.entity);
        if (!(entity instanceof ProfessionNpcEntity npc) || !npc.isAlive() || player.distanceToSqr(npc) > REACH_SQR) {
            SESSIONS.remove(player.getUUID());
            player.displayClientMessage(Component.literal("Aproxime-se do NPC para ser atendido."), true);
            return;
        }
        int tab = NpcActionPayload.TRADE.equals(payload.kind()) ? NpcScreenPayload.TAB_SHOP : NpcScreenPayload.TAB_SERVICES;
        var loaded = NpcCatalog.get(npc.npcId());
        if (loaded == null || session.version != NpcCatalog.version() || !session.npcId.equals(npc.npcId())) {
            show(player, npc, tab, Outcome.fail("As opções deste NPC mudaram. Confira de novo antes de escolher."));
            return;
        }
        switch (payload.kind()) {
            case NpcActionPayload.SERVICE -> show(player, npc, tab, service(player, npc, loaded, payload.index(), now, payload.text()));
            case NpcActionPayload.TRADE -> show(player, npc, tab, trade(player, npc, loaded, payload.index()));
            case NpcActionPayload.TALK -> {
                if (AdmDialogues.open(player, npc, loaded.definition().admDialogue())) SESSIONS.remove(player.getUUID());
                else show(player, npc, NpcScreenPayload.TAB_TALK, null);
            }
            default -> { }
        }
    }

    // --- tela ----------------------------------------------------------------------------------

    private static void show(ServerPlayer player, ProfessionNpcEntity npc, int tab, @Nullable Outcome outcome) {
        if (!player.connection.hasChannel(NpcScreenPayload.TYPE.id())) return;
        var loaded = NpcCatalog.get(npc.npcId());
        if (loaded == null) {
            player.displayClientMessage(Component.literal("Este NPC não existe no catálogo (/npc listar mostra os ids)."), true);
            return;
        }
        var definition = loaded.definition();
        long now = System.currentTimeMillis();
        String blocked = professionalNearby(player, npc, definition);

        var services = new ArrayList<NpcScreenPayload.ServiceRow>();
        for (int i = 0; i < loaded.services().size() && i < NpcScreenPayload.MAX_ROWS; i++) {
            var entry = loaded.services().get(i);
            String reason = blocked;
            if (reason == null) {
                try { checkService(player, definition, entry, i, now); }
                catch (Refusal refusal) { reason = refusal.getMessage(); }
            }
            String input = entry.service().action() == ActionType.NAME_TAG ? "Nome gravado na etiqueta" : "";
            services.add(new NpcScreenPayload.ServiceRow(clip(entry.service().name(), 128), clip(entry.service().description(), 512),
                    clip(describe(entry.cost()), 256), reason == null, clip(reason == null ? "" : reason, 512), input));
        }

        var trades = new ArrayList<NpcScreenPayload.TradeRow>();
        var stock = NpcStockData.get(player.server);
        for (int i = 0; i < loaded.trades().size() && i < NpcScreenPayload.MAX_ROWS; i++) {
            var entry = loaded.trades().get(i);
            int left = remaining(stock, definition, entry);
            String reason = definition.fallbackBlocksTrades() ? blocked : null;
            if (reason == null && left == 0) reason = "Esgotado. " + restockHint(entry);
            if (reason == null && !canAfford(player, entry.price())) reason = "Você precisa de " + describe(entry.price()) + ".";
            ItemStack icon = entry.price().item() == null ? ItemStack.EMPTY : new ItemStack(entry.price().item(), Math.min(99, Math.max(1, entry.price().amount())));
            trades.add(new NpcScreenPayload.TradeRow(entry.result().copy(), entry.trade().amount(), icon,
                    clip(describe(entry.price()), 256), left, reason == null, clip(reason == null ? "" : reason, 512)));
        }

        String eyebrow = !definition.title().isEmpty() ? definition.title()
                : definition.profession() == Profession.NONE ? "Mercador" : definition.profession().label();
        String greeting = definition.greeting();
        if (blocked != null && !loaded.services().isEmpty()) greeting = blocked;
        var dialogue = definition.dialogue().stream().limit(NpcScreenPayload.MAX_LINES).map(line -> clip(line, 512)).toList();
        boolean adm = !definition.admDialogue().isBlank() && AdmDialogues.available();

        UUID token = UUID.randomUUID();
        SESSIONS.put(player.getUUID(), new Session(token, npc.getUUID(), definition.id(), NpcCatalog.version(), now + SESSION_MILLIS));
        String balance = "Saldo: " + Money.describe(Wallet.balance(player.server, player.getUUID()));
        PacketDistributor.sendToPlayer(player, new NpcScreenPayload(token, clip(definition.displayName(), 128),
                clip(eyebrow.toUpperCase(Locale.ROOT), 128), definition.profession().id(), clip(greeting, 512), clip(balance, 128),
                outcome == null ? "" : clip(outcome.message, 512), outcome != null && outcome.error, tab, adm,
                services, trades, dialogue));
    }

    // --- servicos ------------------------------------------------------------------------------

    private static Outcome service(ServerPlayer player, ProfessionNpcEntity npc, LoadedNpc loaded, int index, long now, String text) {
        if (index < 0 || index >= loaded.services().size()) return Outcome.fail("Serviço inválido.");
        var definition = loaded.definition();
        var entry = loaded.services().get(index);
        var service = entry.service();
        String blocked = professionalNearby(player, npc, definition);
        if (blocked != null) return Outcome.fail(blocked);
        // O resultado e montado antes de cobrar: se o item nao aceita, ninguem paga.
        ItemStack prepared = ItemStack.EMPTY;
        String engraving = "";
        try {
            checkService(player, definition, entry, index, now);
            switch (service.action()) {
                case ENCHANT -> prepared = enchanted(player, entry);
                case POTION_STRENGTH, POTION_DURATION -> prepared = brewed(player, service.action());
                case NAME_TAG -> engraving = tagName(text);
                default -> { }
            }
        } catch (Refusal refusal) { return Outcome.fail(refusal.getMessage()); }

        charge(player, entry.cost());
        final String engraved = engraving;
        final ItemStack result = prepared;
        boolean done = switch (service.action()) {
            case HEAL -> { heal(player); yield true; }
            case REPAIR -> { player.getMainHandItem().setDamageValue(0); yield true; }
            case FINISH_FOOD -> {
                var food = player.getMainHandItem().copy();
                FoodCompat.finish(food, true, player.level());
                player.setItemInHand(InteractionHand.MAIN_HAND, food);
                yield true;
            }
            case ENCHANT, POTION_STRENGTH, POTION_DURATION -> { player.setItemInHand(InteractionHand.MAIN_HAND, result); yield true; }
            case NAME_TAG -> { give(player, nameTag(engraved), 1); yield true; }
            case COMMAND -> false;
        };
        boolean commands = runCommands(player, definition, service.commands());
        if (service.action() == ActionType.COMMAND) done = commands;
        if (!done) {
            refund(player, entry.cost());
            AurorionProfissoes.LOGGER.warn("NPC {}: servico '{}' falhou para {}; pagamento devolvido.",
                    definition.id(), service.name(), player.getGameProfile().getName());
            return Outcome.fail("O serviço não pôde ser realizado. O pagamento foi devolvido.");
        }
        if (service.cooldownSeconds() > 0) {
            // Sem isto o mapa so crescia (jogador x NPC x servico) ate o servidor reiniciar.
            if (COOLDOWNS.size() >= COOLDOWN_SWEEP) COOLDOWNS.values().removeIf(until -> until <= now);
            COOLDOWNS.put(cooldownKey(player, definition, index), now + service.cooldownSeconds() * 1000L);
        }
        player.inventoryMenu.broadcastChanges();
        AurorionProfissoes.LOGGER.info("NPC {}: servico '{}' para {} ({}){}.", definition.id(), service.name(),
                player.getGameProfile().getName(), describe(entry.cost()), engraved.isEmpty() ? "" : " nome \"" + engraved + "\"");
        if (service.action() == ActionType.NAME_TAG) return Outcome.ok("Etiqueta gravada: “" + engraved + "”.");
        return Outcome.ok("Serviço realizado: " + service.name() + ".");
    }

    /** Lanca {@link Refusal} com o motivo quando o servico nao pode ser feito agora. */
    private static void checkService(ServerPlayer player, NpcDefinition definition, LoadedService entry, int index, long now) {
        var service = entry.service();
        long until = COOLDOWNS.getOrDefault(cooldownKey(player, definition, index), 0L);
        if (until > now) throw new Refusal("Disponível de novo em " + TimeFormat.duration(until - now).getString() + ".");
        var hand = player.getMainHandItem();
        switch (service.action()) {
            case HEAL -> {
                int severity = severity(player);
                if (severity == 0) throw new Refusal("Você já está saudável.");
                if (service.tier() != TIER_ANY && service.tier() != severity)
                    throw new Refusal("Seu caso é " + tierLabel(ActionType.HEAL, severity) + ": escolha o atendimento dessa faixa.");
            }
            case REPAIR -> {
                if (hand.isEmpty() || hand.getCount() != 1 || !hand.isDamageableItem())
                    throw new Refusal("Segure o equipamento na mão principal.");
                if (!hand.isDamaged()) throw new Refusal("Este equipamento não está danificado.");
                if (!hand.isRepairable()) throw new Refusal("Este equipamento não aceita reparo.");
                int worn = damageTier(hand);
                if (service.tier() != TIER_ANY && service.tier() != worn)
                    throw new Refusal("Dano " + tierLabel(ActionType.REPAIR, worn) + ": escolha o reparo dessa faixa.");
            }
            case ENCHANT -> enchanted(player, entry);
            case POTION_STRENGTH, POTION_DURATION -> brewed(player, service.action());
            case NAME_TAG -> { }
            case FINISH_FOOD -> {
                if (!FoodCompat.qualityAvailable()) throw new Refusal("A integração Quality Food está indisponível.");
                if (!FoodCompat.isFood(hand) || hand.getCount() > 16) throw new Refusal("Segure um lote de até 16 alimentos na mão principal.");
                if (FoodCompat.grade(hand) == 1) throw new Refusal("Este lote já recebeu preparo profissional.");
                if (!FoodCompat.freshEnough(hand, player)) throw new Refusal("Este alimento perdeu o frescor.");
            }
            case COMMAND -> { }
        }
        if (!canAfford(player, entry.cost())) throw new Refusal("Você precisa de " + describe(entry.cost()) + ".");
    }

    /**
     * A faixa da tabela medica da Economia: 0 saudavel, 1 leve, 2 moderado, 3 grave (uma lesao grave
     * do LSO, ou um membro abaixo de 25%), 4 critico (duas lesoes graves, ou uma com a vida no fim).
     * Sem o LSO, so a vida conta.
     */
    static int severity(ServerPlayer player) {
        float health = player.getHealth() / Math.max(1F, player.getMaxHealth());
        float worst = health;
        int critical = 0;
        for (var part : LsoCompat.parts(player).values()) {
            if (part.aurorionCritical()) critical++;
            if (part.aurorionMaxHealth() > 0) worst = Math.min(worst, part.aurorionHealth() / part.aurorionMaxHealth());
        }
        if (critical >= 2 || (critical == 1 && health <= .25F)) return 4;
        if (critical == 1 || worst < .25F) return 3;
        if (worst >= 1F) return 0;
        return worst >= .5F ? 1 : 2;
    }

    /** Faixa de reparo pelo desgaste: ate 25% leve, 50% medio, 75% pesado, acima quase destruido. */
    static int damageTier(ItemStack stack) {
        double worn = stack.getDamageValue() / (double) Math.max(1, stack.getMaxDamage());
        return worn <= .25 ? 1 : worn <= .5 ? 2 : worn <= .75 ? 3 : 4;
    }

    /**
     * O item da mao com o encantamento do servico. Mesmas regras da inscricao do arcanista jogador:
     * so equipamento compativel ou livro comum, sem conflito com o que ja existe, nunca Mending (o
     * parser recusa).
     */
    private static ItemStack enchanted(ServerPlayer player, LoadedService entry) {
        var enchant = entry.enchantment();
        int level = entry.service().level();
        if (enchant == null) throw new Refusal("Encantamento indisponível neste servidor.");
        var hand = player.getMainHandItem();
        if (hand.isEmpty() || hand.getCount() != 1 || !EnchantmentHelper.canStoreEnchantments(hand) || hand.is(Items.ENCHANTED_BOOK))
            throw new Refusal("Segure um equipamento ou um livro comum na mão principal.");
        var original = EnchantmentHelper.getEnchantmentsForCrafting(hand);
        if (original.getLevel(enchant) >= level) throw new Refusal("Este item já tem esse encantamento nesse nível.");
        if (!hand.is(Items.BOOK) && !enchant.value().canEnchant(hand)) throw new Refusal("Este encantamento não serve para este item.");
        for (var existing : original.keySet())
            if (!existing.equals(enchant) && !Enchantment.areCompatible(existing, enchant))
                throw new Refusal("Incompatível com " + Enchantment.getFullname(existing, original.getLevel(existing)).getString() + ".");
        var merged = new ItemEnchantments.Mutable(original);
        merged.set(enchant, level);
        ItemStack result = hand.is(Items.BOOK) ? hand.transmuteCopy(Items.ENCHANTED_BOOK) : hand.copy();
        EnchantmentHelper.setEnchantments(result, merged.toImmutable());
        return result;
    }

    /** A pocao da mao concentrada (glowstone) ou prolongada (redstone), pelas receitas reais do jogo. */
    private static ItemStack brewed(ServerPlayer player, ActionType action) {
        var hand = player.getMainHandItem();
        var catalyst = new ItemStack(action == ActionType.POTION_STRENGTH ? Items.GLOWSTONE_DUST : Items.REDSTONE);
        var brewing = player.level().potionBrewing();
        if (hand.isEmpty() || hand.getCount() != 1 || !brewing.hasMix(hand, catalyst))
            throw new Refusal("Segure uma poção que aceite este aprimoramento, uma de cada vez.");
        var result = brewing.mix(catalyst, hand);
        if (ItemStack.isSameItemSameComponents(hand, result)) throw new Refusal("Esta poção já atingiu o limite.");
        return result;
    }

    /**
     * O nome digitado, com o mesmo filtro da bigorna: sem codigos de cor (§) nem caracteres de
     * controle, ate {@link AnvilMenu#MAX_NAME_LENGTH}. O cliente ja limita, mas o pacote pode vir forjado.
     */
    static String tagName(String text) {
        String name = StringUtil.filterText(text == null ? "" : text).strip();
        if (name.isEmpty()) throw new Refusal("Escreva o nome que vai na etiqueta.");
        if (name.length() > AnvilMenu.MAX_NAME_LENGTH)
            throw new Refusal("Nome longo demais: até " + AnvilMenu.MAX_NAME_LENGTH + " caracteres.");
        return name;
    }

    private static ItemStack nameTag(String name) {
        var tag = new ItemStack(Items.NAME_TAG);
        tag.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return tag;
    }

    /** Cura nativa: vida vanilla e, com o LSO, todas as partes do corpo (inclusive a marca de lesao grave). */
    private static void heal(ServerPlayer player) {
        LsoCompat.healAll(player);
        player.setHealth(player.getMaxHealth());
    }

    /**
     * Roda os comandos como o servidor (nivel 4), mas posicionado no jogador e com ele como
     * executor: {@code @s} e {@code @p} apontam para quem pagou. A saida vai para o log, nao para o chat.
     *
     * @return se ao menos um comando terminou com sucesso
     */
    private static boolean runCommands(ServerPlayer player, NpcDefinition definition, List<String> commands) {
        boolean any = false;
        for (String template : commands) {
            String command = expand(template, player, definition);
            boolean[] success = {false};
            CommandSourceStack source = player.server.createCommandSourceStack()
                    .withSource(CommandSource.NULL)
                    .withLevel(player.serverLevel())
                    .withPosition(player.position())
                    .withRotation(player.getRotationVector())
                    .withEntity(player)
                    .withPermission(Commands.LEVEL_OWNERS)
                    .withSuppressedOutput()
                    .withCallback((ok, result) -> { if (ok) success[0] = true; });
            player.server.getCommands().performPrefixedCommand(source, command);
            AurorionProfissoes.LOGGER.info("NPC {}: /{} -> {}", definition.id(), command, success[0] ? "ok" : "falhou");
            any |= success[0];
        }
        return any;
    }

    /**
     * {@code {player}}, {@code {uuid}}, {@code {npc}}, {@code {x}}, {@code {y}}, {@code {z}}.
     *
     * <p>Os comandos rodam com permissao 4, entao o que entra no texto nao pode virar sintaxe. Nome de
     * conta Mojang e so {@code [A-Za-z0-9_]}, mas em offline-mode o vanilla aceita qualquer caractere
     * visivel: um jogador chamado {@code @a} transformaria {@code /give {player} ...} em
     * {@code /give @a ...}. Nome fora do padrao cai para o UUID, que o jogo tambem aceita como alvo.
     */
    static String expand(String template, ServerPlayer player, NpcDefinition definition) {
        var pos = player.blockPosition();
        return template.replace("{player}", commandSafeName(player.getGameProfile().getName(), player.getUUID()))
                .replace("{uuid}", player.getUUID().toString())
                .replace("{npc}", definition.id())
                .replace("{x}", Integer.toString(pos.getX()))
                .replace("{y}", Integer.toString(pos.getY()))
                .replace("{z}", Integer.toString(pos.getZ()));
    }

    /** O nome, se for so letras, numeros e _ (ate 16); senao o UUID. Nunca vira seletor nem sintaxe. */
    static String commandSafeName(String name, UUID uuid) {
        return SAFE_NAME.matcher(name).matches() ? name : uuid.toString();
    }

    // --- loja ----------------------------------------------------------------------------------

    private static Outcome trade(ServerPlayer player, ProfessionNpcEntity npc, LoadedNpc loaded, int index) {
        if (index < 0 || index >= loaded.trades().size()) return Outcome.fail("Oferta inválida.");
        var definition = loaded.definition();
        var entry = loaded.trades().get(index);
        if (definition.fallbackBlocksTrades()) {
            String blocked = professionalNearby(player, npc, definition);
            if (blocked != null) return Outcome.fail(blocked);
        }
        var stock = NpcStockData.get(player.server);
        if (remaining(stock, definition, entry) == 0) return Outcome.fail("Esgotado. " + restockHint(entry));
        if (!canAfford(player, entry.price())) return Outcome.fail("Você precisa de " + describe(entry.price()) + ".");

        charge(player, entry.price());
        give(player, entry.result(), entry.trade().amount());
        if (entry.trade().limited())
            stock.add(NpcStockData.key(definition.id(), entry.trade().key()), period(entry), 1);
        player.inventoryMenu.broadcastChanges();
        String name = entry.result().getHoverName().getString();
        AurorionProfissoes.LOGGER.info("NPC {}: {} comprou {}x {} ({}).", definition.id(),
                player.getGameProfile().getName(), entry.trade().amount(), name, describe(entry.price()));
        return Outcome.ok("Você comprou " + entry.trade().amount() + "× " + name + ".");
    }

    private static int remaining(NpcStockData stock, NpcDefinition definition, LoadedTrade entry) {
        if (!entry.trade().limited()) return -1;
        int sold = stock.sold(NpcStockData.key(definition.id(), entry.trade().key()), period(entry));
        return Math.max(0, entry.trade().maxStock() - sold);
    }

    private static long period(LoadedTrade entry) {
        return NpcStockData.period(entry.trade().restock(), boot, LocalDate.now());
    }

    private static String restockHint(LoadedTrade entry) {
        return switch (entry.trade().restock()) {
            case DAILY -> "Volta amanhã.";
            case RESTART -> "Volta no próximo reinício do servidor.";
            case NEVER -> "Não será reposto.";
        };
    }

    // --- pagamento -----------------------------------------------------------------------------

    private static boolean canAfford(ServerPlayer player, Price price) {
        if (player.isCreative() && player.hasPermissions(2)) return true;
        if (price.item() != null && price.amount() > 0 && countItem(player, price) < price.amount()) return false;
        return price.money() <= 0 || Wallet.balance(player.server, player.getUUID()) >= price.money();
    }

    private static int countItem(ServerPlayer player, Price price) {
        return player.getInventory().clearOrCountMatchingItems(stack -> stack.is(price.item()), 0,
                player.inventoryMenu.getCraftSlots());
    }

    /** Chamado logo depois de {@link #canAfford}, no mesmo tick. Staff em criativo nao paga. */
    private static void charge(ServerPlayer player, Price price) {
        if (player.isCreative() && player.hasPermissions(2)) return;
        if (price.item() != null && price.amount() > 0)
            player.getInventory().clearOrCountMatchingItems(stack -> stack.is(price.item()), price.amount(),
                    player.inventoryMenu.getCraftSlots());
        if (price.money() > 0) Wallet.add(player.server, player.getUUID(), -price.money());
    }

    private static void refund(ServerPlayer player, Price price) {
        if (player.isCreative() && player.hasPermissions(2)) return;
        if (price.item() != null && price.amount() > 0) give(player, new ItemStack(price.item()), price.amount());
        if (price.money() > 0) Wallet.add(player.server, player.getUUID(), price.money());
    }

    /** Entrega em pilhas do tamanho maximo do item; o que nao cabe cai no chao, na frente do jogador. */
    private static void give(ServerPlayer player, ItemStack template, int amount) {
        int left = amount;
        while (left > 0) {
            var stack = template.copyWithCount(Math.min(left, template.getMaxStackSize()));
            left -= stack.getCount();
            player.getInventory().add(stack);
            if (!stack.isEmpty()) player.drop(stack, false);
        }
    }

    static String describe(Price price) {
        if (price.free()) return "Gratuito";
        var parts = new ArrayList<String>(2);
        if (price.item() != null && price.amount() > 0)
            parts.add(price.amount() + "× " + price.item().getDescription().getString());
        if (price.money() > 0) parts.add(Money.describe(price.money()));
        return String.join(" + ", parts);
    }

    // --- profissional por perto ----------------------------------------------------------------

    /** O motivo do bloqueio quando um jogador do mesmo oficio pode atender; {@code null} se o NPC esta de plantao. */
    private static @Nullable String professionalNearby(ServerPlayer customer, ProfessionNpcEntity npc, NpcDefinition definition) {
        if (definition.profession() == Profession.NONE || definition.fallbackRadius() == 0 || !ProfessionsConfig.enabled()) return null;
        double radius = definition.fallbackRadius();
        for (ServerPlayer other : customer.server.getPlayerList().getPlayers()) {
            if (other == customer || other instanceof FakePlayer || !other.isAlive() || other.isSpectator()) continue;
            if (ProfessionApi.of(other) != definition.profession()) continue;
            if (radius < 0 || (other.level() == npc.level() && other.distanceToSqr(npc) <= radius * radius))
                return definition.profession().label() + " disponível por perto: procure "
                        + other.getDisplayName().getString() + " para este atendimento.";
        }
        return null;
    }

    // --- ciclo de vida -------------------------------------------------------------------------

    private static String cooldownKey(ServerPlayer player, NpcDefinition definition, int index) {
        return player.getUUID() + "|" + definition.id() + "|" + index;
    }

    public static void forget(UUID account) {
        SESSIONS.remove(account);
        LAST_ACTION.remove(account);
    }

    public static void started() { boot = System.currentTimeMillis(); }

    public static void clear() {
        SESSIONS.clear(); COOLDOWNS.clear(); LAST_ACTION.clear();
    }

    private static String clip(String text, int max) {
        return text.length() > max ? text.substring(0, max) : text;
    }
}
