package com.aurorion.diario.client;

import com.aurorion.diario.network.DiaryPayloads;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.LinkedHashMap;
import java.util.Map;

/** Transporte simulado do diário: dados só em memória, durante uma abertura de /diario teste. */
final class DiaryTestSession {
    private final Map<Long, DiaryPayloads.Entry> entries = new LinkedHashMap<>();
    private long nextId = 1;

    DiaryTestSession() {
        add("Uma noite em Aurorion", "12 de Lua Alta", """
                ## Uma noite em Aurorion

                As estrelas iluminaram a estrada enquanto eu voltava para casa.
                Este é um **texto em negrito** e este está em *itálico*.

                > Nem toda luz vem do céu.

                ### Lembranças da viagem
                - Visitar a biblioteca
                - Encontrar os antigos companheiros
                - Investigar a torre

                ---

                Use a barra de ferramentas, altere o título e a data e abra a prévia.
                Os dados deste teste ficam apenas em memória.
                """, 0);
        add("Carta publicada", "13 de Lua Alta", "## Uma carta\n\nUma entrada com publicação simulada.", DiaryPayloads.PUBLISHED);
        add("Um título comprido para conferir o espaço da lista", "14 de Lua Alta", "Uma anotação curta.", DiaryPayloads.PUBLISHED | DiaryPayloads.CHANGES);
        // Entradas suficientes para conferir também a paginação em escalas diferentes.
        for (int i = 4; i <= 14; i++) {
            add("Anotação de viagem " + i, i + " de Lua Alta", "## Dia " + i + "\n\nMais uma página do diário de teste.", 0);
        }
    }

    DiaryPayloads.Open openPayload() {
        return new DiaryPayloads.Open("Personagem de teste", entries.values().stream()
                .map(entry -> new DiaryPayloads.Summary(entry.id(), entry.title(), entry.flags())).toList(), "");
    }

    void handle(DiaryScreen screen, CustomPacketPayload payload) {
        if (payload instanceof DiaryPayloads.Request request) {
            if ("abrir".equals(request.action())) {
                DiaryPayloads.Entry entry = entries.get(request.entryId());
                if (entry != null) screen.load(entry);
            } else if ("lista".equals(request.action())) {
                screen.refresh(openPayload());
            }
        } else if (payload instanceof DiaryPayloads.Save save) {
            long id = save.entryId() > 0 ? save.entryId() : nextId++;
            DiaryPayloads.Entry previous = entries.get(id);
            int flags = previous == null ? 0 : previous.flags();
            if ((flags & DiaryPayloads.PUBLISHED) != 0) flags |= DiaryPayloads.CHANGES;
            int version = previous == null ? 1 : previous.version() + 1;
            entries.put(id, new DiaryPayloads.Entry(id, version, save.title(), save.loreDate(), save.markup(), flags, ""));
            screen.onStatus(new DiaryPayloads.Status(save.draftKey(), "site", id, version, flags, ""));
        } else if (payload instanceof DiaryPayloads.Publish publish) {
            DiaryPayloads.Entry entry = entries.get(publish.entryId());
            if (entry == null) return;
            int flags = publish.publish() ? (entry.flags() | DiaryPayloads.PUBLISHED) & ~DiaryPayloads.CHANGES
                    : entry.flags() & ~(DiaryPayloads.PUBLISHED | DiaryPayloads.CHANGES);
            int version = entry.version() + 1;
            entries.put(entry.id(), new DiaryPayloads.Entry(entry.id(), version, entry.title(), entry.loreDate(), entry.markup(), flags, ""));
            screen.onStatus(new DiaryPayloads.Status("", publish.publish() ? "publicado" : "retirado",
                    entry.id(), version, flags, publish.publish()
                    ? "TESTE LOCAL — publicação simulada." : "TESTE LOCAL — publicação retirada."));
        }
    }

    private void add(String title, String date, String markup, int flags) {
        long id = nextId++;
        entries.put(id, new DiaryPayloads.Entry(id, 1, title, date, markup, flags, ""));
    }
}
