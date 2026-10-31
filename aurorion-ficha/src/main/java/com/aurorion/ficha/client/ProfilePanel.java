package com.aurorion.ficha.client;

import com.aurorion.core.client.menu.CharacterMenu;
import com.aurorion.core.client.menu.MenuUi;
import com.aurorion.ficha.sheet.Catalog;
import com.aurorion.ficha.sheet.FichaSheet;
import com.aurorion.ficha.sheet.Texts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * O cartão do Perfil no menu: o que a aba Ficha da tela antiga mostrava, no desenho liso do menu.
 * <ul>
 *   <li>à esquerda, a vitrine: o personagem de corpo inteiro, com a armadura e as mãos em volta;</li>
 *   <li>cabeçalho: nome, título ou legado, selos de Alma, Marca e elemento;</li>
 *   <li>à esquerda, recursos (vida com a temporária, stamina, sanidade, P.E.), estado vital, condições
 *       com a explicação no hover, ultimate e cargas;</li>
 *   <li>à direita, os números de combate e os atributos — clicar num atributo rola o teste.</li>
 * </ul>
 * Lê o {@link FichaMenu} a cada quadro (nada é copiado) e guarda só as áreas de clique e o texto do hover.
 */
final class ProfilePanel implements CharacterMenu.Panel {
    static final ProfilePanel INSTANCE = new ProfilePanel();

    private static final int VIDA = 0xDC3C50, STAMINA = 0xE0A030, SANIDADE = 0x6D7CF5, PE = 0x2BB8C8;
    private static final int BAD = 0xB3261E, WARN = 0xB7791F, CYAN = 0x1F8A99;
    /** A coluna da vitrine: o personagem de corpo inteiro com o que veste e segura. */
    private static final float SHOWCASE = 104;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String[] ARMOR_NAMES = {"Cabeça", "Peito", "Pernas", "Pés"};

    private record Hit(float x, float y, float w, float h, @Nullable String attribute, String tip) {
    }

    private final List<Hit> hits = new ArrayList<>();

    private ProfilePanel() {
    }

    @Override
    public float width() {
        return 312 + SHOWCASE;
    }

    @Override
    public float height() {
        return 244;
    }

    @Override
    public void render(GuiGraphics g, float x, float y, float w, float h, int mouseX, int mouseY, float alpha) {
        hits.clear();
        showcase(g, x, y, SHOWCASE, h, mouseX, mouseY, alpha);
        x += SHOWCASE;
        w -= SHOWCASE;
        FichaSheet s = FichaMenu.sheet;
        if (s == null) {
            String title = FichaMenu.loading ? "Abrindo a ficha…" : "Sem ficha para mostrar";
            MenuUi.drawCentered(g, MenuUi.text(title), x + w / 2, y + h / 2 - 14, MenuUi.VIOLET, alpha);
            if (!FichaMenu.loading) wrapped(g, FichaMenu.emptyReason(), x + 20, y + h / 2, w - 40, MenuUi.INK, alpha, true);
            tooltip(g, mouseX, mouseY, x - SHOWCASE, y, w + SHOWCASE, h, alpha);
            return;
        }
        header(g, s, x, y, w, alpha);
        float top = y + 52;
        MenuUi.line(g, x + 10, top - 4, x + w - 10, top - 4, 1, MenuUi.argb(MenuUi.GOLD, .55f * alpha));
        FichaSheet.Derived d = s.derived();
        if (!s.hasBuild() || d == null) {
            String text = s.pendingReview()
                    ? "A ficha foi enviada e espera a aprovação da equipe. Os números aparecem aqui assim que aprovarem."
                    : "Esta ficha ainda não foi atualizada para o sistema novo. Atualize no site para ver atributos e combate.";
            wrapped(g, text, x + 14, top + 10, w - 28, MenuUi.INK, alpha, false);
            tooltip(g, mouseX, mouseY, x - SHOWCASE, y, w + SHOWCASE, h, alpha);
            return;
        }
        float lw = (w - 30) * .46f;
        resources(g, s, d, x + 10, top, lw, y + h - 8, alpha);
        combat(g, s, d, x + 20 + lw, top, w - 30 - lw, alpha);
        tooltip(g, mouseX, mouseY, x - SHOWCASE, y, w + SHOWCASE, h, alpha);
    }

