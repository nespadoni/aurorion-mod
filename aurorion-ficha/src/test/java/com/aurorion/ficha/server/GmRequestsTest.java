package com.aurorion.ficha.server;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GmRequestsTest {
    private static final UUID GM = UUID.randomUUID();
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final GmRequests.Spec STEALTH = new GmRequests.Spec("skill", "furtividade", "", 15, false, "vantagem", true, "a porta");

    private static Map<UUID, String> targets(UUID... ids) {
        Map<UUID, String> out = new LinkedHashMap<>();
        for (UUID id : ids) out.put(id, "jogador-" + id.toString().substring(0, 4));
        return out;
    }

    @Test
    void expiresWithoutAClock() {
        GmRequests requests = new GmRequests(new Random(1));
        GmRequests.Request r = requests.create(GM, "Mestre", STEALTH, targets(A, B), 1_000, 180_000);
        assertEquals(181_000, r.expiresAt());
        assertNotNull(requests.get(r.id(), 180_999));
        assertNull(requests.get(r.id(), 181_000), "vence exatamente no prazo");
        assertEquals(0, requests.size(), "quem toca no vencido o tira");
        assertEquals(GmRequests.Claim.NOT_FOUND, requests.claim(r.id(), A, 181_000).claim());
    }

    @Test
    void purgeRemovesOnlyExpiredAndRunsOnCreate() {
        GmRequests requests = new GmRequests(new Random(2));
        GmRequests.Request old = requests.create(GM, "Mestre", STEALTH, targets(A), 0, 1_000);
        GmRequests.Request fresh = requests.create(UUID.randomUUID(), "Outro", STEALTH, targets(B), 500, 10_000);
        assertEquals(2, requests.size());
        requests.create(UUID.randomUUID(), "Terceiro", STEALTH, targets(B), 2_000, 10_000);
        assertEquals(2, requests.size(), "criar outro limpa o vencido");
        assertNull(requests.get(old.id(), 2_000));
        assertNotNull(requests.get(fresh.id(), 2_000));
        assertEquals(2, requests.purge(20_000).size(), "purge devolve o que saiu");
        assertEquals(0, requests.size());
        assertTrue(requests.purge(20_000).isEmpty());
    }

    @Test
    void oneAnswerPerTargetAndRetryAfterFailure() {
        GmRequests requests = new GmRequests(new Random(3));
        GmRequests.Request r = requests.create(GM, "Mestre", STEALTH, targets(A, B), 0, 60_000);
        String commandId = r.target(A).commandId();
        assertNotEquals(commandId, r.target(B).commandId(), "cada alvo tem o seu command_id");

        assertEquals(GmRequests.Claim.OK, requests.claim(r.id(), A, 10).claim());
        assertEquals(GmRequests.Claim.BUSY, requests.claim(r.id(), A, 11).claim(), "segundo aperto com a resposta a caminho");
        requests.release(r.id(), A);
        GmRequests.ClaimResult again = requests.claim(r.id(), A, 12);
        assertEquals(GmRequests.Claim.OK, again.claim(), "falha de rede: pode responder de novo");
        assertEquals(commandId, again.target().commandId(), "com o mesmo command_id (o site não rola duas vezes)");

        requests.finish(r.id(), A, GmRequests.State.DONE, "17 · sucesso");
        assertEquals(GmRequests.Claim.ANSWERED, requests.claim(r.id(), A, 13).claim());
        assertEquals("17 · sucesso", r.target(A).summary());
        assertFalse(r.settled(), "B ainda não respondeu");

        assertEquals(GmRequests.Claim.NOT_TARGET, requests.claim(r.id(), UUID.randomUUID(), 14).claim());
        assertEquals(GmRequests.Claim.OK, requests.decline(r.id(), B, 15).claim());
        assertEquals(GmRequests.State.DECLINED, r.target(B).state());
        assertEquals(GmRequests.Claim.ANSWERED, requests.decline(r.id(), B, 16).claim());
        assertTrue(r.settled());
    }

    @Test
    void pendingForListsNewestFirstAndSkipsAnswered() {
        GmRequests requests = new GmRequests(new Random(4));
        GmRequests.Request first = requests.create(GM, "Mestre", STEALTH, targets(A), 0, 60_000);
        GmRequests.Request second = requests.create(GM, "Mestre", STEALTH, targets(A, B), 1, 60_000);
        assertEquals(second.id(), requests.pendingFor(A, 2).get(0).id());
        assertEquals(2, requests.pendingFor(A, 2).size());
        requests.decline(second.id(), A, 3);
        assertEquals(first.id(), requests.pendingFor(A, 4).get(0).id());
        assertEquals(1, requests.pendingFor(B, 4).size());
    }

    @Test
    void capsTargetsAndRequestsPerGm() {
        GmRequests requests = new GmRequests(new Random(5));
        Map<UUID, String> many = new LinkedHashMap<>();
        for (int i = 0; i < GmRequests.MAX_TARGETS + 12; i++) many.put(UUID.randomUUID(), "p" + i);
        GmRequests.Request big = requests.create(GM, "Mestre", STEALTH, many, 0, 60_000);
        assertEquals(GmRequests.MAX_TARGETS, big.targets().size());

        String oldest = big.id();
        for (int i = 0; i < GmRequests.MAX_PER_GM; i++) requests.create(GM, "Mestre", STEALTH, targets(A), i + 1, 60_000);
        assertNull(requests.get(oldest, 100), "o mestre com pedidos demais perde o mais antigo");
        assertEquals(GmRequests.MAX_PER_GM, requests.size());
    }

    @Test
    void idsAreShortAndUniqueAmongActive() {
        GmRequests requests = new GmRequests(new Random(6));
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < GmRequests.MAX_ACTIVE; i++) {
            String id = requests.create(UUID.randomUUID(), "M", STEALTH, targets(A), 0, 60_000).id();
            assertTrue(id.length() >= 4 && id.length() <= 6, id);
            assertTrue(id.matches("[a-z2-9]+"), "sem caracteres ambíguos: " + id);
            assertTrue(ids.add(id), "id repetido entre ativos: " + id);
        }
    }

    @Test
    void leavingFailsPendingTargetsAndDropsTheGmRequests() {
        GmRequests requests = new GmRequests(new Random(7));
        GmRequests.Request r = requests.create(GM, "Mestre", STEALTH, targets(A, B), 0, 60_000);
        GmRequests.Left left = requests.forget(A);
        assertEquals(GmRequests.State.FAILED, r.target(A).state());
        assertEquals("saiu do servidor", r.target(A).summary());
        assertEquals(GmRequests.State.PENDING, r.target(B).state());
        assertEquals(1, left.abandoned().size(), "a mesa do mestre fica sabendo que A saiu");
        assertTrue(left.dropped().isEmpty());

        GmRequests.Left gmLeft = requests.forget(GM);
        assertNull(requests.get(r.id(), 1), "o mestre saiu: o pedido some");
        assertEquals(1, gmLeft.dropped().size(), "para tirar o cartão de quem ainda não respondeu");
        assertTrue(gmLeft.abandoned().isEmpty());
    }

    @Test
    void leavingWithTheAnswerInFlightKeepsItForTheGm() {
        GmRequests requests = new GmRequests(new Random(8));
        GmRequests.Request r = requests.create(GM, "Mestre", STEALTH, targets(A), 0, 60_000);
        assertEquals(GmRequests.Claim.OK, requests.claim(r.id(), A, 1).claim());
        GmRequests.Left left = requests.forget(A);
        assertEquals(GmRequests.State.ROLLING, r.target(A).state(), "a rolagem já foi ao site: o resultado ainda chega");
        assertTrue(left.abandoned().isEmpty());
        requests.finish(r.id(), A, GmRequests.State.DONE, "12");
        assertTrue(r.settled());
    }

    @Test
    void onlyTheTargetCanAnswerOrDecline() {
        GmRequests requests = new GmRequests(new Random(9));
        GmRequests.Request r = requests.create(GM, "Mestre", STEALTH, targets(A), 0, 60_000);
        assertEquals(GmRequests.Claim.NOT_TARGET, requests.claim(r.id(), B, 1).claim(), "B não responde por A");
        assertEquals(GmRequests.Claim.NOT_TARGET, requests.decline(r.id(), B, 1).claim(), "nem recusa por A");
        assertEquals(GmRequests.Claim.NOT_TARGET, requests.claim(r.id(), GM, 1).claim(), "nem o mestre que pediu");
        assertEquals(GmRequests.State.PENDING, r.target(A).state());
        assertEquals(GmRequests.Claim.NOT_FOUND, requests.claim("0000", A, 1).claim());
        assertEquals(GmRequests.Claim.NOT_FOUND, requests.decline(r.id(), A, 60_000).claim(), "vencido não se recusa");
    }

    @Test
    void privilegedOnlyWithModeOrAlternateAttribute() {
        assertTrue(STEALTH.privileged());
        assertFalse(new GmRequests.Spec("skill", "furtividade", "", 0, true, "normal", false, "").privileged());
        assertTrue(new GmRequests.Spec("skill", "atletismo", "des", 0, true, "normal", false, "").privileged());
        assertFalse(new GmRequests.Spec("attribute", "vig", "vig", 0, true, "normal", false, "").privileged(),
                "teste de atributo: o atributo é o próprio teste, não alternativo");
    }

    @Test
    void onlyTheAskingGmCancelsAndWaitingListsWhoToClose() {
        GmRequests requests = new GmRequests(new Random(10));
        GmRequests.Request r = requests.create(GM, "Mestre", STEALTH, targets(A, B), 0, 60_000);
        requests.claim(r.id(), A, 1);
        assertNull(requests.cancel(r.id(), UUID.randomUUID(), 2), "outro mestre não cancela");
        assertNull(requests.cancel(r.id(), A, 2), "nem o alvo");
        assertEquals(1, requests.size());

        GmRequests.Request cancelled = requests.cancel(r.id(), GM, 3);
        assertNotNull(cancelled);
        assertEquals(0, requests.size(), "cancelado sai da memória na hora");
        assertEquals(1, cancelled.waiting().size(), "só B ainda tem o cartão no HUD");
        assertEquals(B, cancelled.waiting().get(0).id());
        assertEquals(GmRequests.Claim.NOT_FOUND, requests.claim(r.id(), B, 4).claim(), "B não responde pedido cancelado");
        assertNull(requests.cancel(r.id(), GM, 5), "cancelar de novo não acha nada");
    }

    @Test
    void finalResultIsNotOverwritten() {
        GmRequests requests = new GmRequests(new Random(11));
        GmRequests.Request r = requests.create(GM, "Mestre", STEALTH, targets(A, B), 0, 60_000);
        requests.claim(r.id(), A, 1);
        requests.finish(r.id(), A, GmRequests.State.DONE, "17");
        requests.finish(r.id(), A, GmRequests.State.FAILED, "atrasada");
        assertEquals(GmRequests.State.DONE, r.target(A).state(), "resposta repetida ou atrasada não troca o resultado");
        assertEquals("17", r.target(A).summary());

        requests.decline(r.id(), B, 2);
        requests.finish(r.id(), B, GmRequests.State.DONE, "99");
        assertEquals(GmRequests.State.DECLINED, r.target(B).state(), "quem recusou não vira rolagem");
        requests.release(r.id(), B);
        assertEquals(GmRequests.State.DECLINED, r.target(B).state());
    }

    @Test
    void createReportsWhatItDropped() {
        GmRequests requests = new GmRequests(new Random(12));
        GmRequests.Request old = requests.create(GM, "Mestre", STEALTH, targets(A), 0, 1_000);
        List<GmRequests.Request> dropped = new ArrayList<>();
        requests.create(UUID.randomUUID(), "Outro", STEALTH, targets(B), 5_000, 60_000, dropped::add);
        assertEquals(1, dropped.size(), "o vencido sai e chega a quem criou (para a mesa marcar \"venceu\")");
        assertEquals(old.id(), dropped.get(0).id());
        assertEquals(1, dropped.get(0).waiting().size());

        dropped.clear();
        GmRequests.Request first = requests.create(GM, "Mestre", STEALTH, targets(A), 6_000, 60_000);
        for (int i = 1; i < GmRequests.MAX_PER_GM; i++) requests.create(GM, "Mestre", STEALTH, targets(A), 6_000 + i, 60_000);
        assertTrue(dropped.isEmpty());
        requests.create(GM, "Mestre", STEALTH, targets(A), 7_000, 60_000, dropped::add);
        assertEquals(List.of(first.id()), dropped.stream().map(GmRequests.Request::id).toList(), "o mais antigo do mestre sai e é avisado");
        assertEquals(GmRequests.MAX_PER_GM, requests.openBy(GM, 7_000).size());
    }

    @Test
    void specNormalizesWhatArrivedCrooked() {
        GmRequests.Spec spec = new GmRequests.Spec("skill", "furtividade", null, 150, false, "turbo", false, null);
        assertEquals(0, spec.dc(), "CD fora de 1..99 vira sem CD");
        assertTrue(spec.showDc(), "sem CD não há o que esconder");
        assertEquals("normal", spec.mode());
        assertFalse(spec.privileged(), "modo desconhecido não pede staff do site");
        assertEquals("", spec.attribute());
        assertEquals("", spec.label());
        assertEquals(0, new GmRequests.Spec("attribute", "vig", "", -3, true, "normal", false, "").dc());
        GmRequests.Spec hidden = new GmRequests.Spec("skill", "furtividade", "", 12, false, "desvantagem", true, "");
        assertEquals(12, hidden.dc());
        assertFalse(hidden.showDc());
        assertTrue(hidden.privileged());
    }

    @Test
    void diceSpecIsCanonicalAndNeverPrivileged() {
        GmRequests.Spec dice = GmRequests.Spec.dice(" 2D6 + 3 ", 8, false, true, "seguir o rastro");
        assertTrue(dice.free());
        assertTrue(dice.valid());
        assertEquals("2d6+3", dice.formula(), "a forma canônica do motor é a que viaja");
        assertEquals("2d6+3", dice.test());
        assertEquals("2d6+3", dice.key(), "a fórmula vai ao /rpg/roll no lugar da perícia");
        assertEquals(8, dice.dc(), "nos dados a CD compara o total");
        assertFalse(dice.showDc());
        assertTrue(dice.secret());

        // O que chegou torto pelo pacote: vantagem e perícia saem (o site recusa vantagem em rolagem livre).
        GmRequests.Spec crooked = new GmRequests.Spec("free", "furtividade", "des", 150, true, "vantagem", false, null, "d20");
        assertEquals("1d20", crooked.formula());
        assertEquals("", crooked.skill());
        assertEquals("", crooked.attribute());
        assertEquals("normal", crooked.mode());
        assertFalse(crooked.privileged(), "dados nunca pedem staff do site");
        assertEquals(0, crooked.dc());

        GmRequests.Spec bad = GmRequests.Spec.dice("2d6*3", 0, true, false, "");
        assertEquals("", bad.formula(), "fórmula que o motor recusa vira vazia");
        assertFalse(bad.valid());
        assertEquals("", new GmRequests.Spec("skill", "furtividade", "", 0, true, "normal", false, "", "2d6").formula(),
                "fora dos dados a fórmula não existe");
        assertEquals("Furtividade", STEALTH.test());
        assertEquals("furtividade", STEALTH.key());
    }

    @Test
    void diceRequestFlowsLikeATest() {
        GmRequests requests = new GmRequests(new Random(15));
        GmRequests.Request r = requests.create(GM, "Mestre", GmRequests.Spec.dice("1d100", 0, true, false, ""), targets(A), 0, 60_000);
        assertEquals("1d100", r.spec().test());
        assertEquals(GmRequests.Claim.OK, requests.claim(r.id(), A, 1).claim());
        requests.finish(r.id(), A, GmRequests.State.DONE, "57");
        assertTrue(r.settled());
    }

    @Test
    void secondsLeftRoundsUpAndStopsAtZero() {
        GmRequests requests = new GmRequests(new Random(13));
        GmRequests.Request r = requests.create(GM, "Mestre", STEALTH, targets(A), 0, 180_000);
        assertEquals(180, r.secondsLeft(0));
        assertEquals(1, r.secondsLeft(179_001));
        assertEquals(0, r.secondsLeft(180_000));
        assertEquals(0, r.secondsLeft(999_999));
    }

    @Test
    void nullTargetsAndNamesAreSkippedOrEmptied() {
        GmRequests requests = new GmRequests(new Random(14));
        Map<UUID, String> odd = new LinkedHashMap<>();
        odd.put(A, null);
        odd.put(null, "fantasma");
        GmRequests.Request r = requests.create(GM, null, STEALTH, odd, 0, 60_000);
        assertEquals(1, r.targets().size());
        assertEquals("", r.target(A).name());
        assertEquals("", r.gmName());
    }
}
