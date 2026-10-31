package com.aurorion.profissoes.npc;

import com.aurorion.core.character.SheetCheck;
import com.aurorion.profissoes.config.ProfessionsConfig;
import com.aurorion.profissoes.server.SkillCheck;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lábia no balcão do NPC: a ficha mexe no preço em dinheiro (itens pedidos em troca não mudam).
 *
 * <ul>
 *   <li><b>Diplomacia</b>: desconto fixo pelo bônus da perícia — o mesmo toda vez, e a tela já mostra o
 *   preço com ele;</li>
 *   <li><b>Intimidação</b>, quando rende mais que a Diplomacia: o desconto vem na ameaça, e cada compra testa
 *   Intimidação. Falha crítica: o NPC se ofende, recusa e não atende essa pessoa por um tempo.</li>
 * </ul>
 * Só a thread do servidor mexe; o mapa de ofendidos é podado ao crescer.
 */
public final class NpcHaggle {
    public static final String DIPLOMACY = "diplomacia";
    public static final String INTIMIDATION = "intimidacao";
    private static final int SWEEP = 512;
    /** Jogador + NPC → até quando o NPC está ofendido. */
    private static final Map<String, Long> OFFENDED = new HashMap<>();

    private NpcHaggle() {
    }

    /** Desconto em % e se ele vem da ameaça. */
    public record Haggle(int percent, boolean intimidation) {
        static final Haggle NONE = new Haggle(0, false);
    }

    public static Haggle of(ServerPlayer player) {
        if (!ProfessionsConfig.SPEC.isLoaded() || player.isCreative()) return Haggle.NONE;
        int perPoint = ProfessionsConfig.HAGGLE_PER_POINT.get();
        int cap = ProfessionsConfig.HAGGLE_CAP.get();
        int diplomacy = Math.max(0, Math.min(cap, SkillCheck.bonus(player, DIPLOMACY) * perPoint));
        int intimidation = Math.max(0, Math.min(cap, SkillCheck.bonus(player, INTIMIDATION) * perPoint));
        return intimidation > diplomacy ? new Haggle(intimidation, true) : diplomacy > 0 ? new Haggle(diplomacy, false) : Haggle.NONE;
    }

    public static NpcCatalog.Price apply(NpcCatalog.Price price, Haggle haggle) {
        if (haggle.percent() <= 0 || price.money() <= 0) return price;
        long money = Math.max(1, Math.round(price.money() * (100 - haggle.percent()) / 100.0));
        return new NpcCatalog.Price(price.item(), price.amount(), money);
    }

    /** O NPC ainda está ofendido com essa pessoa? Devolve a recusa, ou {@code null}. */
    public static String offended(ServerPlayer player, String npc, long now) {
        Long until = OFFENDED.get(key(player.getUUID(), npc));
        return until != null && until > now ? "Depois daquela ameaça, este NPC não quer conversa com você agora." : null;
    }

    /**
     * Na hora de pagar com desconto de ameaça: testa Intimidação. {@code false} = o NPC se ofendeu (falha
     * crítica) e a compra não acontece.
     */
    public static boolean pressure(ServerPlayer player, String npc, Haggle haggle, long now) {
        if (!haggle.intimidation() || haggle.percent() <= 0) return true;
        if (SkillCheck.roll(player, INTIMIDATION, ProfessionsConfig.INTIMIDATION_DC.get()) != SheetCheck.Outcome.CRIT_FAIL) return true;
        if (OFFENDED.size() >= SWEEP) OFFENDED.values().removeIf(until -> until <= now);
        OFFENDED.put(key(player.getUUID(), npc), now + ProfessionsConfig.OFFENDED_SECONDS.get() * 1000L);
        return false;
    }

    public static String label(Haggle haggle) {
        if (haggle.percent() <= 0) return "";
        return haggle.intimidation() ? " (−" + haggle.percent() + "% na ameaça)" : " (−" + haggle.percent() + "% de lábia)";
    }

    private static String key(UUID player, String npc) {
        return player + "|" + npc;
    }
}