    // --- vitrine -----------------------------------------------------------------------------------

    /** O personagem de corpo inteiro, virando o rosto para onde se aponta, com a armadura e as mãos em volta. */
    private void showcase(GuiGraphics g, float x, float y, float w, float h, int mouseX, int mouseY, float alpha) {
        float bx = x + 7, by = y + 7, bw = w - 7, bh = h - 14;
        MenuUi.panel(g, bx, by, bw, bh, 8, MenuUi.argb(MenuUi.VIOLET, .92f * alpha));
        MenuUi.glow(g, bx + bw / 2, by + bh * .45f, 52, MenuUi.argb(MenuUi.VIOLET_HI, .4f * alpha));
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        float cx = bx + bw / 2;
        float feet = by + bh - 34;
        MenuUi.line(g, cx - 22, feet + 2, cx + 22, feet + 2, 1.2f, MenuUi.argb(MenuUi.GOLD, .7f * alpha));
        if (alpha > .3f) MenuUi.character(g, player, cx - 26, feet - 104, cx + 26, feet, 54, mouseX, mouseY);
        for (int i = 0; i < ARMOR.length; i++) {
            slot(g, player.getItemBySlot(ARMOR[i]), bx + 4, by + 10 + i * 24, ARMOR_NAMES[i], alpha);
        }
        slot(g, player.getMainHandItem(), bx + bw - 22, by + 34, "Mão principal", alpha);
        slot(g, player.getOffhandItem(), bx + bw - 22, by + 58, "Outra mão", alpha);
        String name = player.getGameProfile().getName();
        MenuUi.drawCentered(g, MenuUi.text(MenuUi.fit(name, bw - 8)), cx, feet + 8, MenuUi.IVORY, alpha);
        String vida = Math.round(player.getHealth()) + " / " + Math.round(player.getMaxHealth()) + " ♥";
        MenuUi.drawCentered(g, MenuUi.text(vida), cx, feet + 19, MenuUi.VIOLET_HI, alpha);
    }

    private void slot(GuiGraphics g, ItemStack stack, float x, float y, String label, float alpha) {
        MenuUi.circle(g, x + 9, y + 9, 10, MenuUi.argb(MenuUi.IVORY, .16f * alpha));
        MenuUi.ring(g, x + 9, y + 9, 10, MenuUi.argb(MenuUi.GOLD, .55f * alpha));
        if (!stack.isEmpty() && alpha > .3f) g.renderItem(stack, Math.round(x + 1), Math.round(y + 1));
        String tip = stack.isEmpty() ? label + "\nVazio." : stack.getHoverName().getString() + "\n" + label;
        hits.add(new Hit(x, y, 18, 18, null, tip));
    }

    // --- cabeçalho ---------------------------------------------------------------------------------

    private static void header(GuiGraphics g, FichaSheet s, float x, float y, float w, float alpha) {
        float tx = x + 12;
        scaled(g, MenuUi.fit(s.name().isEmpty() ? "Sem nome" : s.name(), (w - 22) / 1.3f), tx, y + 10, MenuUi.VIOLET, alpha, 1.3f, false);
        String sub = !s.title().isEmpty() ? s.title() : Catalog.legacyLabel(s.legacy());
        float chipY = y + 24;
        if (!sub.isEmpty()) {
            MenuUi.draw(g, MenuUi.text(MenuUi.fit(sub, w - 22)), tx, y + 23, MenuUi.FAINT, alpha);
            chipY = y + 34;
        }
        float cxp = tx;
        cxp += MenuUi.chip(g, "Alma " + s.soul(), cxp, chipY, MenuUi.VIOLET, MenuUi.IVORY, alpha) + 3;
        if (!s.grade().label().isEmpty()) cxp += MenuUi.chip(g, s.grade().label(), cxp, chipY, MenuUi.GOLD, MenuUi.INK, alpha) + 3;
        if (s.element() != null && !s.element().label().isEmpty()) MenuUi.chip(g, s.element().label(), cxp, chipY, CYAN, MenuUi.IVORY, alpha);
    }

