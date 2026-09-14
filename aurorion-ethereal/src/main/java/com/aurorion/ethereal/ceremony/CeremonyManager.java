package com.aurorion.ethereal.ceremony;

import com.aurorion.ethereal.AurorionEthereal;
import com.aurorion.ethereal.config.EtherealConfig;
import com.aurorion.ethereal.house.House;
import com.aurorion.ethereal.house.HouseCatalog;
import com.aurorion.ethereal.house.HouseData;
import com.aurorion.ethereal.house.HouseManager;
import com.aurorion.ethereal.network.CeremonyClosedPayload;
import com.aurorion.ethereal.network.OpenCeremonyPayload;
import com.aurorion.ethereal.network.RevealHousePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A Cerimonia de Vinculacao: o jogador responde, o Conselho decide.
 *
 * <p>O desenho e deliberadamente em duas metades separadas no tempo, e e isso que o diferencia de um
 * questionario:
 *
 * <ol>
 *   <li><b>As respostas</b> acontecem no altar e terminam sem revelar nada. O jogador ve "aguarde a
 *       decisao do Conselho Arcano" — nao a casa.</li>
 *   <li><b>A decisao</b> e da staff, por {@code /casa cerimonia confirmar}. A contagem das respostas
 *       e uma <em>sugestao</em> que aparece pronta para ela, com um botao por casa.</li>
 * </ol>
 *
 * <p>Nada aqui roda por tick. Uma cerimonia inteira custa: um pacote de abertura com as perguntas,
 * um pacote curto por resposta, um punhado de mensagens de chat para quem conduz, e um pacote de
 * revelacao no fim. O ciclo acontece uma vez por jogador na vida do personagem (SDD §7.1).
 */
public final class CeremonyManager {
    public enum Start {
        OK,
        /** Nenhum datapack definiu pergunta — sem isso nao ha o que perguntar. */
        NO_QUESTIONS,
        /** Nenhum datapack definiu casa — nao adianta responder para nao existir destino. */
        NO_HOUSES,
        /** Ja esta respondendo agora. */
        ALREADY_RUNNING,
        /** Ja respondeu e o veredito esta com a staff. */
        AWAITING_VERDICT,
        /** Ja tem casa e o servidor nao permite refazer. */
        ALREADY_BOUND
    }

    public enum Confirm {
        OK,
        /** Nao ha veredito guardado para esse jogador. */
        NO_VERDICT,
        /** A casa pedida nao existe no catalogo. */
        UNKNOWN_HOUSE
    }

    /** Cerimonias em andamento, por jogador. Some no logout e no desligamento do servidor. */
    private static final Map<UUID, ActiveCeremony> RUNNING = new HashMap<>();

    private CeremonyManager() {
    }

    // --- Abertura ------------------------------------------------------------------------------

    public static Start start(ServerPlayer player, @Nullable ServerPlayer conductor) {
        UUID uuid = player.getUUID();
        MinecraftServer server = player.server;

        if (RUNNING.containsKey(uuid)) {
            return Start.ALREADY_RUNNING;
        }
        if (CeremonyData.get(server).verdict(uuid) != null) {
            return Start.AWAITING_VERDICT;
        }
        if (HouseData.get(server).houseOf(uuid) != null && !EtherealConfig.ALLOW_RECHOOSE.get()) {
            return Start.ALREADY_BOUND;
        }
        if (HouseCatalog.isEmpty()) {
            return Start.NO_HOUSES;
        }

        List<CeremonyQuestion> questions = CeremonyCatalog.all();
        if (questions.isEmpty()) {
            return Start.NO_QUESTIONS;
        }

        ActiveCeremony ceremony =
                new ActiveCeremony(uuid, conductor == null ? null : conductor.getUUID(), questions);
        RUNNING.put(uuid, ceremony);

        sendQuestions(player, ceremony);
        spawnCircle(player);

        if (conductor != null && conductor != player) {
            conductor.sendSystemMessage(staffPrefix()
                    .append(Component.translatable("aurorion_ethereal.ceremony.relay.started",
                            player.getDisplayName())));
        }
        return Start.OK;
    }

