package com.aurorion.ficha.server;

import com.aurorion.ficha.sheet.Candidate;
import com.aurorion.ficha.sheet.Catalog;
import com.aurorion.ficha.sheet.FichaSheet;
import com.aurorion.ficha.sheet.RollCard;
import com.aurorion.ficha.sheet.RollResult;
import com.aurorion.ficha.sheet.Texts;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.List;
import java.util.Map;

/**
 * O chat como tela: a linha de rolagem com os detalhes no hover, o pedido do mestre com
 * {@code [Rolar]}/{@code [Recusar]} clicáveis, a lista de fichas para vincular e o resumo da ficha.
 * É o que o cliente sem o mod enxerga — e também o histórico de quem tem o mod.
 *
 * <h2>Uma linha, o resto no hover</h2>
 * Cada rolagem é uma linha curta que se lê de relance — quem, o quê, o total e o resultado com a
 * cor e o símbolo ({@code ✔} sucesso, {@code ✘} falha, {@code ◆} crítico) — e o hover abre o cartão
 * inteiro: natural, bônus (ou "oculto"), total, CD (ou "oculta"), dados com o descartado riscado,
 * vantagem/desvantagem, secreto. Nada no hover passa do que a linha já redigiu: o
 * {@link RollCard} chega aqui como quem recebe pode vê-lo.
 *
 * <p>Só caracteres que a fonte do Minecraft tem (nada de emoji): o dado é {@code ⚄}. Cores do tema
 * "arquivo cósmico" da tela (violeta + dourado), as mesmas do {@code FichaUi}.
 */
final class ChatCards {
    static final int GOLD = 0xD7A857;
    static final int GOLD_HI = 0xFFE9A7;
    static final int INK = 0xF3EAD8;
    static final int SOFT = 0xBFB3D6;
    static final int FAINT = 0x8C8098;
    static final int VIOLET = 0xA78BFA;
    static final int OK = 0x4ADE80;
    static final int BAD = 0xF87171;
    static final int WARN = 0xFBBF24;
    static final int CRIT = 0xFFD76A;

    private static final String RULE = "─────────────";

    private ChatCards() {
    }

    private static Style color(int rgb) {
        return Style.EMPTY.withColor(TextColor.fromRgb(rgb));
    }

    static MutableComponent text(String text, int rgb) {
        return Component.literal(text).withStyle(color(rgb));
    }

    private static MutableComponent bold(String text, int rgb) {
        return Component.literal(text).withStyle(color(rgb).withBold(true));
    }

    /** "Rótulo   valor" de uma linha do hover. */
    private static MutableComponent row(String label, Component value) {
        return text(label + "  ", FAINT).append(value);
    }

    // ── Rolagem ─────────────────────────────────────────────────────────────────

    /** O resultado como a linha mostra: símbolo + texto, e a cor. Vazio quando não há CD nem crítico. */
    record Outcome(String text, int color) {
        boolean present() {
            return !text.isEmpty();
        }
    }

    static Outcome outcome(RollResult roll) {
        String outcome = Texts.outcome(roll);
        if (outcome.isEmpty()) return new Outcome("", GOLD);
        if (outcome.equals("CRÍTICO!")) return new Outcome("◆ CRÍTICO!", CRIT);
        if (outcome.startsWith("CRÍTICO")) return new Outcome("◆ " + outcome, WARN);
        if (outcome.contains("FALHA")) return new Outcome("✘ " + outcome, BAD);
        // Repetição com CD, misto ("1 DE 2 SUCESSOS"): nem verde nem vermelho.
        if (!outcome.equals("SUCESSO")) return new Outcome("✔ " + outcome, WARN);
        return new Outcome("✔ " + outcome, OK);
    }