    // --- recursos, vital, condições ----------------------------------------------------------------

    private void resources(GuiGraphics g, FichaSheet s, FichaSheet.Derived d, float x, float y, float w, float bottom, float alpha) {
        FichaSheet.Resources r = s.resources();
        MenuUi.draw(g, MenuUi.text("RECURSOS"), x, y, MenuUi.GOLD, alpha);
        int[][] bars = {{r.vida(), d.vidaMax(), r.tempHp(), VIDA}, {r.stamina(), d.staminaMax(), 0, STAMINA},
                {r.sanidade(), d.sanidadeMax(), 0, SANIDADE}, {r.pe(), d.peMax(), 0, PE}};
        String[] names = {"Vida", "Stamina", "Sanidade", "P.E."};
        String[] about = {"Pontos de vida atuais / máximos.", "Stamina atual / máxima.",
                "Sanidade atual / máxima. Em zero, o personagem perde a decisão normal.",
                "P.E. atuais / máximos. O máximo vem da Alma (" + s.soul() + ")."};
        float ry = y + 11;
        for (int i = 0; i < bars.length; i++) {
            int[] b = bars[i];
            boolean low = b[1] > 0 && b[0] * 4 <= b[1];
            String value = b[0] + " / " + b[1] + (b[2] > 0 ? "  +" + b[2] : "");
            MenuUi.draw(g, MenuUi.text(names[i]), x, ry, b[3], alpha);
            float vw = MenuUi.font().width(MenuUi.text(value));
            MenuUi.draw(g, MenuUi.text(value), x + w - vw, ry, low ? BAD : MenuUi.INK, alpha);
            MenuUi.resource(g, x, ry + 10, w, 5, b[1] <= 0 ? 0 : b[0] / (float) b[1], b[3], alpha);
            if (b[2] > 0 && b[1] > 0) {
                MenuUi.resource(g, x, ry + 10, w * Math.min(1, b[2] / (float) b[1]), 2, 1, 0xFFE0E6, alpha);
            }
            hits.add(new Hit(x, ry - 1, w, 18, null, names[i] + " " + b[0] + " / " + b[1] + "\n" + about[i]
                    + (b[2] > 0 ? "\n+" + b[2] + " de Vida temporária: some primeiro quando o dano chega." : "")
                    + (low ? "\nUm quarto ou menos do máximo." : "")));
            ry += 20;
        }
        String vital = r.vital();
        if (!vital.isEmpty() && !"consciente".equals(vital)) {
            String label = switch (vital) {
                case "queda" -> "EM QUEDA · " + r.deathSuccesses() + "/3 sucessos · " + r.deathFailures() + "/3 falhas";
                case "estabilizado" -> "ESTABILIZADO";
                case "morto" -> "MORTO EM COMBATE";
                default -> Catalog.vitalLabel(vital).toUpperCase(Locale.ROOT);
            };
            MenuUi.draw(g, MenuUi.text(MenuUi.fit(label, w)), x, ry, BAD, alpha);
            if ("queda".equals(vital)) hits.add(new Hit(x, ry, w, 10, null, "Em queda\nTrês sucessos estabilizam; três falhas, morte."));
            ry += 11;
        }
        MenuUi.draw(g, MenuUi.text("CONDIÇÕES"), x, ry, MenuUi.GOLD, alpha);
        ry += 11;
        float cx = x;
        boolean any = false;
        for (String id : r.conditions()) {
            Catalog.Condition c = Catalog.condition(id);
            int back = switch (c.severity()) {
                case "leve" -> WARN;
                case "moderada", "grave" -> BAD;
                default -> MenuUi.VIOLET;
            };
            String severity = c.severity().isEmpty() || "estado".equals(c.severity()) ? "" : " (" + c.severity() + ")";
            String tip = c.label() + severity + (c.summary().isEmpty() ? "" : "\n" + c.summary())
                    + (c.removal().isEmpty() ? "" : "\nComo sair: " + c.removal());
            float[] pos = chip(g, c.label(), cx, ry, x, w, bottom - 16, back, alpha, tip);
            if (pos == null) break;
            cx = pos[0];
            ry = pos[1];
            any = true;
        }
        if (r.exhaustion() > 0) {
            float[] pos = chip(g, "Exaustão " + r.exhaustion(), cx, ry, x, w, bottom - 16, WARN, alpha,
                    "Exaustão nível " + r.exhaustion() + "\n" + Catalog.exhaustionSummary(r.exhaustion()));
            if (pos != null) {
                cx = pos[0];
                ry = pos[1];
            }
            any = true;
        }
        if (!any) MenuUi.draw(g, MenuUi.text("Nenhuma condição ativa."), x, ry + 2, MenuUi.FAINT, alpha);

        // Selos presos à base do cartão.
        float ex = x, ey = bottom - 12;
        if (r.ultimateAvailable()) {
            ex += chipHit(g, "Ultimate pronta", ex, ey, MenuUi.GOLD, MenuUi.INK, alpha, "A técnica suprema pode ser usada.") + 3;
        }
        if (d.markCharges() > 0) {
            int left = Math.max(0, d.markCharges() - r.chargesUsed());
            ex += chipHit(g, "Cargas " + left + "/" + d.markCharges(), ex, ey, CYAN, MenuUi.IVORY, alpha,
                    "Cargas da Marca: " + left + " de " + d.markCharges() + " restantes.") + 3;
        }
        if (!r.initialized()) {
            chipHit(g, "Recursos não iniciados", ex, ey, MenuUi.FAINT, MenuUi.IVORY, alpha, "O site ainda não registrou os recursos desta ficha.");
        }
    }

