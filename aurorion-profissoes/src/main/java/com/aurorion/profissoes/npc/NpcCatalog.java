package com.aurorion.profissoes.npc;

import com.aurorion.core.config.AurorionConfigs;
import com.aurorion.profissoes.AurorionProfissoes;
import com.aurorion.profissoes.npc.NpcDefinition.*;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.item.ItemParser;
import com.aurorion.profissoes.config.ProfessionsConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Os NPCs carregados, prontos para o servidor usar.
 *
 * <h2>O catalogo mora no mod</h2>
 * Os NPCs de oficio (nome, skin, servicos, loja e precos da Economia do Ato 2) vem de
 * {@code aurorion_profissoes/npcs.default.json}, <b>dentro do jar</b>: atualizar o mod atualiza os
 * NPCs. Antes o exemplo era copiado para a config na primeira subida e nunca mais mudava — servidor
 * antigo continuava com precos em esmeralda depois de qualquer atualizacao.
 *
 * <h2>Ajustes do servidor</h2>
 * {@code config/aurorion/npcs_extras.json} e opcional e comeca vazio. Mesmo formato; um NPC com id
 * novo e acrescentado, um com o id de um NPC do mod <b>substitui aquele NPC inteiro</b>. Aplica com
 * {@code /npc recarregar}, sem rebuild. O {@code npcs.json} das versoes antigas e aposentado na
 * primeira leitura ({@code npcs.json.antigo-<data>}), para nao voltar a sobrepor o catalogo.
 *
 * <p>O estado e um {@link Snapshot} imutavel trocado de uma vez. Um arquivo de ajustes que nem e
 * JSON <b>nao</b> apaga os NPCs que ja estavam funcionando: o snapshot antigo continua valendo e a
 * staff recebe o erro.
 */
public final class NpcCatalog {
    public static final String FILE = "npcs_extras.json";
    private static final String LEGACY_FILE = "npcs.json";
    private static final String BUILT_IN = "/aurorion_profissoes/npcs.default.json";

    public record Price(@Nullable Item item, int amount, long money) {
        public static final Price FREE = new Price(null, 0, 0);
        public boolean free() { return (item == null || amount <= 0) && money <= 0; }
    }
    /** {@code enchantment} so existe em {@code ENCHANT}, ja resolvido no registro do servidor. */
    public record LoadedService(Service service, Price cost, @Nullable Holder<Enchantment> enchantment) {}
    /** {@code result} tem quantidade 1; a quantidade vendida e {@code trade.amount()}. */
    public record LoadedTrade(Trade trade, ItemStack result, Price price) {}
    public record LoadedNpc(NpcDefinition definition, List<LoadedService> services, List<LoadedTrade> trades) {}
    public record Snapshot(int version, Map<String, LoadedNpc> npcs, List<String> errors) {
        static final Snapshot EMPTY = new Snapshot(0, Map.of(), List.of());
    }

    private static volatile Snapshot current = Snapshot.EMPTY;

    private NpcCatalog() {}

