package com.aurorion.ficha.client;

import com.aurorion.ficha.config.FichaConfig;
import com.aurorion.ficha.network.FichaPayloads;
import com.aurorion.ficha.sheet.Binding;
import com.aurorion.ficha.sheet.Candidate;
import com.aurorion.ficha.sheet.Catalog;
import com.aurorion.ficha.sheet.DiceFormula;
import com.aurorion.ficha.sheet.FichaSheet;
import com.aurorion.ficha.sheet.Habilidades;
import com.aurorion.ficha.sheet.Json;
import com.aurorion.ficha.sheet.RollCard;
import com.aurorion.ficha.sheet.RollResult;
import com.aurorion.ficha.sheet.Texts;
import com.google.gson.JsonObject;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * O "servidor" do {@code /ficha teste}: uma ficha fictícia completa (as 22 perícias, recursos,
 * condições, técnicas, lore), candidatos para vincular, uma mesa com jogadores inventados, um pedido
 * do mestre e rolagens simuladas — tudo em memória, sem servidor nem site.
 *
 * <p>As respostas passam pelos mesmos caminhos dos pacotes de verdade ({@link FichaClient}), com o
 * selo de teste: a tela e o HUD que aparecem são os reais. Para parecer rede, algumas respostas
 * saem com atraso por uma fila curta que o tick do cliente esvazia ({@link #tick}); fila vazia é uma
 * checagem só.
 */
final class FichaTestSession {
    private static final Random RANDOM = new Random();
    /** Uma parcela da fórmula canônica ({@link DiceFormula#normalize}): sinal, N, faces e keep/drop — ou só a constante. */
    private static final Pattern TERM = Pattern.compile("([+-]?)(\\d+)(?:d(\\d+)(?:(kh|kl|dh|dl)(\\d+))?)?");
    private static final Pattern DC = Pattern.compile("CD (\\d{1,2})");
    private static final String[][] NEIGHBORS = {{"Kael Duskwind", "Procurar"}, {"Mirela Sombrafolha", "Furtividade"},
            {"Tomas Brasa", "Atletismo"}, {"Yara Lunacinza", "Intenção"}};

    private record Scheduled(long at, Runnable task) {
    }

    private static final List<Scheduled> QUEUE = new ArrayList<>();
    private static FichaSheet main = mainSheet();
    private static FichaSheet current = main;
    @Nullable
    private static Binding binding = new Binding("default", "sheet", 41, 1201, false);
    private static boolean altMode;
    private static int neighbor;
    /** {@code /ficha teste vazio}: ficha mínima e nenhum candidato no Vínculo. */
    private static boolean emptyMode;
    /** Alterna os dois pedidos fictícios. */
    private static int requests;
    /** {@code /ficha teste semficha}: a próxima rolagem volta needs_binding. */
    private static boolean lostBinding;
    /** {@code /ficha teste ocupado}: a próxima rolagem volta busy (o site segurou). */
    private static boolean busyNext;
    /**
     * O que o "servidor" responde a {@code abrir} (Atualizar, as conferidas depois do {@code /vincular}):
     * o código da variante, como o site repetiria. {@code not_linked} é a exceção: o primeiro
     * {@code abrir} faz de conta que o {@code /vincular} foi confirmado e a ficha chega.
     */
    private static String openCode = "ok";
    /** NPCs que "outra staff levou": vincular volta forbidden e a lista nova vem sem eles. */
    private static final java.util.Set<Long> TAKEN = new java.util.HashSet<>();
    /** Pedidos da mesa fictícia: id → alvos que ainda não responderam (o "Cancelar" da mesa age sobre eles). */
    private static final Map<String, Map<UUID, String>> WAITING = new java.util.HashMap<>();
    private static final Map<String, String> WAITING_TEST = new java.util.HashMap<>();

    private FichaTestSession() {
    }

    // ── Entradas do comando ─────────────────────────────────────────────────────

    static void openSheet() {
        reset();
        FichaClient.deliverView(view(FichaPayloads.OPEN | FichaPayloads.GM, "ok"), true);
        // Um pedido do mestre fictício chega pouco depois: aparece no HUD ao fechar a tela.
        later(6_000L, FichaTestSession::fakeRequest);
    }

    /**
     * Um alt que ainda não escolheu ficha, como no login: aviso no HUD e o convite
     * ({@link FichaPayloads#PROMPT}, sem OPEN) que abre a tela sozinha no Vínculo.
     */
    static void openAlt() {
        reset();
        altPrompt();
    }

    /** O mesmo convite 3 s depois: abra o inventário antes para ver a ficha esperar a tela fechar. */
    static void openAltLater() {
        reset();
        FichaHud.notice("Teste local", "Em 3 s chega o convite do alt sem ficha. Abra o inventário: a ficha espera ele fechar.", 5);
        later(3_000L, FichaTestSession::altPrompt);
    }

    private static void altPrompt() {
        altMode = true;
        binding = null;
        current = null;
        FichaHud.notice("Sem ficha", ALT_NOTICE, 14);
        FichaClient.deliverView(needsBinding(FichaPayloads.PROMPT | FichaPayloads.ALT | FichaPayloads.TAB_BIND), true);
    }

    /** O texto que o servidor põe no HUD: o {@code altSemFicha} padrão sem o ":" do fim. */
    private static final String ALT_NOTICE = "Este personagem ainda não tem ficha. Escolha qual ficha ou NPC ele usa.";

    /** A resposta do servidor para um alt sem ficha: {@code needs_binding} com os candidatos junto. */
    private static FichaPayloads.View needsBinding(int flags) {
        JsonObject json = new JsonObject();
        json.add("sheet", com.google.gson.JsonNull.INSTANCE);
        json.add("binding", com.google.gson.JsonNull.INSTANCE);
        json.add("candidates", Candidate.toJson(candidates()));
        json.addProperty("profile_name", "Dev1_alt");
        return new FichaPayloads.View(flags & ~FichaPayloads.GM, "needs_binding", Json.write(json), "", ALT_NOTICE);
    }

    /** A tela aberta recebe a ficha nova sem OPEN: a construção foi aprovada no site enquanto ela estava aberta. */
    static void openChanged() {
        reset();
        main = noBuildSheet(mainSheet().name(), true);
        current = main;
        FichaClient.deliverView(view(FichaPayloads.OPEN, "ok"), true);
        later(3_000L, () -> {
            main = mainSheet();
            current = main;
            JsonObject json = new JsonObject();
            json.add("sheet", current.toJson());
            json.add("binding", binding == null ? com.google.gson.JsonNull.INSTANCE : binding.toJson());
            json.add("candidates", new com.google.gson.JsonArray());
            json.addProperty("profile_name", playerName());
            json.add("habilidades", habilidades().toJson());
            FichaClient.deliverView(new FichaPayloads.View(0, "ok", Json.write(json), "",
                    "A ficha mudou no site; esta é a versão de agora."), true);
        });
    }

    /** Vínculo aberto; vincular o "Capitão Ordrik" volta forbidden e a lista nova chega sem ele. */
    static void openRefused() {
        reset();
        FichaClient.deliverView(view(FichaPayloads.OPEN | FichaPayloads.GM | FichaPayloads.TAB_BIND, "ok"), true);
        FichaClient.deliverStatus(new FichaPayloads.Status("vincular", "ok",
                "TESTE LOCAL — escolha \"Capitão Ordrik\": outra staff o levou antes, o site recusa."), true);
    }

    /** A ficha abre normal, mas o vínculo caiu no site: a próxima rolagem volta needs_binding. */
    static void openLostBinding() {
        reset();
        lostBinding = true;
        FichaClient.deliverView(view(FichaPayloads.OPEN | FichaPayloads.GM, "ok"), true);
        FichaClient.deliverStatus(new FichaPayloads.Status("rolar", "ok",
                "TESTE LOCAL — role qualquer teste: o site responde que este perfil ficou sem ficha."), true);
    }

    /** Erros de configuração do servidor ({@code auth}: credencial; {@code missing}: rotas da ficha desligadas). */
    static void openSetupError(String code) {
        reset();
        current = null;
        binding = null;
        openCode = code;
        FichaClient.deliverView(errorView(FichaPayloads.OPEN, code), true);
    }

    /** Uma resposta sem ficha ({@code auth}, {@code missing}, {@code not_linked}) com o texto do servidor. */
    private static FichaPayloads.View errorView(int flags, String code) {
        JsonObject json = new JsonObject();
        json.add("sheet", com.google.gson.JsonNull.INSTANCE);
        json.add("binding", com.google.gson.JsonNull.INSTANCE);
        json.add("candidates", new com.google.gson.JsonArray());
        json.addProperty("profile_name", playerName());
        String message = switch (code) {
            case "auth" -> "O servidor do jogo não conseguiu se identificar no site (credencial da integração). Avise a staff.";
            case "missing" -> "O site ainda não tem a ficha do jogo ativada. Avise a staff.";
            // O mesmo texto que o servidor manda (textos.semVinculo, aqui o padrão).
            default -> FichaConfig.get(FichaConfig.NOT_LINKED);
        };
        return new FichaPayloads.View(flags, code, Json.write(json), "", message);
    }

    /**
     * Um pedido de <b>dados</b> no HUD ("Rolar 2d6", CD 8 comparada ao total): Rolar (roda ou ficha) rola a fórmula e o
     * cartão mostra os dois d6, o total e sucesso/falha — como um pedido de dados da mesa de verdade.
     */
    static void fakeDiceRequest() {
        long now = Util.getMillis();
        FichaHud.ask(new FichaHud.Ask("dados", "Mestre Aurélio", "O mestre pede", "2d6", "CD 8 · seguir o rastro na lama",
                now, now + 180_000L, true));
        FichaClient.chime();
    }

    /** O aviso de ficha antiga do login da conta principal, pelo mesmo caminho do pacote (a tecla entra no texto). */
    static void demoOutdatedNotice() {
        FichaClient.deliverNotice(new FichaPayloads.Notice("Ficha antiga", Texts.OUTDATED_NOTICE, 12));
    }

    /** Um pedido no HUD que o mestre cancela 4 s depois: o cartão sai sozinho. */
    static void fakeCancelledRequest() {
        long now = Util.getMillis();
        FichaHud.ask(new FichaHud.Ask("cancel", "Mestre Aurélio", "O mestre pede", "Procurar", "CD 13 · o mestre vai cancelar este",
                now, now + 180_000L, true));
        FichaClient.chime();
        // O mesmo pacote que o servidor manda ao cancelar: fecha o cartão e diz o motivo na barra de ação.
        later(4_000L, () -> FichaClient.request(new FichaPayloads.Request("cancel", "", "", "Procurar", "foi cancelado pelo mestre", 0, false)));
    }

    /** A própria ficha com a construção enviada e ainda não aprovada: aviso, só técnicas e história. */
    static void openPending() {
        reset();
        main = noBuildSheet(mainSheet().name(), true);
        current = main;
        FichaClient.deliverView(view(FichaPayloads.OPEN, "ok"), true);
    }

    /** Ficha antiga: o jogador ainda não montou a construção nova no site. */
    static void openOutdated() {
        reset();
        main = noBuildSheet(mainSheet().name(), false);
        current = main;
        FichaClient.deliverView(view(FichaPayloads.OPEN, "ok"), true);
    }

    /** Ficha mínima: sem condições, técnicas nem história, recursos não iniciados e nenhum candidato no Vínculo. */
    static void openEmpty() {
        reset();
        emptyMode = true;
        main = emptySheet();
        current = main;
        FichaClient.deliverView(view(FichaPayloads.OPEN, "ok"), true);
    }

    /** Conta do site não vinculada: a explicação no lugar da ficha. */
    static void openError() {
        reset();
        current = null;
        binding = null;
        openCode = "not_linked";
        FichaClient.deliverView(errorView(FichaPayloads.OPEN, "not_linked"), true);
    }

    /** A ficha abre normal; a próxima rolagem volta busy (rolagens demais no site ou personagem num encontro). */
    static void openBusy() {
        reset();
        busyNext = true;
        FichaClient.deliverView(view(FichaPayloads.OPEN | FichaPayloads.GM, "ok"), true);
        FichaClient.deliverStatus(new FichaPayloads.Status("rolar", "ok",
                "TESTE LOCAL — role qualquer teste: o site segura a rolagem (personagem num encontro)."), true);
    }

    static void openTable() {
        FichaClient.deliverTable(table(), true);
    }

    /** O id do pedido fictício da roda (só ele é tirado do HUD na roda "sem pedido"). */
    private static final String WHEEL_ASK = "roda";

    /**
     * A roda da ficha no modo de clique (aberta pelo chat, sem a tecla segurada): de um jogador ou do
     * mestre ({@code gm}: com a fatia da mesa), com ou sem um pedido no HUD. As fatias respondem
     * localmente: Ficha/Dados/Habilidades abrem a ficha fictícia no lugar certo, a Mesa abre a mesa
     * inventada e Rolar/Recusar respondem o pedido como {@link #answer}.
     */
    static void openWheel(boolean gm, boolean withAsk) {
        reset();
        FichaHud.closeAsk(WHEEL_ASK);
        if (withAsk) {
            long now = Util.getMillis();
            FichaHud.ask(new FichaHud.Ask(WHEEL_ASK, gm ? "Mestra Ivena" : "Mestre Aurélio", "O mestre pede", "Furtividade",
                    "CD 15 · com vantagem · passar pela sentinela sem que ela vire a cabeça", now, now + 120_000L, true));
            FichaClient.chime();
        }
        FichaKeys.openWheel(false, true, gm);
    }

    /** Alterna dois pedidos: um com CD visível e vantagem, outro com CD oculta, secreto e motivo longo. */
    static void fakeRequest() {
        long now = Util.getMillis();
        boolean hidden = requests++ % 2 == 1;
        if (hidden) {
            FichaHud.ask(new FichaHud.Ask("teste2", "Mestre Aurélio", "O mestre pede", "Intenção",
                    "CD oculta · secreto · perceber o que o mercador esconde atrás do balcão", now, now + 90_000L, true));
        } else {
            FichaHud.ask(new FichaHud.Ask("teste", "Mestre Aurélio", "O mestre pede", "Furtividade", "CD 15 · com vantagem · a porta range",
                    now, now + 180_000L, true));
        }
    }

    /**
     * Cartões de exemplo, cobrindo cada estado: crítico seu, vizinho com sucesso, desvantagem com o
     * dado descartado, secreto com bônus escondido, falha crítica, pedido do mestre com vantagem e
     * CD oculta, "crítico, mas falha" com nome longo e uma rolagem livre sem d20.
     */
    static void demoHud() {
        String me = playerName();
        String mine = current == null ? me : current.name();
        FichaClient.deliverCard(card(me, mine, RollCard.OWN, false, "", false,
                roll("skill", "Furtividade", 20, 8, 15, "normal", 0)), true);
        later(900L, () -> FichaClient.deliverCard(card("Kael Duskwind", "Kael Duskwind", RollCard.PUBLIC, false, "", false,
                roll("skill", "Atletismo", 14, 3, 12, "normal", 0)), true));
        later(1_700L, () -> FichaClient.deliverCard(card("Mirela Sombrafolha", "Mirela Sombrafolha", RollCard.PUBLIC, false, "", false,
                roll("attribute", "Vigor", 3, 2, 10, "desvantagem", 11)), true));
        later(2_500L, () -> FichaClient.deliverCard(card("Velho Junco", "Velho Junco", RollCard.PUBLIC, true, "", false, true,
                roll("skill", "Enganação", 17, 0, 0, "normal", 0)), true));
        later(3_300L, () -> FichaClient.deliverCard(card("Tomas Brasa", "Tomas Brasa", RollCard.PUBLIC, false, "", false,
                roll("skill", "Acrobacia", 1, 4, 12, "normal", 0)), true));
        later(4_000L, () -> FichaClient.deliverCard(card(me, mine, RollCard.OWN, false, "teste", true,
                roll("skill", "Intenção", 16, 5, 14, "vantagem", 4)), true));
        later(5_200L, () -> FichaClient.deliverCard(card("Guardiã Celestial do Arquivo Esquecido de Vel'Thar",
                "Guardiã Celestial do Arquivo Esquecido de Vel'Thar", RollCard.PUBLIC, false, "", false,
                roll("skill", "Astros & Mistos", 20, 0, 25, "normal", 0)), true));
        later(6_400L, () -> FichaClient.deliverCard(card(me, mine, RollCard.OWN, false, "", false,
                roll("skill", "Resistência", 1, 3, 12, "normal", 0)), true));
        later(7_600L, () -> {
            List<RollResult.Die> dice = List.of(new RollResult.Die(6, 5, false), new RollResult.Die(6, 2, false), new RollResult.Die(6, 6, false));
            FichaClient.deliverCard(card(me, mine, RollCard.OWN, false, "", false,
                    new RollResult("free", "3d6+2", dice, "normal", 5, 2, 15, 0, null, false, "3d6+2")), true);
        });
    }

    /** Três pedidos de uma vez: o cartão mostra "+2 pedidos" e o do topo já está na reta final. */
    static void fakeRequests() {
        long now = Util.getMillis();
        FichaHud.ask(new FichaHud.Ask("lote1", "Mestre Aurélio", "O mestre pede", "Atletismo", "CD 12 · pular o vão entre os telhados",
                now, now + 120_000L, true));
        FichaHud.ask(new FichaHud.Ask("lote2", "Mestra Ivena", "O mestre pede", "Procurar", "CD 14 · com desvantagem · a sala está escura",
                now, now + 150_000L, true));
        FichaHud.ask(new FichaHud.Ask("lote3", "Mestre Aurélio", "O mestre pede", "Resistência", "CD 13 · o veneno sobe pelo braço",
                now - 155_000L, now + 25_000L, true));
        FichaClient.chime();
    }

    /** Um pedido com 24 s: a contagem e a barra ficam âmbar aos 20 s, e ele sai sozinho ao vencer. */
    static void fakeUrgentRequest() {
        long now = Util.getMillis();
        FichaHud.ask(new FichaHud.Ask("prazo", "Mestre Aurélio", "O mestre pede", "Atletismo", "CD 16 · segure a corda antes que ela arrebente",
                now - 156_000L, now + 24_000L, true));
        FichaClient.chime();
    }

    /** Natural 20: o seu (sucesso e "crítico, mas falha") e o de um vizinho. */
    static void demoCritical() {
        String me = playerName();
        String mine = current == null ? me : current.name();
        FichaClient.deliverCard(card(me, mine, RollCard.OWN, false, "", false, roll("skill", "Furtividade", 20, 8, 15, "normal", 0)), true);
        later(1_300L, () -> FichaClient.deliverCard(card("Kael Duskwind", "Kael Duskwind", RollCard.PUBLIC, false, "", false,
                roll("skill", "Procurar", 20, 2, 12, "normal", 0)), true));
        later(2_600L, () -> FichaClient.deliverCard(card(me, mine, RollCard.OWN, false, "", false,
                roll("skill", "Astros & Mistos", 20, 0, 25, "normal", 0)), true));
    }

    /** Natural 1 (falha crítica, seu e de um vizinho) e uma falha comum contra a CD. */
    static void demoFumble() {
        String me = playerName();
        String mine = current == null ? me : current.name();
        FichaClient.deliverCard(card(me, mine, RollCard.OWN, false, "", false, roll("skill", "Acrobacia", 1, 3, 12, "normal", 0)), true);
        later(1_300L, () -> FichaClient.deliverCard(card("Tomas Brasa", "Tomas Brasa", RollCard.PUBLIC, false, "", false,
                roll("skill", "Atletismo", 1, 4, 10, "normal", 0)), true));
        later(2_600L, () -> FichaClient.deliverCard(card(me, mine, RollCard.OWN, false, "", false,
                roll("attribute", "Vigor", 7, 2, 14, "normal", 0)), true));
    }

    /** Vantagem e depois desvantagem: os dois d20 girando, o descartado apagado e riscado. */
    static void demoAdvantage() {
        String me = playerName();
        String mine = current == null ? me : current.name();
        FichaClient.deliverCard(card(me, mine, RollCard.OWN, false, "", false, roll("skill", "Intenção", 16, 5, 14, "vantagem", 4)), true);
        later(1_400L, () -> FichaClient.deliverCard(card("Yara Lunacinza", "Yara Lunacinza", RollCard.PUBLIC, false, "", false,
                roll("skill", "Furtividade", 18, 3, 15, "vantagem", 9)), true));
        later(2_800L, () -> FichaClient.deliverCard(card(me, mine, RollCard.OWN, false, "", false,
                roll("skill", "Atletismo", 3, 2, 10, "desvantagem", 17)), true));
    }

    /** Oito cartões em pouco mais de um segundo: a pilha, o limite de seis e o corte acima da barra de itens. */
    static void demoStack() {
        String me = playerName();
        String mine = current == null ? me : current.name();
        FichaClient.deliverCard(card(me, mine, RollCard.OWN, false, "", false, roll("skill", "Procurar", d20(), 4, 13, "normal", 0)), true);
        for (int i = 0; i < 6; i++) {
            String[] who = NEIGHBORS[i % NEIGHBORS.length];
            later(150L * (i + 1), () -> FichaClient.deliverCard(card(who[0], who[0], RollCard.PUBLIC, false, "", false,
                    d20Roll("skill", who[1], d20(), 0, RANDOM.nextInt(6), 10 + RANDOM.nextInt(8), "normal")), true));
        }
        later(1_150L, () -> FichaClient.deliverCard(card(me, mine, RollCard.OWN, true, "", false,
                roll("attribute", "Sabedoria", d20(), 5, 0, "normal", 0)), true));
    }

    /** Só o aviso de alt sem ficha (o do login), sem abrir tela. */
    static void demoNotice() {
        FichaHud.notice("Sem ficha", ALT_NOTICE, 14);
    }

    /** O "Abrindo a ficha…" do HUD (como depois da tecla K) e a ficha chegando 1,6 s depois. */
    static void demoLoading() {
        reset();
        FichaHud.loading("abrir", FichaClient.OPENING_SHEET);
        later(1_600L, FichaTestSession::openSheet);
    }

    /** O "Abrindo…" e, sem tela nenhuma, o erro de site fora na barra de ação. */
    static void demoSiteDown() {
        reset();
        FichaHud.loading("abrir", FichaClient.OPENING_SHEET);
        later(1_400L, () -> FichaClient.deliverStatus(new FichaPayloads.Status("abrir", "erro",
                "TESTE LOCAL — O site não respondeu agora. Tente de novo em instantes."), true));
    }

    static void stop() {
        QUEUE.clear();
        WAITING.clear();
        WAITING_TEST.clear();
    }

    private static void reset() {
        QUEUE.clear();
        main = mainSheet();
        current = main;
        binding = new Binding("default", "sheet", main.subjectId(), main.rpgCharacterId(), false);
        altMode = false;
        emptyMode = false;
        lostBinding = false;
        busyNext = false;
        openCode = "ok";
        TAKEN.clear();
    }

    /** Chamado pelo tick do cliente: executa o que venceu. */
    static void tick() {
        if (QUEUE.isEmpty()) return;
        long now = Util.getMillis();
        List<Runnable> due = new ArrayList<>();
        for (Iterator<Scheduled> it = QUEUE.iterator(); it.hasNext(); ) {
            Scheduled s = it.next();
            if (s.at() <= now) {
                due.add(s.task());
                it.remove();
            }
        }
        due.forEach(Runnable::run);
    }

    private static void later(long ms, Runnable task) {
        if (QUEUE.size() < 64) QUEUE.add(new Scheduled(Util.getMillis() + ms, task));
    }

    // ── O que as telas mandariam ao servidor ────────────────────────────────────

    static void handle(CustomPacketPayload payload) {
        if (payload instanceof FichaPayloads.Action action) {
            switch (action.action()) {
                case "abrir" -> later(250L, FichaTestSession::reopen);
                case "candidatos" -> later(350L, () -> {
                    JsonObject json = new JsonObject();
                    json.addProperty("code", "ok");
                    json.add("candidates", Candidate.toJson(candidates()));
                    json.add("binding", binding == null ? com.google.gson.JsonNull.INSTANCE : binding.toJson());
                    FichaClient.deliverCandidates(Json.write(json), true);
                });
                case "vincular" -> later(300L, () -> bind(action.a(), parseLong(action.b())));
                case "limpar" -> later(300L, () -> {
                    current = altMode ? null : main;
                    binding = altMode ? null : new Binding("default", "sheet", main.subjectId(), main.rpgCharacterId(), false);
                    deliverAfterBind(altMode ? "needs_binding" : "ok", "TESTE LOCAL — vínculo removido.");
                });
                // Como FichaServer.notGmMessage: o alt não é mestre, nem o de um staff.
                case "mestre" -> {
                    if (altMode) FichaClient.deliverStatus(new FichaPayloads.Status("mestre", "erro",
                            "No alt você joga como jogador: a mesa do mestre fica na sua conta principal (/personagem trocar)."), true);
                    else openTable();
                }
                case "espiar" -> later(250L, () -> peek(action.a()));
                case "recusar" -> FichaHud.closeAsk(action.a());
                case "cancelar" -> later(250L, () -> cancel(action.a()));
                // A tela confere a versão de tempos em tempos; a ficha fictícia não muda sozinha.
                case "atualizar" -> { }
                default -> { }
            }
        } else if (payload instanceof FichaPayloads.Roll roll) {
            // "pedido" não vem por aqui: o pedido fictício é respondido por answer (ask.local()).
            if (!"pedido".equals(roll.kind())) later(220L, () -> ownRoll(roll));
        } else if (payload instanceof FichaPayloads.Ask ask) {
            gmAsk(ask);
        }
    }

    /**
     * {@code abrir} como o servidor responderia agora: o alt sem ficha volta {@code needs_binding} (com
     * os candidatos, na aba Vínculo), os erros de configuração se repetem e o {@code not_linked}
     * "confirma" o {@code /vincular}.
     */
    private static void reopen() {
        switch (openCode) {
            case "auth", "missing" -> FichaClient.deliverView(errorView(FichaPayloads.OPEN, openCode), true);
            case "not_linked" -> {
                openCode = "ok";
                current = main;
                binding = new Binding("default", "sheet", main.subjectId(), main.rpgCharacterId(), false);
                FichaClient.deliverView(view(FichaPayloads.OPEN | FichaPayloads.GM, "ok"), true);
            }
            default -> FichaClient.deliverView(current == null
                    ? needsBinding(FichaPayloads.OPEN | FichaPayloads.ALT | FichaPayloads.TAB_BIND)
                    : view(FichaPayloads.OPEN | FichaPayloads.GM, "ok"), true);
        }
    }

    /** Tecla do HUD sobre o pedido fictício. */
    static void answer(FichaHud.Ask ask, boolean roll) {
        if (!roll) {
            FichaHud.closeAsk(ask.id());
            return;
        }
        FichaHud.answering(ask.id());
        // O "rolando…" fica um pouco na tela, como a ida ao site. O pedido sai quando o cartão chega.
        later(700L, () -> {
            String detail = ask.detail();
            boolean hidden = detail.contains("CD oculta");
            String mode = detail.contains("com vantagem") ? "vantagem" : detail.contains("com desvantagem") ? "desvantagem" : "normal";
            Matcher dcMatch = DC.matcher(detail);
            boolean dice = Texts.isDice(ask.test());
            int dc = dcMatch.find() ? Integer.parseInt(dcMatch.group(1)) : dice ? 0 : 14;
            if (dice) {
                // Pedido de dados: a fórmula rola como no site e a CD do mestre compara o total.
                RollResult result = freeRoll(ask.test(), ask.test());
                if (result != null) {
                    FichaClient.deliverCard(card(playerName(), current == null ? playerName() : current.name(), RollCard.OWN,
                            detail.contains("secreto"), ask.id(), hidden, result.against(dc)), true);
                }
                FichaHud.closeAsk(ask.id());
                return;
            }
            FichaSheet.Skill skill = null;
            if (current != null) {
                for (FichaSheet.Skill s : current.skills()) if (s.label().equals(ask.test())) skill = s;
            }
            int bonus = skill == null ? 3 : skill.bonus();
            int a = d20(), b = d20();
            int kept = "vantagem".equals(mode) ? Math.max(a, b) : "desvantagem".equals(mode) ? Math.min(a, b) : a;
            int dropped = "normal".equals(mode) ? 0 : kept == a ? b : a;
            RollResult result = d20Roll("skill", ask.test(), kept, dropped, bonus, dc, mode);
            FichaClient.deliverCard(card(playerName(), current == null ? playerName() : current.name(), RollCard.OWN, detail.contains("secreto"),
                    ask.id(), hidden, result), true);
            FichaHud.closeAsk(ask.id());
        });
    }

    private static void ownRoll(FichaPayloads.Roll roll) {
        if (lostBinding) {
            // Como o servidor: needs_binding vira o erro da rolagem e, logo depois, a resolução nova
            // (com os candidatos, sem OPEN) leva a tela aberta ao Vínculo.
            lostBinding = false;
            altMode = true;
            current = null;
            binding = null;
            FichaClient.deliverStatus(new FichaPayloads.Status("rolar", "erro", ALT_NOTICE), true);
            later(400L, () -> FichaClient.deliverView(needsBinding(FichaPayloads.TAB_BIND | FichaPayloads.ALT), true));
            return;
        }
        if (busyNext) {
            // busy do site (FichaServer.rollMessage): a tela mostra "ocupado", o botão volta e dá para repetir.
            busyNext = false;
            FichaClient.deliverStatus(new FichaPayloads.Status("rolar", "ocupado", "O site segurou esta rolagem: este personagem"
                    + " está num encontro em andamento no site. Tente de novo em instantes."), true);
            return;
        }
        FichaSheet sheet = current;
        if (sheet == null) {
            FichaClient.deliverStatus(new FichaPayloads.Status("rolar", "erro", "Este personagem ainda não tem ficha."), true);
            return;
        }
        RollResult result;
        switch (roll.kind()) {
            case "skill" -> {
                FichaSheet.Skill skill = sheet.skill(roll.key());
                result = d20Roll("skill", skill == null ? roll.key() : skill.label(), d20(), 0, skill == null ? 0 : skill.bonus(), roll.dc(), "normal");
            }
            case "attribute" -> {
                FichaSheet.Attribute attr = sheet.attribute(roll.key());
                result = d20Roll("attribute", Catalog.attributeLabel(roll.key()), d20(), 0, attr == null ? 0 : attr.effective(), roll.dc(), "normal");
            }
            default -> {
                RollResult free = freeRoll(roll.key(), "");
                if (free == null) {
                    // Como FichaServer.roll: o motivo e o formato aceito, com exemplos (2#d20+5 entre eles).
                    FichaClient.deliverStatus(new FichaPayloads.Status("rolar", "erro", DiceFormula.explain(roll.key())), true);
                    return;
                }
                result = free;
            }
        }
        FichaClient.deliverCard(card(playerName(), sheet.name(), RollCard.OWN, roll.secret(), "", false, result), true);
        // Um vizinho rola também, para ver os cartões menores empilhando.
        if (!roll.secret()) {
            String[] who = NEIGHBORS[neighbor++ % NEIGHBORS.length];
            later(1_400L, () -> FichaClient.deliverCard(card(who[0], who[0], RollCard.PUBLIC, false, "", false,
                    d20Roll("skill", who[1], d20(), 0, RANDOM.nextInt(7), 10 + RANDOM.nextInt(8), "normal")), true));
        }
    }

    private static void gmAsk(FichaPayloads.Ask ask) {
        // O nome do teste como o servidor monta: atributo alternativo só quando difere do padrão da perícia.
        String key = ask.skill();
        String alt = "";
        if ("skill".equals(ask.kind())) {
            Catalog.Skill skill = Catalog.skill(ask.skill());
            Catalog.Attribute a = ask.attribute().isEmpty() ? null : Catalog.attribute(ask.attribute());
            if (skill != null && a != null && !a.id().equals(skill.attribute())) alt = a.id();
        } else if (!ask.attribute().isEmpty()) {
            key = ask.attribute();
        }
        String formula = ask.free() ? DiceFormula.normalize(ask.formula()) : null;
        if (ask.free() && formula == null) {
            // Como FichaServer.ask: a fórmula que o motor recusaria volta com o motivo.
            FichaClient.deliverStatus(new FichaPayloads.Status("mestre", "erro", DiceFormula.explain(ask.formula())), true);
            return;
        }
        String test = formula != null ? formula : Texts.testName(ask.kind(), key, alt);
        String id = "t" + Integer.toString(RANDOM.nextInt(36 * 36 * 36), 36);
        Map<UUID, FichaPayloads.Player> known = new java.util.HashMap<>();
        for (FichaPayloads.Player p : table().players()) known.put(p.id(), p);
        int delay = 900;
        Map<UUID, String> waiting = new java.util.LinkedHashMap<>();
        WAITING.put(id, waiting);
        WAITING_TEST.put(id, test);
        for (UUID target : ask.targets()) {
            FichaPayloads.Player who = known.get(target);
            String name = who == null ? "Jogador" : who.name();
            String sheet = who == null ? "" : who.sheet();
            waiting.put(target, name);
            FichaClient.deliverTableUpdate(new FichaPayloads.TableUpdate(id, test, target, name, "aguardando", "", ""), true);
            // Uma resposta de cada tipo aparece de vez em quando: recusa, quem sai antes de o site
            // responder e quem nunca responde (fica para o "Cancelar" da mesa ou para vencer). Quem a
            // mesa já marca sem ficha responde e volta erro, como o site faria.
            int fate = sheet.isEmpty() || "ok".equals(sheet) ? RANDOM.nextInt(10) : 9;
            if (fate == 2) continue;
            delay += 900 + RANDOM.nextInt(2_400);
            later(delay, () -> {
                if (waiting.remove(target) == null) return; // o mestre cancelou antes
                if (fate == 0) {
                    FichaClient.deliverTableUpdate(new FichaPayloads.TableUpdate(id, test, target, name, "recusou", "", ""), true);
                    return;
                }
                // Apertou rolar: "rolando" até o site responder (o Cancelar já não o alcança).
                FichaClient.deliverTableUpdate(new FichaPayloads.TableUpdate(id, test, target, name, "rolando", "", ""), true);
                later(600L + RANDOM.nextInt(600), () -> answered(ask, id, test, target, name, sheet, fate));
            });
        }
        FichaClient.deliverStatus(new FichaPayloads.Status("mestre", "ok", "TESTE LOCAL — pedido " + id + " (" + test + ") enviado a "
                + ask.targets().size() + (ask.targets().size() == 1 ? " jogador." : " jogadores.")), true);
    }

    /** O fim da resposta de um alvo fictício: erro (saiu, sem ficha) ou o cartão como o mestre o vê. */
    private static void answered(FichaPayloads.Ask ask, String id, String test, UUID target, String name, String sheet, int fate) {
        if ("sem_vinculo".equals(sheet) || "sem_ficha".equals(sheet)) {
            // FichaServer.peekMessage: a mesa fala do jogador.
            String detail = "sem_vinculo".equals(sheet) ? name + " está num alt que ainda não escolheu ficha."
                    : name + " ainda não tem ficha ativa no site.";
            FichaClient.deliverTableUpdate(new FichaPayloads.TableUpdate(id, test, target, name, "erro", detail, ""), true);
            return;
        }
        if ("sem_construcao".equals(sheet) && !ask.free()) {
            // Ficha antiga: o site recusa atributo e perícia (no_build); dados respondem normal.
            FichaClient.deliverTableUpdate(new FichaPayloads.TableUpdate(id, test, target, name, "erro",
                    name + " ainda está com a ficha antiga (sem a construção nova aprovada): peça Dados, que funciona.", ""), true);
            return;
        }
        if (fate == 1) {
            FichaClient.deliverTableUpdate(new FichaPayloads.TableUpdate(id, test, target, name, "erro",
                    "saiu do servidor antes de o site responder", ""), true);
            return;
        }
        int a = d20(), b = d20();
        int kept = "vantagem".equals(ask.mode()) ? Math.max(a, b) : "desvantagem".equals(ask.mode()) ? Math.min(a, b) : a;
        int dropped = "normal".equals(ask.mode()) ? 0 : kept == a ? b : a;
        RollResult free = ask.free() ? freeRoll(test, test) : null;
        RollResult result = free != null ? free.against(ask.dc())
                : d20Roll(ask.kind(), test, kept, dropped, 1 + RANDOM.nextInt(6), ask.dc(), ask.mode());
        RollCard card = card(name, name, RollCard.GM, ask.secret(), id, !ask.showDc() && ask.dc() > 0, result);
        String summary = Texts.resultSummary(result);
        FichaClient.deliverTableUpdate(new FichaPayloads.TableUpdate(id, test, target, name, "rolou", summary,
                Json.write(card.toJson())), true);
    }

    /** O "Cancelar" da mesa: quem ainda não respondeu vira "cancelado" (o cartão sairia do HUD deles). */
    private static void cancel(String requestId) {
        Map<UUID, String> waiting = WAITING.remove(requestId);
        if (waiting == null || waiting.isEmpty()) {
            FichaClient.deliverStatus(new FichaPayloads.Status("mestre", "erro",
                    "Esse pedido não está mais aberto: venceu, já foi cancelado ou é de outro mestre."), true);
            return;
        }
        waiting.forEach((target, name) -> FichaClient.deliverTableUpdate(
                new FichaPayloads.TableUpdate(requestId, WAITING_TEST.getOrDefault(requestId, ""), target, name, "cancelado", "", ""), true));
        FichaClient.deliverStatus(new FichaPayloads.Status("mestre", "ok",
                "TESTE LOCAL — pedido " + requestId + " cancelado para quem ainda não respondeu."), true);
    }

    private static void bind(String type, long id) {
        Candidate chosen = null;
        for (Candidate c : candidates()) if (c.sameAs(type, id)) chosen = c;
        if (chosen == null) {
            FichaClient.deliverStatus(new FichaPayloads.Status("vincular", "erro", "TESTE LOCAL — essa ficha não existe."), true);
            return;
        }
        if (chosen.npc() && chosen.staffOnly() && chosen.subjectId() == 100) {
            // forbidden, como o site responde quando outra staff ligou o NPC livre antes; a lista
            // nova chega sozinha (FichaServer.bind pede os candidatos de novo).
            TAKEN.add(chosen.subjectId());
            FichaClient.deliverStatus(new FichaPayloads.Status("vincular", "erro",
                    "O site não deixou usar esta ficha: o NPC já está ligado a outro jogador."), true);
            later(400L, () -> {
                JsonObject json = new JsonObject();
                json.addProperty("code", "ok");
                json.add("candidates", Candidate.toJson(candidates()));
                json.add("binding", binding == null ? com.google.gson.JsonNull.INSTANCE : binding.toJson());
                FichaClient.deliverCandidates(Json.write(json), true);
            });
            return;
        }
        current = chosen.npc() ? npcSheet(chosen) : main;
        binding = new Binding("explicit", chosen.subjectType(), chosen.subjectId(), 3000 + chosen.subjectId(), false);
        deliverAfterBind("ok", "TESTE LOCAL — este perfil agora usa " + chosen.name() + ".");
    }

    private static void deliverAfterBind(String code, String message) {
        int flags = FichaPayloads.TAB_BIND | FichaPayloads.GM | (altMode ? FichaPayloads.ALT : 0);
        // Limpar num alt volta needs_binding com a lista junto, como o /bind do site.
        FichaClient.deliverView("needs_binding".equals(code) ? needsBinding(flags) : view(flags, code), true);
        FichaClient.deliverStatus(new FichaPayloads.Status("vincular", "ok", message), true);
    }

    private static void peek(String uuid) {
        String name = "Jogador";
        UUID id = null;
        String hint = "";
        for (FichaPayloads.Player p : table().players()) {
            if (p.id().toString().equals(uuid)) {
                name = p.name();
                id = p.id();
                hint = p.sheet();
            }
        }
        // Quem a mesa marca sem ficha: o /peek volta o código e a explicação sobre o jogador (FichaServer.peekMessage).
        String code = "sem_vinculo".equals(hint) ? "needs_binding" : "sem_ficha".equals(hint) ? "no_sheet" : "ok";
        String message = switch (code) {
            case "needs_binding" -> name + " está num alt que ainda não escolheu ficha.";
            case "no_sheet" -> name + " ainda não tem ficha ativa no site.";
            default -> "";
        };
        FichaSheet sheet = !"ok".equals(code) ? null : name.startsWith("Orren") ? noBuildSheet(name, true) : otherSheet(name);
        JsonObject json = new JsonObject();
        json.add("sheet", sheet == null ? com.google.gson.JsonNull.INSTANCE : sheet.toJson());
        json.add("binding", com.google.gson.JsonNull.INSTANCE);
        json.add("candidates", new com.google.gson.JsonArray());
        json.addProperty("profile_name", "");
        json.addProperty("peek_name", name);
        if (sheet != null && !name.startsWith("Orren")) json.add("habilidades", peekHabilidades().toJson());
        if (id != null) json.addProperty("peek_id", id.toString());
        FichaClient.deliverView(new FichaPayloads.View(FichaPayloads.OPEN | FichaPayloads.PEEK | FichaPayloads.GM, code,
                Json.write(json), "", message), true);
    }

    // ── Montagem dos dados fictícios ────────────────────────────────────────────

    /**
     * A aba Habilidades de exemplo (o que o servidor manda no campo {@code habilidades}): os quatro
     * estados (ativa, liberada, progresso, bloqueada), ícones de item e um id de mod ausente (cai na
     * inicial), em três grupos com cores próprias.
     */
    private static Habilidades habilidades() {
        return new Habilidades(List.of(
                new Habilidades.Grupo("magias", "Magias", "#C59AFF", List.of(
                        new Habilidades.Item("Escudo Arcano", "Absorve o próximo golpe, até 8 de dano.", "Nível 1", "ativa", -1, "minecraft:amethyst_shard"),
                        new Habilidades.Item("Seta Ígnea", "Dispara uma seta de fogo: 2d6 de dano no alvo.", "Nível 2", "liberada", -1, "minecraft:blaze_powder"),
                        new Habilidades.Item("Luz Velada", "Uma luz fraca que só você enxerga.", "Nível 1", "liberada", -1, "ars_nouveau:novice_spell_book"),
                        new Habilidades.Item("Passo Sombrio", "Teleporte curto. Falta concluir o ritual da Velhafonte.", "Nível 1", "progresso", 60, "minecraft:ender_eye"),
                        new Habilidades.Item("Chamado Abissal", "Precisa de INT 3.", "", "bloqueada", -1, "minecraft:echo_shard"))),
                new Habilidades.Grupo("profissao", "Profissão", "#E0A030", List.of(
                        new Habilidades.Item("Cartógrafa", "Mapas revelam estruturas num raio maior.", "Nível 4", "ativa", -1, "minecraft:filled_map"),
                        new Habilidades.Item("Rumo ao nível 5", "Mapeie 3 regiões novas para subir.", "Nível 5", "progresso", 35, "minecraft:compass"))),
                new Habilidades.Grupo("marca", "Marca · Martírio", "#DC3C50", List.of(
                        new Habilidades.Item("Sangue Votivo", "Cura parte do dano causado; ativar custa Vida.", "Grau 1", "ativa", -1, "minecraft:redstone"),
                        new Habilidades.Item("Banquete Final", "A Ultimate da Marca libera no Grau 3.", "", "bloqueada", -1, "minecraft:nether_star")))));
    }

    /** O que a mesa vê ao espiar outro jogador: só a profissão. */
    private static Habilidades peekHabilidades() {
        return new Habilidades(List.of(new Habilidades.Grupo("profissao", "Profissão", "#E0A030", List.of(
                new Habilidades.Item("Ferreiro", "Repara e reforça armas de ferro.", "Nível 2", "ativa", -1, "minecraft:anvil"),
                new Habilidades.Item("Têmpera fina", "Forje 10 lâminas para liberar.", "", "progresso", 70, "minecraft:iron_sword")))));
    }

    private static FichaPayloads.View view(int flags, String code) {
        JsonObject json = new JsonObject();
        json.add("sheet", current == null ? com.google.gson.JsonNull.INSTANCE : current.toJson());
        json.add("binding", binding == null ? com.google.gson.JsonNull.INSTANCE : binding.toJson());
        json.add("candidates", new com.google.gson.JsonArray());
        json.addProperty("profile_name", altMode ? "Dev1_alt" : playerName());
        if (!altMode) json.add("habilidades", habilidades().toJson());
        // Mestre é a permissão do próprio perfil: o alt de um staff não é mestre (sem botão Mesa).
        int f = altMode ? (flags | FichaPayloads.ALT) & ~FichaPayloads.GM : flags;
        return new FichaPayloads.View(f, code, Json.write(json), "", "");
    }

    private static FichaPayloads.Table table() {
        List<FichaPayloads.Player> players = List.of(
                player("Kael Duskwind", "Kael_Dusk", 4, true),
                player("Mirela Sombrafolha", "mirelinha", 7, true),
                player("Tomas Brasa", "TomasB", 11, true),
                player("Yara Lunacinza", "yaraluna", 15, true),
                player("Orren Pedra-Funda", "orren_pf", 22, false),
                player("Sefi", "sefi_dev", 31, true),
                player("Bartolomeu Ventania-de-Prata da Costa Norte", "bartolomeu_vp", 36, true),
                new FichaPayloads.Player(UUID.nameUUIDFromBytes("ficha-teste:nhara".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        "nhara", "Nhara", 40, true, "sem_vinculo"),
                player("Ilo Cinzento", "ilo_c", 44, false),
                new FichaPayloads.Player(UUID.nameUUIDFromBytes("ficha-teste:dario_r".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        "dario_r", "Dário Rocha", 46, true, "sem_ficha"),
                new FichaPayloads.Player(UUID.nameUUIDFromBytes("ficha-teste:velhoeli".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        "velhoeli", "Eli Antigo", 47, true, "sem_construcao"));
        return new FichaPayloads.Table(players, 48, 180);
    }

    private static FichaPayloads.Player player(String name, String profile, int distance, boolean modded) {
        UUID id = UUID.nameUUIDFromBytes(("ficha-teste:" + profile).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new FichaPayloads.Player(id, profile, name, distance, modded);
    }

    private static List<Candidate> candidates() {
        if (emptyMode) return List.of();
        List<Candidate> out = new ArrayList<>();
        out.add(new Candidate("sheet", 41, "sheet", "Lyra Vennamor", "Cartógrafa das Marés Silenciosas", "", false, List.of()));
        out.add(new Candidate("npc", 7, "npc", "Bram Ferrolho", "Ferreiro do Porto Velho", "", false, List.of("Dev1_alt")));
        out.add(new Candidate("npc", 9, "npc", "Irmã Celessa", "Guardiã do Relicário", "", false, List.of()));
        String[][] free = {{"Capitão Ordrik", "Comandante da Guarda"}, {"A Mulher de Cinzas", "Aparição do Vazio"},
                {"Mercador Tálio", "Rotas do Leste"}, {"Velho Junco", "Pescador que viu demais"}, {"Guardiã Celestial do Arquivo Esquecido de Vel'Thar", "Constructo de vigília que ninguém lembra de ter construído"},
                {"Oráculo Mira", "Leitora do Padrão"}, {"Contrabandista Vex", "Sem endereço fixo"}};
        for (int i = 0; i < free.length; i++) {
            if (!TAKEN.contains(100L + i)) out.add(new Candidate("npc", 100 + i, "npc", free[i][0], free[i][1], "", true, List.of()));
        }
        return out;
    }

    private static FichaSheet mainSheet() {
        // Atributos: FOR 2, DES 4, VIG 3, SAB 5, INT 3, VIT 4 (SAB e DES com bônus da Marca).
        int[][] attrs = {{2, 2}, {3, 4}, {3, 3}, {4, 5}, {3, 3}, {4, 4}};
        List<FichaSheet.Attribute> attributes = new ArrayList<>();
        for (int i = 0; i < Catalog.ATTRIBUTES.size(); i++) {
            Catalog.Attribute a = Catalog.ATTRIBUTES.get(i);
            attributes.add(new FichaSheet.Attribute(a.id(), a.label(), attrs[i][0], attrs[i][1]));
        }
        int[] training = {1, 0, 3, 4, 2, 1, 3, 2, 0, 3, 1, 0, 2, 1, 2, 0, 1, 2, 0, 3, 2, 1};
        List<FichaSheet.Skill> skills = new ArrayList<>();
        for (int i = 0; i < Catalog.SKILLS.size(); i++) {
            Catalog.Skill s = Catalog.SKILLS.get(i);
            int attr = attrs[Catalog.ATTRIBUTES.indexOf(Catalog.attribute(s.attribute()))][1];
            skills.add(new FichaSheet.Skill(s.id(), s.label(), s.attribute(), training[i], attr + training[i]));
        }
        List<FichaSheet.Ability> abilities = List.of(
                new FichaSheet.Ability("Lança de Aurora", "Um feixe de luz solar condensada atravessa a linha inimiga, queimando quem estiver no caminho e deixando o chão em brasas por um turno.", "solaris", "3", 2, true),
                new FichaSheet.Ability("Mapa das Correntes", "Lê o fluxo do lugar: vantagem no próximo teste de Natureza ou Procurar feito na mesma cena.", "esoterico", "1", 1, true),
                new FichaSheet.Ability("Fogo Vivo", "Invocação de Ressonância do Solaris: remove Medo ou Pânico de um aliado próximo após um acerto.", "solaris", "4", 3, false),
                new FichaSheet.Ability("Passo de Maré", "Movimento extra de 3 m que não provoca reações, uma vez por rodada.", "", "1", 1, false),
                new FichaSheet.Ability("Selo do Farol", "Marca um ponto conhecido; enquanto a marca durar, sabe a direção dele sem testes.", "solaris", "Incomum", 1, false),
                new FichaSheet.Ability("Canção das Marés Silenciosas", "Uma melodia antiga acalma criaturas do mar. Intenção CD 13 para que não ataquem nesta cena.", "minguantis", "3", 2, false),
                new FichaSheet.Ability("Brasa Guardada", "Guarda uma centelha do último Fogo Vivo; o próximo usa uma ação a menos.", "solaris", "5", 4, false),
                new FichaSheet.Ability("Juramento do Farol que Nunca se Apaga sobre o Mar de Cinzas", "", "", "", 0, false));
        FichaSheet.Derived derived = new FichaSheet.Derived(42, 28, 30, 18, 14, 2, 9, 4, 13, 2, 3);
        FichaSheet.Resources resources = new FichaSheet.Resources(true, 31, 20, 17, 4, 5, "consciente", 1,
                List.of("sangramento", "medo", "canalizando"), 1, 0, 0, true);
        FichaSheet.Lore lore = new FichaSheet.Lore("27 invernos",
                "Nascida no Porto Velho, filha de um faroleiro que desapareceu na noite em que as marés pararam de cantar.",
                "Neutra e curiosa — fiel aos mapas, desconfiada das coroas.",
                "Fala pouco, anota tudo. Ri alto quando acerta uma rota impossível e some quando alguém pergunta pelo pai.",
                "Cabelos cor de cobre presos com uma bússola quebrada; casaco de lona salgado e tinta nos dedos.");
        return new FichaSheet(1201, "sheet", 41, "Lyra Vennamor", "Cartógrafa das Marés Silenciosas", "", "/profile/rpg/ficha/1201",
                3, 2, new FichaSheet.Element("solaris", "Solaris"), new FichaSheet.Grade(2, "Marcado Vinculado"),
                true, true, false, List.copyOf(attributes), List.copyOf(skills), derived, resources, abilities, lore, "v-teste");
    }

    private static FichaSheet npcSheet(Candidate c) {
        FichaSheet base = mainSheet();
        FichaSheet.Resources r = new FichaSheet.Resources(true, 48, 30, 22, 4, 0, "consciente", 0, List.of(), 0, 0, 0, false);
        return new FichaSheet(3000 + c.subjectId(), "npc", c.subjectId(), c.name(), c.title(), "", "", 1, 0,
                new FichaSheet.Element("abissal", "Abissal"), new FichaSheet.Grade(1, "Marcado Desperto"), true, false, false,
                base.attributes(), base.skills(), new FichaSheet.Derived(52, 30, 24, 8, 15, 4, 8, 1, 15, 1, 2), r,
                base.abilities().subList(0, 2), FichaSheet.Lore.EMPTY, "v-npc");
    }

    private static FichaSheet otherSheet(String name) {
        FichaSheet base = mainSheet();
        FichaSheet.Resources r = new FichaSheet.Resources(true, 9, 25, 26, 14, 0, "queda", 2,
                List.of("envenenado", "caido", "atordoado", "vulneravel", "ofuscado", "lentidao", "efeito_agudo", "exposto", "fratura"), 0, 1, 2, false);
        return new FichaSheet(1500 + name.length(), "sheet", 60 + name.length(), name, "Viajante de passagem", "", "", 2, 1,
                new FichaSheet.Element("vazio", "Vazio"), new FichaSheet.Grade(1, "Marcado Desperto"), true, false, false,
                base.attributes(), base.skills(), new FichaSheet.Derived(36, 26, 30, 16, 13, 1, 9, 3, 12, 1, 2), r,
                base.abilities().subList(2, 5), FichaSheet.Lore.EMPTY, "v-outro");
    }

    /** {@code pending}: construção enviada esperando a equipe; senão, ficha antiga que ninguém atualizou. */
    private static FichaSheet noBuildSheet(String name, boolean pending) {
        FichaSheet base = mainSheet();
        return new FichaSheet(1700, "sheet", 77, name, "Recém-chegado", "", "/profile/rpg/ficha/1700", 1, 0, null,
                new FichaSheet.Grade(0, "Sem Marca"), false, false, pending, List.of(), List.of(), null, FichaSheet.Resources.EMPTY, List.of(), base.lore(), "v-sem");
    }

    // ── Rolagens simuladas ──────────────────────────────────────────────────────

    private static int d20() {
        return 1 + RANDOM.nextInt(20);
    }

    /** {@code dropped} 0 = sem segundo dado. */
    private static RollResult d20Roll(String kind, String label, int kept, int dropped, int bonus, int dc, String mode) {
        List<RollResult.Die> dice = new ArrayList<>();
        dice.add(new RollResult.Die(20, kept, false));
        if (dropped > 0) dice.add(new RollResult.Die(20, dropped, true));
        int total = kept + bonus;
        Boolean success = dc > 0 ? total >= dc : null;
        return new RollResult(kind, label, dice, mode, kept, bonus, total, Math.max(0, dc), success, kept == 20,
                Texts.d20Formula(bonus));
    }

    /**
     * Rolagem livre como o motor faz: a fórmula canônica ({@link DiceFormula}) com NdF, kh/kl/dh/dl e
     * constantes; o descartado volta marcado. Sem crítico nem CD — como o site; o pedido de dados aplica
     * a CD depois ({@link RollResult#against}). {@code null} se o motor recusaria a fórmula.
     */
    @Nullable
    private static RollResult freeRoll(String expr, String label) {
        String formula = DiceFormula.normalize(expr);
        if (formula == null) return null;
        // "2#1d20+5": a expressão rola N vezes; como o site, cada uma vira um Repeat e o de cima soma tudo.
        int times = DiceFormula.repeat(formula);
        String once = formula.substring(formula.indexOf('#') + 1);
        List<RollResult.Die> dice = new ArrayList<>();
        List<RollResult.Repeat> repeats = new ArrayList<>();
        int total = 0, constant = 0;
        for (int rep = 0; rep < times; rep++) {
            List<RollResult.Die> own = new ArrayList<>();
            int[] sums = rollOnce(once, own, RollResult.MAX_DICE - dice.size());
            dice.addAll(own);
            total += sums[0];
            constant += sums[1];
            if (times > 1) repeats.add(new RollResult.Repeat(sums[0], sums[1], own, null));
        }
        int natural = dice.isEmpty() ? 0 : dice.get(0).value();
        return new RollResult("free", label.isEmpty() ? formula : label, dice, "normal", natural, constant, total, 0, null, false,
                formula, repeats);
    }

    /** Uma rolagem da expressão (sem repetição): põe até {@code room} dados em {@code dice}; devolve {total, constante}. */
    private static int[] rollOnce(String formula, List<RollResult.Die> dice, int room) {
        int total = 0, constant = 0;
        int start = dice.size();
        Matcher m = TERM.matcher(formula);
        while (m.find()) {
            int sign = "-".equals(m.group(1)) ? -1 : 1;
            if (m.group(3) == null) {
                constant += sign * Integer.parseInt(m.group(2));
                continue;
            }
            int count = Integer.parseInt(m.group(2)), sides = Integer.parseInt(m.group(3));
            String op = m.group(4) == null ? "" : m.group(4);
            int n = m.group(5) == null ? 0 : Integer.parseInt(m.group(5));
            int[] values = new int[count];
            for (int i = 0; i < count; i++) values[i] = 1 + RANDOM.nextInt(sides);
            // Ordem dos índices pelo valor: os n maiores (ou menores) ficam ou saem, conforme a operação.
            Integer[] order = new Integer[count];
            for (int i = 0; i < count; i++) order[i] = i;
            java.util.Arrays.sort(order, (a, b) -> Integer.compare(values[b], values[a]));
            boolean[] kept = new boolean[count];
            java.util.Arrays.fill(kept, op.isEmpty());
            for (int r = 0; r < count; r++) {
                int i = order[r];
                switch (op) {
                    case "kh" -> kept[i] = r < n;
                    case "kl" -> kept[i] = r >= count - n;
                    case "dh" -> kept[i] = r >= n;
                    case "dl" -> kept[i] = r < count - n;
                    default -> { }
                }
            }
            for (int i = 0; i < count; i++) {
                if (dice.size() - start < room) dice.add(new RollResult.Die(sides, values[i], !kept[i]));
                if (kept[i]) total += sign * values[i];
            }
        }
        return new int[]{total + constant, constant};
    }

    private static RollResult roll(String kind, String label, int natural, int bonus, int dc, String mode, int dropped) {
        return d20Roll(kind, label, natural, dropped, bonus, dc, mode);
    }

    private static RollCard card(String rollerName, String characterName, String view, boolean secret, String requestId,
                                 boolean dcHidden, RollResult roll) {
        return card(rollerName, characterName, view, secret, requestId, dcHidden, false, roll);
    }

    /** {@code bonusHidden}: o espectador não vê o bônus (número privado de NPC), só o total. */
    private static RollCard card(String rollerName, String characterName, String view, boolean secret, String requestId,
                                 boolean dcHidden, boolean bonusHidden, RollResult roll) {
        Minecraft minecraft = Minecraft.getInstance();
        String rollerId = minecraft.player == null ? "" : minecraft.player.getUUID().toString();
        return new RollCard(UUID.randomUUID().toString(), rollerId, rollerName, characterName, view, secret, requestId,
                dcHidden, bonusHidden, roll.redacted(bonusHidden, dcHidden && !RollCard.GM.equals(view)));
    }

    /** {@code /ficha teste vazio}: construção aprovada, mas nada além dos números. */
    private static FichaSheet emptySheet() {
        FichaSheet base = mainSheet();
        return new FichaSheet(1800, "sheet", 88, "Sem Nome Ainda", "", "", "", 1, 0, null, new FichaSheet.Grade(0, ""),
                true, false, false, base.attributes(), base.skills(), base.derived(),
                new FichaSheet.Resources(false, 0, 0, 0, 0, 0, "consciente", 0, List.of(), 0, 0, 0, false),
                List.of(), FichaSheet.Lore.EMPTY, "v-vazio");
    }

    private static String playerName() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? "Você" : minecraft.player.getGameProfile().getName();
    }

    private static long parseLong(String text) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
