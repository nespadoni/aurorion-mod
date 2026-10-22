package com.aurorion.areas.server;

import com.aurorion.areas.AurorionAreas;
import com.aurorion.areas.api.AreaApi;
import com.aurorion.areas.config.AreasConfig;
import com.aurorion.areas.data.AreaData;
import com.aurorion.areas.region.AreaRegion;
import com.aurorion.areas.rules.Decision;
import com.aurorion.core.character.AltData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Aviso de entrada para o Discord: a staff marca a area com {@code /area aviso <id> true} e cada
 * entrada vira uma linha numa fila que o bot puxa por RCON ({@code /area avisos puxar}). A mesma
 * entrada vai na hora para o chat de quem tem OP online ({@link StaffAlert#watched}), entao o aviso
 * no jogo nao depende do bot estar no ar.
 *
 * <h2>Por que fila, e nao envio</h2>
 *
 * <p>RCON so responde, nunca chama: o servidor nao tem como avisar o bot. O bot pergunta de poucos
 * em poucos segundos e a resposta esvazia a fila — o mesmo contrato do {@code /limbo relatorio}.
 * A fila vive so na memoria e tem teto: com o bot fora do ar por horas, as entradas mais antigas
 * caem primeiro, e um restart do servidor zera o que ninguem puxou.
 *
 * <h2>Independe da prioridade</h2>
 *
 * <p>A marca e lida na propria area, e nao na regra resolvida do lugar: uma sala vigiada dentro de
 * uma escola de prioridade maior continua avisando. Excecoes de personagem nao valem aqui — a
 * marca diz respeito ao lugar, nao a quem passa por ele.
 *
 * <h2>Custo</h2>
 *
 * <p>So as areas marcadas sao percorridas (normalmente uma ou duas), a cada {@link #CHECK_TICKS}
 * ticks por jogador, e a lista e refeita apenas quando a revisao dos dados muda. Sem area marcada
 * o tick sai na primeira linha.
 */
public final class AreaWatch {
    /** Chave guardada em {@code flags} da area; {@code /area perfil} a preserva. */
    public static final String RULE = "aviso_discord";
    private static final int CHECK_TICKS = 10;
    private static final int QUEUE_LIMIT = 200;
    /**
     * Teto da resposta do {@code puxar}. O RCON do vanilla parte respostas acima de 4096 bytes em
     * varios pacotes; ficando abaixo, o bot le sempre um pacote so.
     */
    static final int PULL_BUDGET = 3_500;
    private static final Map<UUID, Set<String>> INSIDE = new HashMap<>();
    /** {@code uuid|area} -> ultimo aviso em ms. Sobrevive ao logout, para relogar nao repetir o aviso. */
    private static final Map<String, Long> LAST = new HashMap<>();
    private static final ArrayDeque<Entry> QUEUE = new ArrayDeque<>();
    private static List<AreaRegion> watched = List.of();
    private static AreaData watchedData;
    private static long watchedRevision = -1;
    private static int dropped;

    private AreaWatch() {}

    public record Entry(long at, String player, UUID uuid, String label, String area, String name,
                        ResourceLocation dimension, int x, int y, int z, boolean test) {
        /** Uma linha {@code evento <query string>}: so ASCII, e nome com espaco nao quebra o parse. */
        String line() {
            return "evento t=" + at + "&area=" + enc(area) + "&nome=" + enc(name) + "&jogador=" + enc(player)
                    + "&uuid=" + uuid + "&rotulo=" + enc(label) + "&dim=" + enc(dimension.toString())
                    + "&x=" + x + "&y=" + y + "&z=" + z + (test ? "&teste=1" : "");
        }
        private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    }

    public static boolean watches(AreaRegion region) { return region.rules().flag(RULE) == Decision.ALLOW; }

    private static List<AreaRegion> watched(MinecraftServer server) {
        var data = AreaData.get(server);
        if (data != watchedData || data.revision() != watchedRevision) {
            watchedData = data; watchedRevision = data.revision();
            watched = data.all().stream().filter(r -> r.enabled() && watches(r)).toList();
        }
        return watched;
    }

    static void tick(ServerPlayer player) {
        if (player.tickCount % CHECK_TICKS != 0) return;
        var list = watched(player.server);
        UUID id = player.getUUID();
        if (list.isEmpty() || !AreasConfig.ENABLED.get()) { INSIDE.remove(id); return; }
        // Morto fica onde estava: o renascimento e quem decide se ele saiu.
        if (!player.isAlive()) return;
        var dimension = player.level().dimension().location();
        Set<String> before = INSIDE.get(id);
        Set<String> now = null;
        // Staff em criativo/espectador e acompanhada, mas nao avisa: trocar para sobrevivencia la
        // dentro nao conta como entrada.
        boolean silent = AreaApi.bypass(player);
        for (AreaRegion region : list) {
            if (!region.dimension().equals(dimension) || !region.volume().contains(player.getX(), player.getY(), player.getZ())) continue;
            if (now == null) now = new HashSet<>(4);
            now.add(region.id());
            if (before != null && before.contains(region.id())) continue;
            if (silent) bypassNotice(player, region); else entered(player, region);
        }
        if (now == null) INSIDE.remove(id); else INSIDE.put(id, now);
    }

    private static void entered(ServerPlayer player, AreaRegion region) {
        long now = System.currentTimeMillis();
        String key = player.getUUID() + "|" + region.id();
        Long last = LAST.get(key);
        long cooldown = 1000L * AreasConfig.ENTRY_ALERT_COOLDOWN.get();
        if (last != null && now - last < cooldown) return;
        LAST.put(key, now);
        if (LAST.size() > 4096) LAST.values().removeIf(at -> now - at >= cooldown);
        BlockPos pos = player.blockPosition();
        Entry entry = new Entry(now, player.getGameProfile().getName(), player.getUUID(),
                AltData.staffLabel(player.server, player.getGameProfile()), region.id(), region.name(),
                region.dimension(), pos.getX(), pos.getY(), pos.getZ(), false);
        offer(entry);
        StaffAlert.watched(player.server, entry);
    }

    /**
     * Sem isto, a staff testa o aviso em criativo, nada acontece e parece defeito. Barra de acao, e
     * nao chat: quem voa por cima de varias areas vigiadas nao enche o historico.
     */
    private static void bypassNotice(ServerPlayer player, AreaRegion region) {
        player.displayClientMessage(Component.literal("Área vigiada " + region.name()
                + ": criativo/espectador não dispara o aviso.").withStyle(ChatFormatting.GRAY), true);
    }

    static void offer(Entry entry) {
        if (QUEUE.size() >= QUEUE_LIMIT) { QUEUE.pollFirst(); dropped++; }
        QUEUE.addLast(entry);
        AurorionAreas.LOGGER.info("Aviso de entrada (Discord): {} entrou em {} — {} {} {} {}",
                entry.label(), entry.area(), entry.dimension(), entry.x(), entry.y(), entry.z());
    }

    /** Evento falso para conferir a ponte com o bot sem precisar andar ate a area. */
    public static void test(MinecraftServer server, String player, UUID uuid, ResourceLocation dimension, BlockPos pos) {
        Entry entry = new Entry(System.currentTimeMillis(), player, uuid, player, "teste", "Teste do aviso",
                dimension, pos.getX(), pos.getY(), pos.getZ(), true);
        offer(entry);
        StaffAlert.watched(server, entry);
    }

    /**
     * Esvazia a fila ate {@link #PULL_BUDGET} bytes. Primeira linha {@code avisos=N restantes=M
     * descartados=D}; o bot chama de novo enquanto {@code restantes} for maior que zero.
     */
    public static String pull() { return drain(QUEUE, PULL_BUDGET, takeDropped()); }

    private static int takeDropped() { int value = dropped; dropped = 0; return value; }

    static String drain(Deque<Entry> queue, int budget, int droppedCount) {
        StringBuilder body = new StringBuilder();
        int count = 0;
        while (!queue.isEmpty()) {
            String line = queue.peekFirst().line();
            // Uma linha sempre cabe (id <= 48, nome <= 96): nunca trava a fila com um evento so.
            if (count > 0 && body.length() + line.length() + 1 > budget) break;
            queue.pollFirst();
            body.append('\n').append(line);
            count++;
        }
        return "avisos=" + count + " restantes=" + queue.size() + " descartados=" + droppedCount + body;
    }

    public static int pending() { return QUEUE.size(); }
    public static List<AreaRegion> watchedAreas(MinecraftServer server) { return watched(server); }

    static void forget(UUID id) { INSIDE.remove(id); }
    /** Fim do servidor: a fila nao sobrevive a um restart e a revisao dos dados recomeca. */
    static void clear() {
        INSIDE.clear(); LAST.clear(); QUEUE.clear(); dropped = 0;
        watched = List.of(); watchedData = null; watchedRevision = -1;
    }
}
