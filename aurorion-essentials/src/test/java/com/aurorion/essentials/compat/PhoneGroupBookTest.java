package com.aurorion.essentials.compat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhoneGroupBookTest {
    private final AtomicInteger changes = new AtomicInteger();
    private final PhoneGroupBook book = new PhoneGroupBook(changes::incrementAndGet);
    private final UUID ana = id(1);
    private final UUID bia = id(2);
    private final UUID caio = id(3);

    private static UUID id(int n) {
        return new UUID(0L, n);
    }

    private PhoneGroupBook.Group criar(UUID dono, String nome, UUID... convidados) {
        PhoneGroupBook.Created created = book.create(UUID.randomUUID(), dono, nome, List.of(convidados), 1_000L);
        assertEquals(PhoneGroupBook.Result.OK, created.result());
        assertNotNull(created.group());
        return created.group();
    }

    @Test
    void donoEhOPrimeiroMembroEOGrupoApareceParaTodos() {
        PhoneGroupBook.Group grupo = criar(ana, "  Turma   da  praça ", bia, caio, ana);
        assertEquals("Turma da praça", grupo.name());
        assertEquals(ana, grupo.owner());
        assertEquals(List.of(ana, bia, caio), List.copyOf(grupo.members()));
        assertEquals(List.of(grupo), book.groupsOf(caio));
        assertTrue(changes.get() > 0);
    }

    @Test
    void nomeComCorOuVazioOuLongoEhRecusado() {
        assertNull(PhoneGroupBook.cleanName("   "));
        assertNull(PhoneGroupBook.cleanName("x".repeat(PhoneGroupBook.MAX_NAME + 1)));
        assertEquals("Vermelho", PhoneGroupBook.cleanName("§cVermelho"));
        assertEquals(PhoneGroupBook.Result.INVALID_NAME,
                book.create(UUID.randomUUID(), ana, "\n\t", List.of(bia), 0L).result());
        assertEquals(0, book.size());
    }

    @Test
    void textoTemLimiteETiraControle() {
        assertEquals("oi tudo bem", PhoneGroupBook.cleanText(" oi\ntudo\u0000 bem "));
        assertNull(PhoneGroupBook.cleanText("a".repeat(PhoneGroupBook.MAX_TEXT + 1)));
        assertEquals(PhoneGroupBook.MAX_TEXT, PhoneGroupBook.cleanText("a".repeat(PhoneGroupBook.MAX_TEXT)).length());
    }

    @Test
    void grupoNaoPassaDeDezesseisMembros() {
        List<UUID> convidados = new ArrayList<>();
        for (int i = 10; i < 10 + PhoneGroupBook.MAX_MEMBERS + 4; i++) convidados.add(id(i));
        PhoneGroupBook.Created created = book.create(UUID.randomUUID(), ana, "Grande", convidados, 0L);
        assertEquals(PhoneGroupBook.MAX_MEMBERS, created.group().members().size());
        assertEquals(5, created.skipped().size());
        assertEquals(PhoneGroupBook.Result.GROUP_FULL, book.add(created.group().id(), ana, id(999)));
    }

    @Test
    void contaNaoEntraEmGruposDemais() {
        for (int i = 0; i < PhoneGroupBook.MAX_GROUPS_PER_MEMBER; i++) criar(ana, "G" + i);
        assertEquals(PhoneGroupBook.Result.TOO_MANY_GROUPS,
                book.create(UUID.randomUUID(), ana, "Mais um", List.of(), 0L).result());

        PhoneGroupBook.Group daBia = criar(bia, "Da Bia");
        assertEquals(PhoneGroupBook.Result.TARGET_TOO_MANY_GROUPS, book.add(daBia.id(), bia, ana));
        PhoneGroupBook.Created comAna = book.create(UUID.randomUUID(), caio, "Com Ana", List.of(ana, bia), 0L);
        assertEquals(List.of(ana), comAna.skipped());
        assertFalse(comAna.group().has(ana));
    }

    @Test
    void soODonoAdicionaERemove() {
        PhoneGroupBook.Group grupo = criar(ana, "Turma", bia);
        assertEquals(PhoneGroupBook.Result.NOT_OWNER, book.add(grupo.id(), bia, caio));
        assertEquals(PhoneGroupBook.Result.NOT_MEMBER, book.add(grupo.id(), caio, caio));
        assertEquals(PhoneGroupBook.Result.OK, book.add(grupo.id(), ana, caio));
        assertEquals(PhoneGroupBook.Result.ALREADY_MEMBER, book.add(grupo.id(), ana, caio));
        assertEquals(PhoneGroupBook.Result.NOT_OWNER, book.remove(grupo.id(), bia, caio));
        assertEquals(PhoneGroupBook.Result.NOT_OWNER, book.remove(grupo.id(), ana, ana));
        assertEquals(PhoneGroupBook.Result.OK, book.remove(grupo.id(), ana, caio));
        assertTrue(book.groupsOf(caio).isEmpty());
    }

    @Test
    void donoQueSaiPassaOGrupoEUltimoQueSaiAcabaComEle() {
        PhoneGroupBook.Group grupo = criar(ana, "Turma", bia, caio);
        assertEquals(PhoneGroupBook.Result.OK, book.leave(grupo.id(), ana));
        assertEquals(bia, grupo.owner());
        assertEquals(PhoneGroupBook.Result.NOT_MEMBER, book.leave(grupo.id(), ana));
        book.leave(grupo.id(), bia);
        book.leave(grupo.id(), caio);
        assertNull(book.get(grupo.id()));
        assertEquals(PhoneGroupBook.Result.NOT_FOUND, book.leave(grupo.id(), caio));
    }

    @Test
    void resetTiraAContaDeTudoEEhIdempotente() {
        PhoneGroupBook.Group comBia = criar(ana, "A", bia);
        PhoneGroupBook.Group soBia = criar(bia, "B");
        book.enqueue(bia, linha(comBia.id(), 5_000L));

        List<PhoneGroupBook.Group> mudaram = book.removeAccount(bia);
        assertEquals(List.of(comBia), mudaram);
        assertNull(book.get(soBia.id()));
        assertTrue(book.groupsOf(bia).isEmpty());
        assertTrue(book.drain(bia, 5_000L).isEmpty());
        assertTrue(book.removeAccount(bia).isEmpty());
    }

    @Test
    void filaDescartaAMaisAntigaEAVencida() {
        UUID grupo = criar(ana, "Fila", bia).id();
        for (int i = 0; i < PhoneGroupBook.QUEUE_PER_MEMBER + 3; i++) book.enqueue(bia, linha(grupo, i));
        List<PhoneGroupBook.Line> entregue = book.drain(bia, PhoneGroupBook.QUEUE_TTL_MS);
        assertEquals(PhoneGroupBook.QUEUE_PER_MEMBER, entregue.size());
        assertEquals(3L, entregue.getFirst().at());
        assertTrue(book.drain(bia, 0L).isEmpty());

        book.enqueue(caio, linha(grupo, 0L));
        book.enqueue(caio, linha(grupo, PhoneGroupBook.QUEUE_TTL_MS));
        assertEquals(1, book.drain(caio, PhoneGroupBook.QUEUE_TTL_MS + 1).size());

        book.enqueue(caio, linha(grupo, 0L));
        book.purgeExpired(PhoneGroupBook.QUEUE_TTL_MS + 1);
        assertTrue(book.queues().isEmpty());
    }

    @Test
    void cargaNaoDisparaMudancaENemPassaDosLimites() {
        PhoneGroupBook carregado = new PhoneGroupBook(() -> {
            throw new AssertionError("restaurar nao suja o arquivo");
        });
        List<UUID> muitos = new ArrayList<>();
        for (int i = 0; i < PhoneGroupBook.MAX_MEMBERS + 5; i++) muitos.add(id(100 + i));
        UUID grupo = UUID.randomUUID();
        carregado.restore(grupo, "Salvo", 0L, muitos);
        carregado.restore(UUID.randomUUID(), "", 0L, muitos);
        assertEquals(1, carregado.size());
        assertEquals(PhoneGroupBook.MAX_MEMBERS, carregado.get(grupo).members().size());
        assertEquals(id(100), carregado.get(grupo).owner());
    }

    private static PhoneGroupBook.Line linha(UUID grupo, long at) {
        return new PhoneGroupBook.Line(grupo, PhoneGroupBook.TEXT, "nick", "Nome", "oi", at);
    }
}