    /** Um selo que quebra a linha quando não cabe; devolve a próxima posição ou {@code null} sem espaço. */
    @Nullable
    private float[] chip(GuiGraphics g, String label, float cx, float cy, float x, float w, float maxY, int back, float alpha, String tip) {
        float cw = MenuUi.font().width(MenuUi.text(label)) + 10;
        if (cx + cw > x + w && cx > x) {
            cx = x;
            cy += 14;
        }
        if (cy + 12 > maxY) return null;
        chipHit(g, label, cx, cy, back, MenuUi.IVORY, alpha, tip);
        return new float[]{cx + cw + 3, cy};
    }

    private float chipHit(GuiGraphics g, String label, float x, float y, int back, int ink, float alpha, String tip) {
        float cw = MenuUi.chip(g, label, x, y, back, ink, alpha);
        hits.add(new Hit(x, y, cw, 12, null, tip));
        return cw;
    }

    // --- combate e atributos -----------------------------------------------------------------------

    private void combat(GuiGraphics g, FichaSheet s, FichaSheet.Derived d, float x, float y, float w, float alpha) {
        MenuUi.draw(g, MenuUi.text("COMBATE"), x, y, MenuUi.GOLD, alpha);
        String[][] stats = {
                {String.valueOf(d.defesa()), "DEF", statTip("defesa", s, d)},
                {String.valueOf(d.rd()), "RD", statTip("rd", s, d)},
                {d.movimentoM() + "m", "MOV", statTip("movimento", s, d)},
                {Texts.signed(d.iniciativaBonus()), "INIC", statTip("iniciativa", s, d)}};
        String[][] more = {
                {String.valueOf(d.fortitude()), "FORT", statTip("fortitude", s, d)},
                {Texts.signed(d.resonanceB()), "RESS", statTip("ressonancia", s, d)},
                {String.valueOf(s.soul()), "ALMA", statTip("alma", s, d)}};
        tiles(g, stats, x, y + 11, w, alpha);
        tiles(g, more, x, y + 11 + 27, w, alpha);

        float ay = y + 11 + 54 + 6;
        MenuUi.draw(g, MenuUi.text("ATRIBUTOS · clique para testar"), x, ay, MenuUi.GOLD, alpha);
        ay += 11;
        int cols = 3;
        float aw = (w - (cols - 1) * 4) / cols, ah = 34;
        List<FichaSheet.Attribute> attributes = s.attributes();
        for (int i = 0; i < attributes.size() && i < 6; i++) {
            FichaSheet.Attribute a = attributes.get(i);
            float ax = x + (i % cols) * (aw + 4), ayy = ay + (i / cols) * (ah + 4);
            String label = a.label().isEmpty() ? Catalog.attributeLabel(a.id()) : a.label();
            boolean changed = a.base() != a.effective();
            MenuUi.panel(g, ax, ayy, aw, ah, 6, MenuUi.argb(MenuUi.VIOLET, .92f * alpha));
            MenuUi.drawCentered(g, MenuUi.text(Catalog.attributeAbbreviation(a.id())), ax + aw / 2, ayy + 3, MenuUi.VIOLET_HI, alpha);
            scaled(g, String.valueOf(a.effective()), ax + aw / 2, ayy + 12, changed ? MenuUi.GOLD_HI : MenuUi.IVORY, alpha, 1.4f, true);
            MenuUi.drawCentered(g, MenuUi.text(MenuUi.fit(changed ? "base " + a.base() : label, aw - 4)), ax + aw / 2, ayy + 25,
                    MenuUi.FAINT, alpha * .9f);
            hits.add(new Hit(ax, ayy, aw, ah, a.id(), label + " " + a.effective()
                    + (changed ? "\nBase " + a.base() + ", efetivo " + a.effective() + " (Marca, condições ou efeitos)." : "")
                    + "\nClique para um teste: 1d20 " + Texts.signed(a.effective()) + "."));
        }
    }