    /** O arquivo de ajustes do servidor. */
    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(AurorionConfigs.FOLDER).resolve(FILE);
    }

    public static @Nullable LoadedNpc get(String id) { return id == null ? null : current.npcs.get(id); }
    public static int version() { return current.version; }
    public static Collection<String> ids() { return current.npcs.keySet(); }
    public static List<String> errors() { return current.errors; }

    /** Le o catalogo do mod e os ajustes do servidor de novo. Devolve os avisos (vazio quando tudo carregou). */
    public static List<String> reload(MinecraftServer server) {
        var errors = new ArrayList<String>();
        var builtIn = NpcConfigParser.parse(builtIn());
        builtIn.errors().forEach(message -> errors.add("catálogo do mod: " + message));

        Path path = file();
        String json;
        try {
            String retired = retireLegacy(path.resolveSibling(LEGACY_FILE));
            if (!retired.isEmpty()) errors.add("O npcs.json antigo foi aposentado (ficou em " + retired
                    + "). Os NPCs agora vêm do mod; ajustes vão em " + FILE + ".");
            if (Files.notExists(path)) {
                Files.createDirectories(path.getParent());
                Files.writeString(path, "{\n  \"npcs\": []\n}\n", StandardCharsets.UTF_8);
            }
            json = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException error) {
            AurorionProfissoes.LOGGER.error("NPCs: nao consegui ler {}; mantendo os NPCs atuais.", path, error);
            return List.of("Não consegui ler " + path + ": " + error.getMessage());
        }
        var extras = NpcConfigParser.parse(json);
        if (!extras.readable()) {
            extras.errors().forEach(message -> errors.add(FILE + ": " + message));
            errors.forEach(message -> AurorionProfissoes.LOGGER.error("NPCs: {}", message));
            errors.add("Os NPCs carregados anteriormente continuam valendo.");
            return List.copyOf(errors);
        }
        extras.errors().forEach(message -> errors.add(FILE + ": " + message));

        // Mesmo id: o NPC do servidor substitui o do mod inteiro (nao ha mistura campo a campo).
        var definitions = new LinkedHashMap<String, NpcDefinition>();
        builtIn.npcs().forEach(npc -> definitions.put(npc.id(), npc));
        extras.npcs().forEach(npc -> definitions.put(npc.id(), npc));

        var npcs = new LinkedHashMap<String, LoadedNpc>();
        for (var npc : definitions.values()) {
            String path0 = "npcs[" + npc.id() + "]";
            var services = new ArrayList<LoadedService>();
            for (int i = 0; i < npc.services().size(); i++) {
                var service = npc.services().get(i);
                try { services.add(new LoadedService(service, price(service.cost()), enchantment(server, service))); }
                catch (IllegalArgumentException error) {
                    errors.add(path0 + ".services[" + i + "]: " + error.getMessage() + " Serviço ignorado.");
                }
            }
            var trades = new ArrayList<LoadedTrade>();
            for (int i = 0; i < npc.trades().size(); i++) {
                var trade = npc.trades().get(i);
                try { trades.add(new LoadedTrade(trade, stack(server, trade), price(trade.price()))); }
                catch (IllegalArgumentException error) {
                    errors.add(path0 + ".trades[" + i + "]: " + error.getMessage() + " Oferta ignorada.");
                }
            }
            npcs.put(npc.id(), new LoadedNpc(npc, List.copyOf(services), List.copyOf(trades)));
        }
        current = new Snapshot(current.version + 1, Collections.unmodifiableMap(npcs), List.copyOf(errors));
        errors.forEach(message -> AurorionProfissoes.LOGGER.warn("NPCs: {}", message));
        AurorionProfissoes.LOGGER.info("NPCs: {} carregado(s) ({} do mod, {} de {}; {} aviso(s)).", npcs.size(),
                builtIn.npcs().size(), extras.npcs().size(), path, errors.size());
        return List.copyOf(errors);
    }

    /**
     * Grava uma copia do catalogo do mod em {@code config/aurorion/npcs_exemplo.json}, so para a staff
     * consultar precos e copiar trechos para o {@code npcs_extras.json}. Essa copia nunca e carregada:
     * se fosse, congelaria os NPCs de novo na versao do dia em que foi exportada.
     */
    public static Path exportBuiltIn() throws IOException {
        Path target = file().resolveSibling("npcs_exemplo.json");
        Files.createDirectories(target.getParent());
        Files.writeString(target, builtIn(), StandardCharsets.UTF_8);
        return target;
    }

    private static String builtIn() {
        try (InputStream in = NpcCatalog.class.getResourceAsStream(BUILT_IN)) {
            if (in != null) return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            AurorionProfissoes.LOGGER.error("NPCs: catalogo embutido ilegivel.", error);
        }
        return "{\"npcs\":[]}";
    }

    /** Move o {@code npcs.json} das versoes antigas para o lado. Devolve o nome novo, ou vazio se nao havia. */
    private static String retireLegacy(Path legacy) throws IOException {
        if (Files.notExists(legacy)) return "";
        String backup = LEGACY_FILE + ".antigo-" + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Files.move(legacy, legacy.resolveSibling(backup));
        AurorionProfissoes.LOGGER.warn("NPCs: {} aposentado como {}. O catalogo vem do mod; ajustes em {}.", legacy, backup, FILE);
        return backup;
    }

    public static void clear() { current = Snapshot.EMPTY; }

    private static Price price(Cost cost) {
        if (cost.money() <= 0 && !cost.hasItem()) return Price.FREE;
        return new Price(cost.hasItem() ? item(cost.itemId()) : null, cost.amount(), cost.money());
    }

    private static @Nullable Holder<Enchantment> enchantment(MinecraftServer server, Service service) {
        if (service.action() != ActionType.ENCHANT) return null;
        var key = ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.parse(service.enchantment()));
        var holder = server.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(key);
        if (holder.isEmpty()) throw new IllegalArgumentException("encantamento \"" + service.enchantment() + "\" não existe neste servidor.");
        int limit = ProfessionsConfig.SPEC.isLoaded() ? ProfessionsConfig.SPECIALIST_ENCHANT_MAX.get() : 10;
        if (service.level() > limit)
            throw new IllegalArgumentException("nível " + service.level() + " passa do limite especializado (" + limit + ").");
        return holder.get();
    }

    private static Item item(String id) {
        var location = ResourceLocation.tryParse(id);
        var item = location == null ? Optional.<Item>empty() : BuiltInRegistries.ITEM.getOptional(location);
        if (item.isEmpty() || item.get() == Items.AIR) throw new IllegalArgumentException("item \"" + id + "\" não existe neste servidor.");
        return item.get();
    }

    /** Mesmo parser do {@code /give}: {@code minecraft:iron_sword[enchantments={...}]}. */
    private static ItemStack stack(MinecraftServer server, Trade trade) {
        item(trade.itemId());
        ItemStack stack;
        try {
            var result = new ItemParser(server.registryAccess()).parse(new StringReader(trade.itemId() + trade.components()));
            stack = new ItemStack(result.item(), 1, result.components());
        } catch (CommandSyntaxException error) {
            throw new IllegalArgumentException("components inválidos: " + error.getMessage());
        }
        if (!trade.nbtData().isEmpty()) {
            try {
                var tag = TagParser.parseTag(trade.nbtData());
                CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> data.merge(tag));
            } catch (CommandSyntaxException error) {
                throw new IllegalArgumentException("nbt_data inválido: " + error.getMessage());
            }
        }
        return stack;
    }
}
