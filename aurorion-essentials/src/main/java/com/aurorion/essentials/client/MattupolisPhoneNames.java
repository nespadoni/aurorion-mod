package com.aurorion.essentials.client;

import com.aurorion.essentials.fakename.FakeName;
import com.aurorion.essentials.fakename.FakeNameRegistry;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Troca o nick pelo nome do personagem no instante do desenho, e so ali.
 *
 * <p><b>Exibicao apenas.</b> O telefone roteia ligacao, mensagem e PIX por nome, resolvido no
 * servidor com {@code PlayerList#getPlayerByName} — ou seja, pelo nick da conta Mojang. O campo que
 * a tela guarda e devolve ao servidor continua sendo o nick real; muda o que aparece. O motivo
 * completo esta em {@code MattupolisPhoneCompat}.</p>
 *
 * <p>Isto roda no caminho de render: todo texto de toda tela do telefone, todo frame. Por isso o
 * indice de nick e montado no maximo uma vez por segundo em vez de varrer a lista de jogadores a
 * cada chamada — com 80 pessoas online e uma agenda aberta, a varredura por chamada era mais de mil
 * comparacoes de string por frame (ver SDD.md §5).</p>
 */
public final class MattupolisPhoneNames {
    /**
     * Nick para conta. Ordenado sem diferenciar maiuscula de minuscula: a busca sai sem alocar a
     * copia em minusculas que um {@code HashMap} exigiria a cada texto desenhado.
     */
    private static final Map<String, UUID> BY_NICK = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    /** Um segundo de atraso para um nome recem-trocado e invisivel numa tela de celular. */
    private static final long REFRESH_INTERVAL_MS = 1000L;

    private static ClientPacketListener connection;
    private static long refreshedAt = Long.MIN_VALUE;

    private MattupolisPhoneNames() {}

    public static String display(String text) {
        if (text == null || text.isBlank()) return text;

        ClientPacketListener current = Minecraft.getInstance().getConnection();
        if (current == null) return text;
        refreshIndex(current);

        UUID account = BY_NICK.get(text);
        FakeName fake = account == null ? null : FakeNameRegistry.get(account);
        return fake == null ? text : fake.plain();
    }

    /** Igual ao {@link #display(String)}, cortado na largura do cabecalho de chamada. */
    public static String callLabel(String name) {
        String display = display(name);
        if (display == null) return name;

        var font = Minecraft.getInstance().font;
        return font.width(display) <= 144 ? display : font.plainSubstrByWidth(display, 132) + "...";
    }

    private static void refreshIndex(ClientPacketListener current) {
        if (connection != current) {
            BY_NICK.clear();
            connection = current;
            refreshedAt = Long.MIN_VALUE;
        }

        long now = Util.getMillis();
        if (now - refreshedAt < REFRESH_INTERVAL_MS) return;
        refreshedAt = now;

        // Acrescenta sem limpar: a agenda do telefone continua listando quem saiu no meio da sessao,
        // e esse contato tem que seguir aparecendo com o nome do personagem. O mapa so zera quando a
        // conexao troca, que e quando os UUIDs deixam de valer.
        for (var info : current.getOnlinePlayers()) {
            var profile = info.getProfile();
            BY_NICK.put(profile.getName(), profile.getId());
        }
    }
}
