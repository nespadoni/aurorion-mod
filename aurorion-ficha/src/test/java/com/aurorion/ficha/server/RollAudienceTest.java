package com.aurorion.ficha.server;

import com.aurorion.ficha.sheet.RollCard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RollAudienceTest {
    private static final String WORLD = "minecraft:overworld";
    private static final RollAudience.Viewer ROLLER = viewer(WORLD, 0, 64, 0, false);

    private static RollAudience.Viewer viewer(String dimension, double x, double y, double z, boolean gm) {
        return new RollAudience.Viewer(UUID.randomUUID(), dimension, x, y, z, gm);
    }

    @Test
    void publicReachesWhoIsNearInTheSameDimension() {
        RollAudience.Viewer near = viewer(WORLD, 10, 64, 10, false);
        RollAudience.Viewer edge = viewer(WORLD, 24, 64, 0, false);
        RollAudience.Viewer far = viewer(WORLD, 30, 64, 0, false);
        RollAudience.Viewer nether = viewer("minecraft:the_nether", 1, 64, 1, false);
        RollAudience.Viewer gmNear = viewer(WORLD, 5, 64, 0, true);
        Map<UUID, String> out = RollAudience.of(ROLLER, List.of(ROLLER, near, edge, far, nether, gmNear), false, null, 24);
        assertEquals(RollCard.OWN, out.get(ROLLER.id()));
        assertEquals(RollCard.PUBLIC, out.get(near.id()));
        assertEquals(RollCard.PUBLIC, out.get(edge.id()), "o raio inclui a borda");
        assertFalse(out.containsKey(far.id()));
        assertFalse(out.containsKey(nether.id()), "outra dimensão não vê, mesmo nas mesmas coordenadas");
        assertEquals(RollCard.GM, out.get(gmNear.id()), "mestre por perto vê os números do mestre");
        assertEquals(ROLLER.id(), out.keySet().iterator().next(), "quem rolou vem primeiro");
    }

    @Test
    void heightCountsInTheDistance() {
        RollAudience.Viewer below = viewer(WORLD, 0, 30, 0, false);
        assertFalse(RollAudience.of(ROLLER, List.of(below), false, null, 24).containsKey(below.id()));
    }

    @Test
    void secretReachesOnlyTheRollerAndGmsAnywhere() {
        RollAudience.Viewer near = viewer(WORLD, 2, 64, 2, false);
        RollAudience.Viewer gmFar = viewer("minecraft:the_end", 9_000, 64, 9_000, true);
        Map<UUID, String> out = RollAudience.of(ROLLER, List.of(near, gmFar), true, null, 24);
        assertEquals(2, out.size());
        assertFalse(out.containsKey(near.id()));
        assertEquals(RollCard.GM, out.get(gmFar.id()));
    }

    @Test
    void requestingGmAlwaysReceives() {
        RollAudience.Viewer asker = viewer("minecraft:the_nether", 5_000, 64, 5_000, false);
        Map<UUID, String> open = RollAudience.of(ROLLER, List.of(asker), false, asker.id(), 24);
        assertEquals(RollCard.GM, open.get(asker.id()), "pública: chega ao mestre que pediu, mesmo longe");
        Map<UUID, String> secret = RollAudience.of(ROLLER, List.of(asker), true, asker.id(), 24);
        assertEquals(RollCard.GM, secret.get(asker.id()), "secreta: também");
    }

    @Test
    void redactionRules() {
        assertTrue(RollAudience.hideBonus(RollCard.PUBLIC, true), "bônus de NPC é privado para o público");
        assertFalse(RollAudience.hideBonus(RollCard.OWN, true), "quem controla o NPC vê o bônus (é dele)");
        assertFalse(RollAudience.hideBonus(RollCard.GM, true));
        assertFalse(RollAudience.hideBonus(RollCard.PUBLIC, false), "bônus de jogador é público");
        assertTrue(RollAudience.hideTarget(RollCard.OWN, true), "CD escondida pelo mestre: nem quem rola vê");
        assertTrue(RollAudience.hideTarget(RollCard.PUBLIC, true));
        assertFalse(RollAudience.hideTarget(RollCard.GM, true));
        assertFalse(RollAudience.hideTarget(RollCard.PUBLIC, false));
    }

    @Test
    void privateBonusFollowsTheSiteFlag() {
        assertTrue(RollAudience.privateBonus("skill", true, false), "roll.private do site manda, mesmo sem ficha NPC vista");
        assertTrue(RollAudience.privateBonus("attribute", false, true), "ficha NPC vista (ou alt sem ficha vista) reforça");
        assertFalse(RollAudience.privateBonus("skill", false, false), "ficha de jogador: bônus público");
        assertFalse(RollAudience.privateBonus("free", true, true), "rolagem livre: a fórmula é a que o jogador digitou");
    }

    @Test
    void secretByAGmReachesOtherGmsButNotPlayersNearby() {
        RollAudience.Viewer gmRoller = viewer(WORLD, 0, 64, 0, true);
        RollAudience.Viewer otherGm = viewer(WORLD, 3, 64, 3, true);
        RollAudience.Viewer player = viewer(WORLD, 1, 64, 1, false);
        Map<UUID, String> out = RollAudience.of(gmRoller, List.of(gmRoller, otherGm, player), true, null, 24);
        assertEquals(RollCard.OWN, out.get(gmRoller.id()), "quem rolou vê como dono, mesmo sendo mestre");
        assertEquals(RollCard.GM, out.get(otherGm.id()));
        assertFalse(out.containsKey(player.id()));
    }

    @Test
    void radiusZeroLeavesOnlyTheRollerAndTheAskingGm() {
        RollAudience.Viewer sameSpot = viewer(WORLD, 0.5, 64, 0, false);
        RollAudience.Viewer asker = viewer(WORLD, 100, 64, 0, true);
        Map<UUID, String> out = RollAudience.of(ROLLER, List.of(sameSpot, asker), false, asker.id(), 0);
        assertFalse(out.containsKey(sameSpot.id()));
        assertEquals(RollCard.GM, out.get(asker.id()));
        assertEquals(2, out.size());
    }

    @Test
    void askingGmRollingForHimselfIsStillTheOwner() {
        RollAudience.Viewer gm = viewer(WORLD, 0, 64, 0, true);
        Map<UUID, String> out = RollAudience.of(gm, List.of(gm), true, gm.id(), 24);
        assertEquals(Map.of(gm.id(), RollCard.OWN), out, "o mestre que pediu a si mesmo recebe um cartão só");
    }

    @Test
    void hiddenDcAndNpcBonusStayWithGmsOnly() {
        boolean privateBonus = RollAudience.privateBonus("skill", true, false);
        assertTrue(RollAudience.hideBonus(RollCard.PUBLIC, privateBonus));
        assertFalse(RollAudience.hideBonus(RollCard.GM, privateBonus), "o mestre vê o bônus do NPC");
        assertTrue(RollAudience.hideTarget(RollCard.OWN, true), "CD oculta: quem rola vê só sucesso/falha");
        assertFalse(RollAudience.hideTarget(RollCard.GM, true));
    }
}
