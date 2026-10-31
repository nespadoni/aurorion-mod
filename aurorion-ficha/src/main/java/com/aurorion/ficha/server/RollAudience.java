package com.aurorion.ficha.server;

import com.aurorion.ficha.sheet.RollCard;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Quem vê uma rolagem, e como.
 *
 * <ul>
 *   <li>Quem rolou sempre vê ({@code own}).</li>
 *   <li>Pública: quem está até {@code radius} blocos, na mesma dimensão, vê ({@code public}; um
 *       mestre por perto vê como {@code gm}).</li>
 *   <li>Secreta: só quem rolou e os mestres online, onde estiverem ({@code gm}).</li>
 *   <li>Pedida pelo mestre: o mestre que pediu sempre recebe ({@code gm}), mesmo longe ou em outra
 *       dimensão.</li>
 * </ul>
 *
 * <p>A distância é a 3D, como o "raio" de um anúncio local. Uma conta por rolagem sobre a lista de
 * online — nada por tick. Sem dependência do Minecraft (testável).
 */
public final class RollAudience {
    /**
     * Um jogador online visto pela regra: posição, dimensão e se conta como mestre. {@code gm} vem de
     * quem chama ({@code FichaServer.isGm}): o <b>perfil</b> com OP — o alt nunca, nem o de staff, que
     * veria rolagens secretas e o bônus dos NPCs dos outros.
     */
    public record Viewer(UUID id, String dimension, double x, double y, double z, boolean gm) {
    }

    private RollAudience() {
    }

    /** Perfil → {@link RollCard#OWN}/{@link RollCard#GM}/{@link RollCard#PUBLIC}, quem rolou primeiro. */
    public static Map<UUID, String> of(Viewer roller, Collection<Viewer> online, boolean secret,
                                       @Nullable UUID requestingGm, int radius) {
        Map<UUID, String> out = new LinkedHashMap<>();
        out.put(roller.id(), RollCard.OWN);
        double max = (double) radius * radius;
        for (Viewer v : online) {
            if (v.id().equals(roller.id())) continue;
            boolean asked = v.id().equals(requestingGm);
            if (secret) {
                if (v.gm() || asked) out.put(v.id(), RollCard.GM);
                continue;
            }
            if (asked) {
                out.put(v.id(), RollCard.GM);
                continue;
            }
            if (!v.dimension().equals(roller.dimension())) continue;
            double dx = v.x() - roller.x(), dy = v.y() - roller.y(), dz = v.z() - roller.z();
            if (dx * dx + dy * dy + dz * dz <= max) out.put(v.id(), v.gm() ? RollCard.GM : RollCard.PUBLIC);
        }
        return out;
    }

    /**
     * O bônus desta rolagem é número privado? Quem decide é o {@code roll.private} do site (o
     * {@code PrivateActor} do motor: a ficha que rolou é um NPC). {@code npcHint} — a última ficha
     * vista do perfil era NPC, ou é um alt ainda sem ficha vista — só reforça: na dúvida, o bônus não
     * vaza. Rolagem livre não tem bônus de ficha (a fórmula é a que o jogador digitou) e nunca esconde.
     */
    public static boolean privateBonus(String rollKind, boolean sitePrivate, boolean npcHint) {
        if ("free".equals(rollKind)) return false;
        return sitePrivate || npcHint;
    }

    /**
     * Esconder o bônus? Só de quem vê como público e só quando ele é privado ({@link #privateBonus}):
     * quem controla o NPC vê (é dele), o mestre também.
     */
    public static boolean hideBonus(String view, boolean privateBonus) {
        return privateBonus && RollCard.PUBLIC.equals(view);
    }

    /** Esconder a CD? Quando o mestre pediu escondida: só ele (e outros mestres) a vê. */
    public static boolean hideTarget(String view, boolean dcHiddenByGm) {
        return dcHiddenByGm && !RollCard.GM.equals(view);
    }
}
