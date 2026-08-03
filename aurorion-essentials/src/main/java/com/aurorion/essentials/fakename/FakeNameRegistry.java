package com.aurorion.essentials.fakename;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache em memoria de "quem tem nome falso agora", comum a cliente e servidor.
 *
 * <p>O servidor escreve aqui a partir do {@code FakeNameManager} (fonte de verdade, respaldada
 * por {@code FakeNameData} em disco); o cliente escreve a partir dos pacotes de sincronizacao.
 * O mixin em {@code Player#getName()} so le daqui — nao sabe de rede nem de disco, entao o mesmo
 * codigo de leitura vale para os dois lados.</p>
 *
 * <p>Usa {@link ConcurrentHashMap} porque em {@code runClient} (cliente + servidor integrado na
 * mesma JVM) a thread do servidor e a thread de render tocam este mapa ao mesmo tempo — em
 * producao (servidor dedicado) isso nunca acontece, mas o custo de ser thread-safe aqui e
 * irrelevante perto do resto do trabalho de cada chamada.</p>
 */
public final class FakeNameRegistry {
    private static final Map<UUID, FakeName> ACTIVE = new ConcurrentHashMap<>();

    private FakeNameRegistry() {
    }

    @Nullable
    public static FakeName get(UUID player) {
        return ACTIVE.get(player);
    }

    @Nullable
    public static Component getDisplayName(UUID player) {
        FakeName fakeName = ACTIVE.get(player);
        return fakeName == null ? null : fakeName.component();
    }

    public static void put(UUID player, FakeName fakeName) {
        ACTIVE.put(player, fakeName);
    }

    public static void remove(UUID player) {
        ACTIVE.remove(player);
    }

    public static void clear() {
        ACTIVE.clear();
    }

    public static Map<UUID, FakeName> all() {
        return Collections.unmodifiableMap(ACTIVE);
    }
}
