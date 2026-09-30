package com.aurorion.servicos.data;

import com.aurorion.profissoes.data.Profession;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Quem pode o que com um pedido, e o que o tempo faz com pedidos e vagas. */
class ServicosRulesTest {
    private static final UUID CLIENTE = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final UUID MEDICO = UUID.fromString("00000000-0000-0000-0000-00000000000d");
    private static final UUID OUTRO = UUID.fromString("00000000-0000-0000-0000-00000000000e");
    private static final long T0 = 1_000_000_000L;

    private static Pedido aberto() {
        return new Pedido(1, CLIENTE, "medico", "Estou ferido na praça", null, 0, PedidoStatus.ABERTO, null, T0, T0);
    }

    private static Pedido direto() {
        return new Pedido(2, CLIENTE, "medico", "Consulta", MEDICO, 7, PedidoStatus.ABERTO, null, T0, T0);
    }

    @Test
    void cleanStripsColorCodesControlCharsAndExtraSpaces() {
        assertEquals("Olá mundo", ServicosRules.clean("  §cOlá \n\t mundo  ", 40));
        assertEquals("abc", ServicosRules.clean("abcdef", 3));
        assertEquals("", ServicosRules.clean(null, 10));
    }

    @Test
    void openOrderGoesToAnyoneOfTheAreaButNotTheClient() {
        assertTrue(ServicosRules.canAccept(aberto(), MEDICO, true));
        assertFalse(ServicosRules.canAccept(aberto(), OUTRO, false), "sem anuncio na area, nao pega");
        assertFalse(ServicosRules.canAccept(aberto(), CLIENTE, true), "quem pediu nao aceita o proprio pedido");
    }

    @Test
    void directOrderIsOnlyForTheChosenProfessional() {
        assertTrue(ServicosRules.canAccept(direto(), MEDICO, true));
        assertFalse(ServicosRules.canAccept(direto(), OUTRO, true));
        assertTrue(ServicosRules.canRefuse(direto(), MEDICO));
        assertFalse(ServicosRules.canRefuse(aberto(), MEDICO), "pedido aberto nao se recusa: so nao se aceita");
    }

    /** O primeiro que aceita leva: depois de aceito, ninguem mais aceita. */
    @Test
    void anAcceptedOrderCannotBeTakenAgain() {
        Pedido aceito = aberto().with(PedidoStatus.ACEITO, MEDICO, T0 + 10);
        assertFalse(ServicosRules.canAccept(aceito, OUTRO, true));
        assertTrue(ServicosRules.canComplete(aceito, CLIENTE));
        assertTrue(ServicosRules.canComplete(aceito, MEDICO));
        assertFalse(ServicosRules.canComplete(aceito, OUTRO));
    }

    @Test
    void cancelling() {
        assertTrue(ServicosRules.canCancel(aberto(), CLIENTE));
        assertFalse(ServicosRules.canCancel(aberto(), MEDICO));
        Pedido aceito = aberto().with(PedidoStatus.ACEITO, MEDICO, T0);
        assertTrue(ServicosRules.canCancel(aceito, MEDICO), "o profissional pode desistir");
        assertFalse(ServicosRules.canCancel(aceito.with(PedidoStatus.CONCLUIDO, MEDICO, T0), CLIENTE));
    }

    @Test
    void timePassing() {
        Pedido pedido = aberto();
        assertSame(pedido, ServicosRules.age(pedido, T0 + ServicosRules.OPEN_ORDER_TTL - 1));
        assertEquals(PedidoStatus.EXPIRADO, ServicosRules.age(pedido, T0 + ServicosRules.OPEN_ORDER_TTL).status());

        Pedido aceito = pedido.with(PedidoStatus.ACEITO, MEDICO, T0);
        assertEquals(PedidoStatus.CONCLUIDO, ServicosRules.age(aceito, T0 + ServicosRules.ACCEPTED_TTL).status());

        Pedido cancelado = pedido.with(PedidoStatus.CANCELADO, null, T0);
        assertFalse(ServicosRules.purge(cancelado, T0 + ServicosRules.FINISHED_TTL - 1));
        assertTrue(ServicosRules.purge(cancelado, T0 + ServicosRules.FINISHED_TTL));
        assertFalse(ServicosRules.purge(aceito, T0 + 10 * ServicosRules.FINISHED_TTL), "em andamento nao some");
    }

    @Test
    void counterpartIsTheOtherSide() {
        assertEquals(MEDICO, direto().counterpart(CLIENTE));
        assertNull(aberto().counterpart(CLIENTE), "aberto e sem ninguem ainda: ninguem do outro lado");
        assertEquals(CLIENTE, direto().counterpart(MEDICO));
    }

    @Test
    void applyingTwiceKeepsOneCandidacy() {
        Vaga vaga = new Vaga(3, CLIENTE, "cozinheiro", "Cozinheiro na taverna", "", "20/dia", T0, List.of());
        Vaga uma = vaga.withCandidate(MEDICO);
        assertSame(uma, uma.withCandidate(MEDICO));
        assertEquals(1, uma.candidates().size());
        assertTrue(uma.withoutCandidate(MEDICO).candidates().isEmpty());
    }

    @Test
    void everyProfessionIsACategoryPlusOthers() {
        for (Profession profession : Profession.values()) {
            assertEquals(profession != Profession.NONE, Categorias.exists(profession.id()), profession.id());
        }
        assertTrue(Categorias.exists(Categorias.OUTROS));
        assertNull(Categorias.profession(Categorias.OUTROS));
        assertEquals(Profession.DOCTOR, Categorias.profession("medico"));
    }
}
