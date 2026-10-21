package com.aurorion.economia.server;

import com.aurorion.core.character.CharacterData;
import com.aurorion.economia.AurorionEconomia;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * O livro-caixa: toda mudanca de saldo vira uma linha em {@code <mundo>/aurorion_economia/livro.jsonl}.
 *
 * <p>E o que o ECONOMIA.md §4.7 pede — "emissao e gasto de dinheiro viram linha em disco" — e o que
 * faltava para responder, depois de um golpe ou de uma inflacao, <b>quem</b> passou dinheiro para
 * <b>quem</b>, <b>quando</b> e <b>por qual caminho</b> (comando, celular, cobranca, NPC, cofre). O
 * {@code SavedData} so guarda o saldo atual; ele nao conta historia.</p>
 *
 * <h2>Por que a escrita sai da thread do servidor</h2>
 *
 * <p>Diferente da auditoria do Limbo (alguns eventos por dia), dinheiro se move a todo momento num
 * servidor de 80 pessoas: loja de NPC, cobrancas, celular. Abrir e fechar arquivo no tick para cada
 * pagamento seria custo de disco pago pelo TPS de todo mundo. A linha e montada na thread do
 * servidor (onde nomes e personagens podem ser lidos com seguranca) e entregue a uma fila limitada;
 * uma thread propria grava em lote.</p>
 *
 * <p>A fila tem teto de proposito: se o disco travar, a memoria nao cresce sem limite. Linha que
 * nao couber vai inteira para o log do servidor em WARN — perde-se a conveniencia do arquivo, nao o
 * registro. No desligamento, {@link #drain()} grava o que sobrou na thread de quem chama.</p>
 *
 * <h2>Quem aparece na linha</h2>
 *
 * <p>Conta e sempre o UUID da conta mais o nome real da conta (perfil Mojang), e o nome do
 * personagem ao lado quando existe. Nada de nome inventado: a linha tem que bater com o
 * {@code usercache} e com o painel da staff.</p>
 */
public final class MoneyLedger {
    /** Contraparte de toda emissao e de todo ralo: o dinheiro nasce ou morre no sistema. */
    public static final String SYSTEM = "SISTEMA";

    private static final String DIR = AurorionEconomia.MOD_ID;
    private static final String FILE = "livro.jsonl";
    private static final int CAPACITY = 16_384;
    private static final int BATCH = 512;

    private record Line(Path file, String json) { }

    private static final BlockingQueue<Line> QUEUE = new ArrayBlockingQueue<>(CAPACITY);
    private static final Object WRITE_LOCK = new Object();
    private static Thread writer;
    /** Origem herdada por escritas que nao a recebem por parametro. So na thread do servidor. */
    private static String scope;

    private MoneyLedger() {
    }

    /** Uma ponta da linha: uma conta, o sistema ou o cofre de uma Casa. */
    public record Party(@Nullable UUID account, String label) {
        public static Party system() {
            return new Party(null, SYSTEM);
        }

        public static Party house(ResourceLocation house) {
            return new Party(null, "casa:" + house);
        }

        public static Party account(UUID account) {
            return new Party(account, "conta");
        }
    }

    public static Path path(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(DIR).resolve(FILE);
    }

    public static void transfer(@Nullable MinecraftServer server, UUID from, UUID to, long amount, String origin) {
        record(server, Party.account(from), Party.account(to), amount, origin);
    }

    /** Dinheiro que entrou na conta vindo do sistema (emissao). */
    public static void credit(@Nullable MinecraftServer server, UUID to, long amount, String origin) {
        record(server, Party.system(), Party.account(to), amount, origin);
    }

    /** Dinheiro que saiu da conta para o sistema (ralo). */
    public static void debit(@Nullable MinecraftServer server, UUID from, long amount, String origin) {
        record(server, Party.account(from), Party.system(), amount, origin);
    }

    /**
     * Grava uma movimentacao. Nunca lanca: livro-caixa com problema nao pode desfazer um pagamento
     * que ja aconteceu nem derrubar o tick.
     *
     * @param server null em teste de unidade (sem mundo): a chamada vira no-op
     */
    public static void record(@Nullable MinecraftServer server, Party from, Party to, long amount, String origin) {
        if (server == null || amount <= 0) return;
        String json;
        try {
            JsonObject line = new JsonObject();
            line.addProperty("ts", System.currentTimeMillis());
            line.addProperty("origem", origin == null || origin.isBlank() ? "nao_informada" : origin);
            line.addProperty("fragmentos", amount);
            line.add("de", describe(server, from));
            line.add("para", describe(server, to));
            json = line.toString();
        } catch (RuntimeException error) {
            AurorionEconomia.LOGGER.error("Livro-caixa: nao consegui montar a linha ({} -> {}, {} fragmentos, {}).",
                    from, to, amount, origin, error);
            return;
        }
        if (!QUEUE.offer(new Line(path(server), json))) {
            AurorionEconomia.LOGGER.warn("LIVRO_CAIXA (fila cheia, so no log) {}", json);
            return;
        }
        ensureWriter();
    }

    /**
     * Define a origem das escritas feitas dentro do bloco por codigo que nao recebe origem (o cofre
     * das Casas, por exemplo). Use em try-with-resources; aninha restaurando a anterior.
     */
    public static Scope origin(String origin) {
        String previous = scope;
        scope = origin;
        return new Scope(previous);
    }

    static String currentOrigin(String fallback) {
        return scope == null ? fallback : scope;
    }

    public static final class Scope implements AutoCloseable {
        private final String previous;

        private Scope(String previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            scope = previous;
        }
    }

    /** Grava, na thread de quem chama, o que ainda estiver na fila. Chamado ao parar o servidor. */
    public static void drain() {
        List<Line> rest = new ArrayList<>();
        QUEUE.drainTo(rest);
        if (!rest.isEmpty()) write(rest);
    }

    private static JsonObject describe(MinecraftServer server, Party party) {
        JsonObject out = new JsonObject();
        if (party.account() == null) {
            out.addProperty("tipo", party.label());
            return out;
        }
        UUID account = party.account();
        out.addProperty("tipo", "conta");
        out.addProperty("uuid", account.toString());
        out.addProperty("nome", accountName(server, account));
        CharacterData.Character character = CharacterData.get(server).find(account);
        if (character != null) {
            out.addProperty("personagem_id", character.id().toString());
            if (!character.fullName().isBlank()) out.addProperty("personagem", character.fullName());
        }
        return out;
    }

    /** O nome da conta Mojang: online pelo perfil, offline pelo usercache. Vazio se nunca visto. */
    private static String accountName(MinecraftServer server, UUID account) {
        ServerPlayer online = server.getPlayerList().getPlayer(account);
        if (online != null) return online.getGameProfile().getName();
        GameProfileCache cache = server.getProfileCache();
        if (cache == null) return "";
        Optional<GameProfile> profile = cache.get(account);
        return profile.map(GameProfile::getName).orElse("");
    }

    private static synchronized void ensureWriter() {
        if (writer != null && writer.isAlive()) return;
        writer = new Thread(MoneyLedger::drainLoop, "Aurorion Economia - livro-caixa");
        writer.setDaemon(true);
        writer.start();
    }

    private static void drainLoop() {
        List<Line> batch = new ArrayList<>(BATCH);
        while (true) {
            try {
                batch.add(QUEUE.take());
                QUEUE.drainTo(batch, BATCH - 1);
                write(batch);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException error) {
                AurorionEconomia.LOGGER.error("Livro-caixa: falha inesperada na gravacao.", error);
            } finally {
                batch.clear();
            }
        }
    }

    private static void write(List<Line> batch) {
        // Agrupa por arquivo: no servidor dedicado e um so; no integrado, mundos trocados nao se misturam.
        Map<Path, StringBuilder> byFile = new LinkedHashMap<>();
        for (Line line : batch) byFile.computeIfAbsent(line.file(), key -> new StringBuilder()).append(line.json()).append('\n');
        synchronized (WRITE_LOCK) {
            byFile.forEach((file, text) -> {
                try {
                    Files.createDirectories(file.getParent());
                    Files.writeString(file, text, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException error) {
                    AurorionEconomia.LOGGER.error("Livro-caixa: nao consegui gravar em {}; as linhas seguem abaixo.", file, error);
                    AurorionEconomia.LOGGER.warn("LIVRO_CAIXA (falha de disco) {}", text);
                }
            });
        }
    }
}