    /**
     * Reabre a tela de quem fechou no meio, na pergunta em que ele parou.
     *
     * <p>Sem isto, apertar Esc durante a cerimonia trancaria o jogador: o servidor continuaria com a
     * cerimonia em andamento e o altar responderia "voce ja esta respondendo" para sempre. O indice
     * que volta e o do servidor, entao fechar a tela nao adianta nada para quem quisesse repetir uma
     * pergunta.
     *
     * @return false se nao havia cerimonia em andamento.
     */
    public static boolean resume(ServerPlayer player) {
        ActiveCeremony ceremony = RUNNING.get(player.getUUID());
        if (ceremony == null) {
            return false;
        }
        sendQuestions(player, ceremony);
        return true;
    }

    private static void sendQuestions(ServerPlayer player, ActiveCeremony ceremony) {
        PacketDistributor.sendToPlayer(player, new OpenCeremonyPayload(
                ceremony.questions().stream().map(CeremonyQuestion::view).toList(),
                ceremony.currentIndex()));
    }

    // --- Respostas -----------------------------------------------------------------------------

    /**
     * Uma resposta vinda do cliente. Tudo aqui e hostil ate provar o contrario: se o indice nao for
     * o da pergunta atual, ou a opcao nao existir, o pacote e ignorado em silencio — nao ha resposta
     * util a dar a um cliente que esta mentindo, e responder so daria a ele um oraculo.
     */
    public static void answer(ServerPlayer player, int index, int option) {
        ActiveCeremony ceremony = RUNNING.get(player.getUUID());
        if (ceremony == null) {
            return;
        }

        CeremonyQuestion.Option chosen = ceremony.answer(index, option);
        if (chosen == null) {
            return;
        }

        relayAnswer(player, ceremony, chosen);
        if (ceremony.isFinished()) {
            finish(player.server, ceremony);
        }
    }

    private static void relayAnswer(ServerPlayer player, ActiveCeremony ceremony, CeremonyQuestion.Option chosen) {
        UUID conductorId = ceremony.conductor();
        if (conductorId == null) {
            return;
        }
        ServerPlayer conductor = player.server.getPlayerList().getPlayer(conductorId);
        if (conductor == null) {
            return;
        }

        House house = HouseCatalog.get(chosen.house());
        conductor.sendSystemMessage(staffPrefix()
                .append(Component.translatable("aurorion_ethereal.ceremony.relay.answer",
                        player.getDisplayName(),
                        chosen.text().copy().withStyle(ChatFormatting.YELLOW),
                        house != null ? house.coloredName() : Component.literal(chosen.house().getPath()))));
    }

    // --- Fim das perguntas ---------------------------------------------------------------------

    private static void finish(MinecraftServer server, ActiveCeremony ceremony) {
        RUNNING.remove(ceremony.player());

        Map<ResourceLocation, Integer> tally = sortedTally(ceremony.tally());
        ResourceLocation suggested = tally.keySet().stream().findFirst().orElse(null);
        ServerPlayer player = server.getPlayerList().getPlayer(ceremony.player());
        String name = player != null ? player.getGameProfile().getName() : ceremony.player().toString();

        CeremonyData.get(server).putVerdict(ceremony.player(), new CeremonyData.Verdict(
                name, tally, ceremony.summary(), suggested, System.currentTimeMillis()));

        if (player != null) {
            PacketDistributor.sendToPlayer(player, new CeremonyClosedPayload(
                    Component.translatable("aurorion_ethereal.ceremony.finished.player")));
            player.sendSystemMessage(Component.translatable("aurorion_ethereal.ceremony.finished.player")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
        }

        AurorionEthereal.LOGGER.info("Cerimonia de {} concluida; sugestao: {}", name, suggested);
        if (EtherealConfig.NOTIFY_STAFF.get()) {
            notifyStaff(server, ceremony.player(), ceremony.conductor());
        }
    }

    /**
     * Maior primeiro, empate desfeito pelo id da casa.
     *
     * <p>O desempate por id nao e decoracao: sem ele, duas casas com a mesma contagem trocariam de
     * lugar entre uma leitura e outra (a ordem de um {@code HashMap} nao e estavel entre execucoes),
     * e a "casa sugerida" mudaria sozinha entre o fim da cerimonia e a leitura da staff no dia
     * seguinte.
     */
    private static Map<ResourceLocation, Integer> sortedTally(Map<ResourceLocation, Integer> raw) {
        List<Map.Entry<ResourceLocation, Integer>> entries = new ArrayList<>(raw.entrySet());
        entries.sort(Map.Entry.<ResourceLocation, Integer>comparingByValue().reversed()
                .thenComparing(entry -> entry.getKey().toString()));

        Map<ResourceLocation, Integer> sorted = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Integer> entry : entries) {
            sorted.put(entry.getKey(), entry.getValue());
        }
        return sorted;
    }