    private void tiles(GuiGraphics g, String[][] stats, float x, float y, float w, float alpha) {
        int n = stats.length;
        float tw = (w - (n - 1) * 3) / n, th = 24;
        for (int i = 0; i < n; i++) {
            float tx = x + i * (tw + 3);
            MenuUi.panel(g, tx, y, tw, th, 5, MenuUi.argb(MenuUi.VIOLET_DEEP, .1f * alpha + .05f));
            MenuUi.outline(g, tx, y, tw, th, 5, MenuUi.argb(MenuUi.VIOLET, .35f * alpha));
            scaled(g, stats[i][0], tx + tw / 2, y + 3, MenuUi.INK, alpha, 1.15f, true);
            MenuUi.drawCentered(g, MenuUi.text(stats[i][1]), tx + tw / 2, y + 14, MenuUi.FAINT, alpha);
            hits.add(new Hit(tx, y, tw, th, null, stats[i][2]));
        }
    }

    private static int effective(FichaSheet s, String id) {
        FichaSheet.Attribute a = s.attribute(id);
        return a == null ? 0 : a.effective();
    }

    /** De onde vem cada número de combate (as fórmulas do motor, como na tela antiga). */
    private static String statTip(String id, FichaSheet s, FichaSheet.Derived d) {
        int des = effective(s, "des");
        return switch (id) {
            case "defesa" -> {
                int gear = d.defesa() - 10 - des;
                yield "Defesa " + d.defesa() + "\nA dificuldade para acertar você.\n10 + Destreza " + des
                        + (gear != 0 ? " + equipamento " + Texts.signed(gear) : "") + ".";
            }
            case "rd" -> "Redução de dano " + d.rd() + "\nSai de cada dano recebido. Vem do equipamento.";
            case "movimento" -> "Movimento " + d.movimentoM() + " m por turno"
                    + (d.movimentoM() == Math.min(13, 6 + des) ? "\n6 + Destreza " + des + ", até 13 m." : "");
            case "iniciativa" -> {
                FichaSheet.Skill intent = s.skill("intencao");
                int trained = intent == null ? 0 : intent.value();
                yield "Iniciativa " + Texts.signed(d.iniciativaBonus())
                        + (d.iniciativaBonus() == des + trained ? "\nDestreza " + des + " + treino em Intenção " + trained + "." : "");
            }
            case "fortitude" -> {
                int sab = effective(s, "sab"), vit = effective(s, "vit");
                yield "Fortitude " + d.fortitude()
                        + (d.fortitude() == (sab + vit + 1) / 2 ? "\nMetade de Sabedoria " + sab + " + Vitral " + vit + ", arredondada para cima." : "");
            }
            case "ressonancia" -> "Bônus de Ressonância " + Texts.signed(d.resonanceB()) + "\nVem da escala da Alma (" + s.soul() + ").";
            default -> "Alma " + s.soul() + "\nO grau de poder da ficha: define a escala dos números e o máximo de P.E.";
        };
    }

