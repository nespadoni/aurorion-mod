package com.aurorion.profissoes.npc;

import com.aurorion.core.config.AurorionConfigs;
import com.aurorion.profissoes.AurorionProfissoes;
import com.aurorion.profissoes.npc.NpcDefinition.*;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.component.DataComponents;
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
 * <p>O arquivo mora em {@code config/aurorion/npcs.json}, ao lado das outras configs do Aurorion,
 * e nao em datapack: a staff edita no servidor e aplica com {@code /npc recarregar}, sem rebuild
 * nem {@code /reload} do mundo inteiro. Na primeira subida sem arquivo, o exemplo embutido no jar e
 * copiado para la.
 *
 * <p>O estado e um {@link Snapshot} imutavel trocado de uma vez. Um arquivo que nem e JSON
 * <b>nao</b> apaga os NPCs que ja estavam funcionando: o snapshot antigo continua valendo e a staff
 * recebe o erro.
 */
public final class NpcCatalog {
    public static final String FILE = "npcs.json";
    private static final String DEFAULT_RESOURCE = "/aurorion_profissoes/npcs.default.json";

    public record Price(@Nullable Item item, int amount, long money) {
        public static final Price FREE = new Price(null, 0, 0);
        public boolean free() { return (item == null || amount <= 0) && money <= 0; }
    }
    public record LoadedService(Service service, Price cost) {}
    /** {@code result} tem quantidade 1; a quantidade vendida e {@code trade.amount()}. */
    public record LoadedTrade(Trade trade, ItemStack result, Price price) {}
    public record LoadedNpc(NpcDefinition definition, List<LoadedService> services, List<LoadedTrade> trades) {}
    public record Snapshot(int version, Map<String, LoadedNpc> npcs, List<String> errors) {
        static final Snapshot EMPTY = new Snapshot(0, Map.of(), List.of());
    }

    private static volatile Snapshot current = Snapshot.EMPTY;

    private NpcCatalog() {}

    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(AurorionConfigs.FOLDER).resolve(FILE);
    }

    public static @Nullable LoadedNpc get(String id) { return id == null ? null : current.npcs.get(id); }
    public static int version() { return current.version; }
    public static Collection<String> ids() { return current.npcs.keySet(); }
    public static List<String> errors() { return current.errors; }

    /** Le o arquivo de novo. Devolve os erros encontrados (vazio quando tudo carregou). */
    public static List<String> reload(MinecraftServer server) {
        Path path = file();
        String json;
        try {
            if (Files.notExists(path)) writeDefault(path);
            json = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException error) {
            AurorionProfissoes.LOGGER.error("NPCs: nao consegui ler {}; mantendo os NPCs atuais.", path, error);
            return List.of("Não consegui ler " + path + ": " + error.getMessage());
        }
        var parsed = NpcConfigParser.parse(json);
        var errors = new ArrayList<>(parsed.errors());
        if (!parsed.readable()) {
            errors.forEach(message -> AurorionProfissoes.LOGGER.error("NPCs: {}", message));
            errors.add("Os NPCs carregados anteriormente continuam valendo.");
            return List.copyOf(errors);
        }

        var npcs = new LinkedHashMap<String, LoadedNpc>();
        for (int n = 0; n < parsed.npcs().size(); n++) {
            var npc = parsed.npcs().get(n);
            String path0 = "npcs[" + npc.id() + "]";
            var services = new ArrayList<LoadedService>();
            for (int i = 0; i < npc.services().size(); i++) {
                var service = npc.services().get(i);
                try { services.add(new LoadedService(service, price(service.cost()))); }
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
        AurorionProfissoes.LOGGER.info("NPCs: {} carregado(s) de {} ({} aviso(s)).", npcs.size(), path, errors.size());
        return List.copyOf(errors);
    }

    public static void clear() { current = Snapshot.EMPTY; }

    private static Price price(Cost cost) {
        if (cost.money() <= 0 && !cost.hasItem()) return Price.FREE;
        return new Price(cost.hasItem() ? item(cost.itemId()) : null, cost.amount(), cost.money());
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

    private static void writeDefault(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        try (InputStream in = NpcCatalog.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (in == null) Files.writeString(path, "{\n  \"npcs\": []\n}\n", StandardCharsets.UTF_8);
            else Files.copy(in, path);
        }
        AurorionProfissoes.LOGGER.info("NPCs: exemplo criado em {}.", path);
    }
}