    /** Manda o veredito para quem conduziu; sem condutor, para toda a staff online. */
    private static void notifyStaff(MinecraftServer server, UUID player, @Nullable UUID conductorId) {
        ServerPlayer conductor = conductorId == null ? null : server.getPlayerList().getPlayer(conductorId);
        if (conductor != null) {
            sendVerdict(server, conductor::sendSystemMessage, player);
            return;
        }

        for (ServerPlayer staff : server.getPlayerList().getPlayers()) {
            if (staff.hasPermissions(2)) {
                sendVerdict(server, staff::sendSystemMessage, player);
            }
        }
    }

    /**
     * O veredito formatado, incluindo um botao por casa.
     *
     * <p>Os botoes existem para que a decisao seja um clique e nao uma digitacao: o comando completo
     * tem o nome do jogador e o id da casa dentro, e errar um deles no meio de uma cerimonia ao vivo
     * e o tipo de atrito que faz staff parar de usar a ferramenta.
     */
    public static boolean sendVerdict(MinecraftServer server, java.util.function.Consumer<Component> sink, UUID player) {
        CeremonyData.Verdict verdict = CeremonyData.get(server).verdict(player);
        if (verdict == null) {
            return false;
        }

        sink.accept(Component.literal("══════════════════════════════").withStyle(ChatFormatting.GOLD));
        sink.accept(Component.translatable("aurorion_ethereal.ceremony.verdict.header",
                Component.literal(verdict.playerName()).withStyle(ChatFormatting.WHITE))
                .withStyle(ChatFormatting.GOLD));

        sink.accept(Component.translatable("aurorion_ethereal.ceremony.verdict.answers").withStyle(ChatFormatting.AQUA));
        for (String line : verdict.summary()) {
            sink.accept(Component.literal("  " + line).withStyle(ChatFormatting.DARK_GRAY));
        }

        sink.accept(Component.translatable("aurorion_ethereal.ceremony.verdict.tally").withStyle(ChatFormatting.AQUA));
        verdict.tally().forEach((id, points) -> {
            House house = HouseCatalog.get(id);
            sink.accept(Component.literal("  ")
                    .append(house != null ? house.coloredName() : Component.literal(id.toString()))
                    .append(Component.literal(": " + points).withStyle(ChatFormatting.GRAY)));
        });

        sink.accept(Component.translatable("aurorion_ethereal.ceremony.verdict.decide").withStyle(ChatFormatting.AQUA));
        MutableComponent buttons = Component.literal("  ");
        for (House house : HouseCatalog.all()) {
            boolean suggested = house.id().equals(verdict.suggested());
            buttons.append(confirmButton(verdict.playerName(), house, suggested)).append(Component.literal(" "));
        }
        sink.accept(buttons);
        sink.accept(Component.literal("══════════════════════════════").withStyle(ChatFormatting.GOLD));
        return true;
    }