    // --- hover e clique ----------------------------------------------------------------------------

    private void tooltip(GuiGraphics g, int mouseX, int mouseY, float px, float py, float pw, float ph, float alpha) {
        Hit hit = at(mouseX, mouseY);
        if (hit == null) return;
        if (hit.attribute() != null) MenuUi.outline(g, hit.x(), hit.y(), hit.w(), hit.h(), 6, MenuUi.argb(MenuUi.GOLD_HI, alpha));
        var font = MenuUi.font();
        float maxW = 170;
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (String part : hit.tip().split("\n")) lines.addAll(font.split(FormattedText.of(part, MenuUi.text("").getStyle()), (int) maxW));
        float tw = 0;
        for (FormattedCharSequence line : lines) tw = Math.max(tw, font.width(line));
        float bw = tw + 12, bh = lines.size() * 10 + 8;
        float bx = Math.min(mouseX + 10, px + pw - bw - 4), by = Math.min(mouseY + 10, py + ph - bh - 4);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(0, 0, 200);
        MenuUi.panel(g, bx, by, bw, bh, 5, MenuUi.argb(MenuUi.VIOLET_DEEP, .95f * alpha));
        MenuUi.outline(g, bx, by, bw, bh, 5, MenuUi.argb(MenuUi.GOLD, .7f * alpha));
        float ly = by + 5;
        boolean first = true;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, (int) (bx + 6), (int) ly, MenuUi.argb(first ? MenuUi.GOLD_HI : MenuUi.IVORY, alpha), false);
            first = false;
            ly += 10;
        }
        pose.popPose();
    }

    @Nullable
    private Hit at(double mx, double my) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (mx >= h.x() && mx <= h.x() + h.w() && my >= h.y() && my <= h.y() + h.h()) return h;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Hit hit = at(mouseX, mouseY);
        if (hit == null || hit.attribute() == null || button != 0) return false;
        FichaMenu.rollAttribute(hit.attribute());
        return true;
    }

    // --- texto -------------------------------------------------------------------------------------

    private static void scaled(GuiGraphics g, String text, float x, float y, int rgb, float alpha, float scale, boolean centered) {
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1);
        if (centered) MenuUi.drawCentered(g, MenuUi.text(text), 0, 0, rgb, alpha);
        else MenuUi.draw(g, MenuUi.text(text), 0, 0, rgb, alpha);
        pose.popPose();
    }

    private static void wrapped(GuiGraphics g, String text, float x, float y, float w, int rgb, float alpha, boolean centered) {
        var font = MenuUi.font();
        float ly = y;
        for (FormattedCharSequence line : font.split(FormattedText.of(text, MenuUi.text("").getStyle()), (int) w)) {
            float lx = centered ? x + (w - font.width(line)) / 2 : x;
            g.drawString(font, line, (int) lx, (int) ly, MenuUi.argb(rgb, alpha), false);
            ly += 10;
        }
    }
}