    /**
     * "⚄ Lyra · Furtividade ▲  17 ✔ SUCESSO" — o total na cor do resultado, o cartão inteiro no
     * hover. {@code ▲}/{@code ▼}: vantagem/desvantagem; {@code [secreto]} quando só quem rolou e os
     * mestres recebem; {@code ◆} na frente do nome quando o mestre pediu o teste.
     */
    static Component rollLine(RollCard card) {
        RollResult roll = card.roll();
        Outcome outcome = outcome(roll);
        MutableComponent line = text("⚄ ", VIOLET);
        if (card.secret()) line.append(text("[secreto] ", VIOLET));
        if (!card.requestId().isEmpty()) line.append(text("◆ ", GOLD));
        line.append(text(card.displayName(), GOLD_HI)).append(text(" · ", FAINT))
                .append(text(roll.label().isEmpty() ? "Rolagem" : roll.label(), INK));
        if ("vantagem".equals(roll.mode())) line.append(text(" ▲", OK));
        else if ("desvantagem".equals(roll.mode())) line.append(text(" ▼", BAD));
        line.append(text("  ", FAINT));
        if (roll.repeated()) {
            // "17 · 9 = 26": cada rolagem na cor dela (com CD), a soma apagada.
            appendTotals(line, roll);
            line.append(text(" = " + Texts.number(roll.total()), FAINT));
        } else {
            line.append(bold(Texts.number(roll.total()), outcome.present() ? outcome.color() : GOLD));
        }
        if (roll.hasTarget()) line.append(text(" / CD " + roll.target(), FAINT));
        else if (card.dcHidden()) line.append(text(" / CD ?", FAINT));
        if (outcome.present()) line.append(text("  " + outcome.text(), outcome.color()));
        Component details = rollHover(card, outcome);
        return line.withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, details)));
    }

    /** O cartão inteiro, para o hover da linha. */
    static Component rollHover(RollCard card, Outcome outcome) {
        RollResult roll = card.roll();
        boolean free = "free".equals(roll.kind());
        boolean d20 = !roll.dice().isEmpty() && roll.dice().get(0).sides() == 20;
        MutableComponent out = Component.empty().append(bold(roll.label().isEmpty() ? "Rolagem" : roll.label(), GOLD_HI));
        out.append(text("\n" + card.displayName(), SOFT));
        if (!card.characterName().isEmpty() && !card.characterName().equals(card.rollerName())) {
            out.append(text(" · por " + card.rollerName(), FAINT));
        }
        out.append(text("\n" + RULE, FAINT));

        if (d20 && !free) {
            int natColor = roll.naturalMax() ? CRIT : roll.naturalOne() ? BAD : INK;
            out.append("\n").append(row("Natural", text(String.valueOf(roll.natural()), natColor)));
            out.append("\n").append(row("Bônus", card.bonusHidden() ? text("oculto (número do NPC)", FAINT)
                    : text(Texts.signed(roll.bonus()), INK)));
        } else if (!roll.formula().isEmpty()) {
            out.append("\n").append(row("Fórmula", text(roll.formula(), INK)
                    .append(roll.repeated() ? text("  (" + roll.repeats().size() + " rolagens)", FAINT) : Component.empty())));
        }
        if (roll.repeated()) {
            // Uma linha por rolagem: "2ª  9  4 (d20)  ✘".
            int i = 0;
            for (RollResult.Repeat r : roll.repeats()) {
                MutableComponent value = bold(Texts.number(r.total()), repeatColor(r));
                if (!r.dice().isEmpty()) value.append(text("  ", FAINT)).append(dice(r.dice()));
                if (r.success() != null) value.append(text(r.success() ? "  ✔" : "  ✘", r.success() ? OK : BAD));
                out.append("\n").append(row(++i + "ª", value));
            }
            out.append("\n").append(row("Soma", text(Texts.number(roll.total()), INK)));
        } else {
            if (!roll.dice().isEmpty()) out.append("\n").append(row("Dados", dice(roll)));
            out.append("\n").append(row("Total", bold(Texts.number(roll.total()), outcome.present() ? outcome.color() : GOLD)));
        }

        if (roll.hasTarget()) {
            out.append("\n").append(row("CD", text(String.valueOf(roll.target()), INK)
                    .append(card.dcHidden() ? text("  (oculta dos jogadores)", FAINT) : Component.empty())));
        } else if (card.dcHidden()) {
            out.append("\n").append(row("CD", text("oculta pelo mestre", FAINT)));
        }
        if (outcome.present()) out.append("\n").append(row("Resultado", text(outcome.text(), outcome.color())));
        String mode = Texts.modeLabel(roll.mode());
        if (!mode.isEmpty()) {
            out.append("\n").append(text(Character.toUpperCase(mode.charAt(0)) + mode.substring(1)
                    + ": dois d20, vale o " + ("vantagem".equals(roll.mode()) ? "maior" : "menor") + ".", SOFT));
        }

        if (card.secret() || !card.requestId().isEmpty()) out.append(text("\n" + RULE, FAINT));
        if (card.secret()) out.append(text("\nRolagem secreta: só quem rolou e os mestres veem.", FAINT));
        if (!card.requestId().isEmpty()) out.append(text("\nPedido do mestre · " + card.requestId(), FAINT));
        return out;
    }

    /** "17 · 9" com cada total na cor da comparação dele (sem CD, dourado). */
    private static void appendTotals(MutableComponent line, RollResult roll) {
        boolean first = true;
        for (RollResult.Repeat r : roll.repeats()) {
            if (!first) line.append(text(" · ", FAINT));
            first = false;
            line.append(bold(Texts.number(r.total()), repeatColor(r)));
        }
    }

    private static int repeatColor(RollResult.Repeat r) {
        return r.success() == null ? GOLD : r.success() ? OK : BAD;
    }

    /** "12 · 7" com o dado descartado (vantagem/desvantagem, {@code kh}/{@code kl}) riscado. */
    private static MutableComponent dice(RollResult roll) {
        return dice(roll.dice());
    }

    private static MutableComponent dice(List<RollResult.Die> dice) {
        MutableComponent out = Component.empty();
        if (dice.isEmpty()) return out;
        boolean first = true;
        for (RollResult.Die d : dice) {
            if (!first) out.append(text(" · ", FAINT));
            first = false;
            String value = String.valueOf(d.value());
            if (d.dropped()) {
                out.append(Component.literal(value).withStyle(color(FAINT).withStrikethrough(true)));
            } else {
                int rgb = d.sides() == 20 && d.value() == 20 ? CRIT : d.sides() == 20 && d.value() == 1 ? BAD : INK;
                out.append(text(value, rgb));
            }
        }
        RollResult.Die firstDie = dice.get(0);
        boolean sameSides = true;
        for (RollResult.Die d : dice) sameSides &= d.sides() == firstDie.sides();
        if (sameSides && firstDie.sides() > 0) out.append(text("  (d" + firstDie.sides() + ")", FAINT));
        return out;
    }

    // ── Pedido do mestre ────────────────────────────────────────────────────────

    /**
     * "◆ O mestre pede: Furtividade · CD 15 · ▲ vantagem  “ouvir a porta”  [Rolar] [Recusar]"; num
     * pedido de dados, "◆ O mestre pede: rolar 2d6 · CD 8 …" (a CD compara o total).
     * Tudo sai da {@link GmRequests.Spec} (a CD oculta nunca vira número aqui); {@code detail} fica
     * por compatibilidade e só entra no hover quando a especificação não tem o que mostrar.
     */
    static Component requestLine(String title, GmRequests.Request request, String test, String detail) {
        GmRequests.Spec spec = request.spec();
        String command = "/ficha pedido " + request.id();
        MutableComponent head = text("◆ " + title + ": ", GOLD);
        if (spec.free()) head.append(text("rolar ", GOLD));
        head.append(bold(test, GOLD_HI));
        if (spec.dc() > 0) head.append(text(" · ", FAINT)).append(text(spec.showDc() ? "CD " + spec.dc() : "CD oculta", SOFT));
        if ("vantagem".equals(spec.mode())) head.append(text(" · ▲ vantagem", OK));
        else if ("desvantagem".equals(spec.mode())) head.append(text(" · ▼ desvantagem", BAD));
        if (spec.secret()) head.append(text(" · secreto", VIOLET));
        if (!spec.label().isEmpty()) {
            String label = spec.label().length() > 40 ? spec.label().substring(0, 39) + "…" : spec.label();
            head.append(text("  “" + label + "”", SOFT).withStyle(ChatFormatting.ITALIC));
        }
        Component details = requestHover(title, request, test, detail);
        head.withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, details)));

        MutableComponent line = Component.empty().append(head).append("  ");
        line.append(button("[Rolar]", OK, command, text("Rolar " + test + " agora", INK)
                .append(text("\n" + command, FAINT))));
        line.append(" ").append(button("[Recusar]", BAD, command + " recusar", text("Avisar o mestre que você não vai rolar", INK)));
        return line;
    }

    private static Component requestHover(String title, GmRequests.Request request, String test, String detail) {
        GmRequests.Spec spec = request.spec();
        MutableComponent out = Component.empty().append(bold(title, GOLD_HI));
        if (!request.gmName().isEmpty()) out.append(text("\npor " + request.gmName(), SOFT));
        out.append(text("\n" + RULE, FAINT));
        if (spec.free()) {
            out.append("\n").append(row("Dados", text(test, INK).append(text("  (rolagem livre)", FAINT))));
        } else {
            out.append("\n").append(row("Teste", text(test, INK)));
        }
        if (spec.dc() > 0) {
            MutableComponent dc = spec.showDc() ? text(String.valueOf(spec.dc()), INK)
                    : text("oculta — você vê só sucesso ou falha", FAINT);
            if (spec.free()) dc.append(text("  (o total precisa chegar nela)", FAINT));
            out.append("\n").append(row("CD", dc));
        } else {
            out.append("\n").append(row("CD", text("nenhuma (vale o total)", FAINT)));
        }
        String mode = Texts.modeLabel(spec.mode());
        if (!mode.isEmpty()) out.append("\n").append(row("Modo", text(mode, "vantagem".equals(spec.mode()) ? OK : BAD)));
        if (!spec.label().isEmpty()) out.append("\n").append(row("Motivo", text(spec.label(), INK)));
        else if (spec.dc() == 0 && mode.isEmpty() && !detail.isEmpty()) out.append("\n").append(text(detail, SOFT));
        if (spec.secret()) out.append(text("\nSecreto: o resultado vai só para você e os mestres.", VIOLET));
        long seconds = Math.max(0L, (request.expiresAt() - request.createdAt()) / 1000L);
        out.append(text("\n" + RULE, FAINT));
        out.append(text("\nVale por " + duration(seconds) + " · pedido " + request.id(), FAINT));
        return out;
    }

    /** "3 min", "90 s". */
    static String duration(long seconds) {
        if (seconds >= 120 && seconds % 60 == 0) return seconds / 60 + " min";
        if (seconds >= 120) return Math.round(seconds / 60.0) + " min";
        return seconds + " s";
    }

    /**
     * Para o mestre sem o mod: "✔ Pedido ab3k · Furtividade → 3 jogadores  [Cancelar]" com os alvos
     * no hover; o {@code [Cancelar]} roda {@code /ficha mestre cancelar <id>}.
     */
    static Component requestSentLine(GmRequests.Request request, String test) {
        return requestSentLine(request, test, "/ficha mestre cancelar " + request.id());
    }

    /** Como {@link #requestSentLine(GmRequests.Request, String)}, com outro comando; vazio esconde o {@code [Cancelar]}. */
    static Component requestSentLine(GmRequests.Request request, String test, String cancelCommand) {
        MutableComponent targets = Component.empty().append(bold("Alvos", GOLD_HI));
        for (GmRequests.Target t : request.targets().values()) targets.append(text("\n• " + t.name(), INK));
        int count = request.targets().size();
        MutableComponent line = text("✔ Pedido " + request.id() + " · ", OK).append(text(test, GOLD_HI))
                .append(text(" → " + count + (count == 1 ? " jogador" : " jogadores"), SOFT)
                        .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, targets))));
        if (!cancelCommand.isEmpty()) {
            line.append("  ").append(button("[Cancelar]", BAD, cancelCommand, text("Tirar o pedido de quem ainda não respondeu", INK)));
        }
        return line;
    }

    /**
     * Uma resposta chegando ao mestre sem o mod: "» Lyra recusou Furtividade", "» Lyra não conseguiu
     * rolar Furtividade" (motivo no hover), "» o pedido venceu para Lyra". {@code state}: os de
     * {@code FichaPayloads.TableUpdate}. A rolagem em si ele já recebe pela {@link #rollLine}.
     */
    static Component answerLine(String name, String test, String state, String detail) {
        MutableComponent line = text("» ", FAINT);
        switch (state) {
            case "recusou" -> line.append(text(name, GOLD_HI)).append(text(" recusou ", BAD)).append(text(test, INK));
            case "expirou" -> line.append(text("o pedido de " + test + " venceu para ", FAINT)).append(text(name, GOLD_HI));
            case "cancelado" -> line.append(text("pedido de " + test + " cancelado para ", FAINT)).append(text(name, GOLD_HI));
            case "aguardando" -> line.append(text(name, GOLD_HI)).append(text(" pode responder de novo: " + test, WARN));
            case "rolou" -> line.append(text(name, GOLD_HI)).append(text(" rolou " + test, OK));
            default -> line.append(text(name, GOLD_HI)).append(text(" não conseguiu rolar ", BAD)).append(text(test, INK));
        }
        if (!detail.isEmpty()) {
            Component why = text(detail, INK);
            line.withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, why)));
            line.append(text(" ⓘ", FAINT));
        }
        return line;
    }

    /** O pedido saiu antes da resposta (venceu, o mestre cancelou ou saiu), para quem não tem o HUD. */
    static Component requestGoneLine(String requestId, String test, String reason) {
        return text("◆ ", FAINT).append(text("O pedido " + requestId + (test.isEmpty() ? "" : " (" + test + ")") + " " + reason + ".", SOFT));
    }

    // ── Vínculo ─────────────────────────────────────────────────────────────────

    static Component candidateLine(Candidate c, boolean current) {
        String kind = c.staffOnly() ? "NPC livre" : c.npc() ? "NPC" : "Ficha";
        MutableComponent line = current ? text("● ", OK) : text("○ ", FAINT);
        MutableComponent about = Component.empty().append(bold(c.name().isEmpty() ? "Sem nome" : c.name(), GOLD_HI)).append(text("\n" + kind, SOFT));
        if (!c.title().isEmpty()) about.append(text("\n" + c.title(), INK));
        if (!c.boundProfiles().isEmpty()) about.append(text("\nTambém usada por: " + String.join(", ", c.boundProfiles()), FAINT));
        if (current) about.append(text("\nEm uso neste perfil.", OK));
        line.append(text(c.name().isEmpty() ? "Sem nome" : c.name(), current ? GOLD_HI : INK)
                .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, about))))
                .append(text(" — " + kind, FAINT));
        if (!c.title().isEmpty()) line.append(text(" · " + c.title(), SOFT));
        if (current) {
            line.append(text("  em uso", OK));
        } else {
            line.append("  ").append(button("[Usar]", OK, "/ficha vincular " + c.subjectType() + " " + c.subjectId(),
                    text(c.staffOnly() ? "Liga este NPC livre a você no site e o usa neste perfil." : "Usar esta ficha neste perfil.", INK)));
        }
        if (!c.boundProfiles().isEmpty()) line.append(text("  (também em " + String.join(", ", c.boundProfiles()) + ")", FAINT));
        return line;
    }

    static Component clearLine() {
        return text("   ", FAINT).append(button("[Voltar ao padrão]", SOFT, "/ficha vincular limpar",
                text("Na conta principal, volta a usar a sua ficha ativa.", INK)));
    }

    // ── Ficha (resumo) ──────────────────────────────────────────────────────────

    /** O essencial da ficha em poucas linhas: recursos, defesa e os atributos (nome inteiro no hover). */
    static List<Component> sheetSummary(FichaSheet sheet) {
        MutableComponent head = text("◆ ", GOLD).append(bold(sheet.name().isEmpty() ? "Ficha" : sheet.name(), GOLD_HI));
        if (!sheet.title().isEmpty()) head.append(text(" — " + sheet.title(), SOFT));
        head.append(text("  Alma " + sheet.soul(), FAINT));
        if (!sheet.hasBuild()) {
            return List.of(head, text(sheet.pendingReview()
                    ? "   Construção enviada; aguardando a equipe aprovar no site."
                    : "   Ficha ainda não atualizada para o sistema novo: monte a construção no site e envie para aprovar.", FAINT));
        }
        FichaSheet.Derived d = sheet.derived();
        FichaSheet.Resources r = sheet.resources();
        MutableComponent res = text("   ", FAINT);
        if (d != null) {
            res.append(text("Vida " + r.vida() + "/" + d.vidaMax() + (r.tempHp() > 0 ? " (+" + r.tempHp() + ")" : ""), 0xFF7A85))
                    .append(text("  ", FAINT))
                    .append(text("Stamina " + r.stamina() + "/" + d.staminaMax(), 0xF3C15A)).append(text("  ", FAINT))
                    .append(text("Sanidade " + r.sanidade() + "/" + d.sanidadeMax(), 0x9AA8FF)).append(text("  ", FAINT))
                    .append(text("P.E. " + r.pe() + "/" + d.peMax(), 0x5FD8E0));
        }
        MutableComponent attrs = text("   ", FAINT);
        for (FichaSheet.Attribute a : sheet.attributes()) {
            Component name = text(Catalog.attributeLabel(a.id()) + (a.base() != a.effective() ? " (base " + a.base() + ")" : ""), INK);
            attrs.append(text(Catalog.attributeAbbreviation(a.id()) + " ", FAINT)
                    .append(text(String.valueOf(a.effective()), INK))
                    .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, name))))
                    .append(text("  ", FAINT));
        }
        if (d != null) attrs.append(text("Defesa " + d.defesa() + "  RD " + d.rd(), SOFT));
        return List.of(head, res, attrs);
    }

    static Component siteLink(String prefix, String url) {
        return text(prefix, SOFT).append(Component.literal(url).withStyle(style -> style.withColor(ChatFormatting.AQUA)
                .withUnderlined(true).withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, text("Abrir no navegador", INK)))));
    }

    /** Comandos sugeridos: clicar põe o comando na caixa do chat (não executa), o hover explica. */
    static Component commandHint(Map<String, String> commands) {
        MutableComponent out = text("   ", FAINT);
        boolean first = true;
        for (Map.Entry<String, String> e : commands.entrySet()) {
            if (!first) out.append(text("  ", FAINT));
            first = false;
            Component hover = text(e.getValue(), INK).append(text("\nClique para escrever o comando.", FAINT));
            out.append(Component.literal(e.getKey().strip()).withStyle(style -> style.withColor(TextColor.fromRgb(GOLD))
                    .withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, e.getKey()))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover))));
        }
        return out;
    }

    static MutableComponent button(String label, int rgb, String command, String hover) {
        return button(label, rgb, command, text(hover, INK));
    }

    /** Botão do chat: roda {@code command} ao clicar; o hover diz o que acontece. */
    static MutableComponent button(String label, int rgb, String command, Component hover) {
        return Component.literal(label).withStyle(style -> style.withColor(TextColor.fromRgb(rgb)).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }

    static Component info(String text) {
        return text(text, SOFT);
    }

    static Component error(String text) {
        return text(text, BAD);
    }
}