    private static Component confirmButton(String playerName, House house, boolean suggested) {
        String command = "/casa cerimonia confirmar " + playerName + " " + house.id();
        MutableComponent label = Component.literal(suggested ? "[★ " : "[")
                .append(house.name().copy())
                .append(Component.literal("]"));

        return label.withStyle(style -> style
                .withColor(house.color())
                .withBold(suggested)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("aurorion_ethereal.ceremony.verdict.button_hover",
                                house.coloredName(), Component.literal(playerName)))));
    }

    // --- Decisao da staff ----------------------------------------------------------------------

    /**
     * Vincula o jogador a casa decidida e dispara a revelacao.
     *
     * <p>Com o jogador offline a revelacao vai para a fila em disco e toca no proximo login dele:
     * a cerimonia dele nao pode terminar num anuncio de chat que ele nao estava la para ver.
     */
    public static Confirm confirm(MinecraftServer server, UUID player, ResourceLocation houseId) {
        CeremonyData data = CeremonyData.get(server);
        if (data.verdict(player) == null) {
            return Confirm.NO_VERDICT;
        }

        House house = HouseCatalog.get(houseId);
        if (house == null) {
            return Confirm.UNKNOWN_HOUSE;
        }

        data.removeVerdict(player);
        HouseManager.bind(server, player, houseId);

        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            reveal(online, house);
            HouseManager.announce(server, online.getDisplayName(), house);
        } else {
            data.queueReveal(player, houseId);
        }
        return Confirm.OK;
    }

    /** A revelacao em si: tela animada no cliente e particulas na cor da casa em volta do jogador. */
    public static void reveal(ServerPlayer player, House house) {
        PacketDistributor.sendToPlayer(player, new RevealHousePayload(
                house.name(), house.motto(), house.color()));
        spawnReveal(player, house.color());
    }

    /** Uma revelacao que ficou esperando o jogador voltar. Chamada no login. */
    public static void playPendingReveal(ServerPlayer player) {
        ResourceLocation pending = CeremonyData.get(player.server).takeReveal(player.getUUID());
        if (pending == null) {
            return;
        }
        House house = HouseCatalog.get(pending);
        if (house != null) {
            reveal(player, house);
            HouseManager.announce(player.server, player.getDisplayName(), house);
        }
    }

    // --- Cancelamento e ciclo de vida -----------------------------------------------------------

    /** @return true se havia mesmo algo para cancelar (cerimonia em andamento ou veredito parado). */
    public static boolean cancel(MinecraftServer server, UUID player) {
        boolean running = RUNNING.remove(player) != null;
        boolean pending = CeremonyData.get(server).removeVerdict(player);

        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null && (running || pending)) {
            PacketDistributor.sendToPlayer(online, new CeremonyClosedPayload(
                    Component.translatable("aurorion_ethereal.ceremony.cancelled.player")));
        }
        return running || pending;
    }

    public static boolean isRunning(UUID player) {
        return RUNNING.containsKey(player);
    }

    /** Sair no meio das perguntas descarta o andamento — nada foi decidido ainda. */
    public static void forget(UUID player) {
        RUNNING.remove(player);
    }

    public static void clear() {
        RUNNING.clear();
    }

    // --- Particulas ----------------------------------------------------------------------------

    private static MutableComponent staffPrefix() {
        return Component.literal("[Cerimonia] ").withStyle(ChatFormatting.DARK_AQUA);
    }

    /** Circulo de runas ao pe do altar quando a cerimonia comeca. */
    private static void spawnCircle(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        for (int i = 0; i < 24; i++) {
            double angle = Math.PI * 2 * i / 24.0;
            level.sendParticles(ParticleTypes.ENCHANT,
                    x + 2.5 * Math.cos(angle), y + 0.5, z + 2.5 * Math.sin(angle), 4, 0.0, 0.3, 0.0, 0.04);
        }
        for (int i = 0; i < 16; i++) {
            double angle = Math.PI * 2 * i / 16.0;
            level.sendParticles(ParticleTypes.END_ROD,
                    x + 1.8 * Math.cos(angle), y + 0.1, z + 1.8 * Math.sin(angle), 2, 0.0, 0.2, 0.0, 0.03);
        }
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 10, 0.2, 0.1, 0.2, 0.05);
    }

    /** Explosao de po na cor da casa, sincronizada com a tela de revelacao. */
    private static void spawnReveal(ServerPlayer player, int color) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(
                (color >> 16 & 0xFF) / 255.0F,
                (color >> 8 & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F), 1.8F);

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        for (int i = 0; i < 32; i++) {
            double angle = Math.PI * 2 * i / 32.0;
            level.sendParticles(dust, x + 2.2 * Math.cos(angle), y + 0.1, z + 2.2 * Math.sin(angle),
                    2, 0.0, 0.05, 0.0, 0.0);
        }
        for (int i = 0; i < 20; i++) {
            double angle = Math.PI * 2 * i / 20.0;
            level.sendParticles(dust, x + 1.5 * Math.cos(angle), y + 1.0, z + 1.5 * Math.sin(angle),
                    2, 0.0, 0.05, 0.0, 0.0);
        }
        level.sendParticles(dust, x, y, z, 30, 0.3, 0.1, 0.3, 0.18);
        level.sendParticles(dust, x, y + 3.5, z, 20, 0.4, 0.0, 0.4, 0.06);
    }

    /** Vereditos parados, do mais antigo para o mais novo — o que {@code /casa cerimonia pendentes} lista. */
    public static List<Map.Entry<UUID, CeremonyData.Verdict>> pending(MinecraftServer server) {
        List<Map.Entry<UUID, CeremonyData.Verdict>> entries =
                new ArrayList<>(CeremonyData.get(server).verdicts().entrySet());
        entries.sort(Comparator.comparingLong(entry -> entry.getValue().finishedAt()));
        return entries;
    }
}
