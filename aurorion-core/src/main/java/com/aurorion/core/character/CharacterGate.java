package com.aurorion.core.character;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Existe um criador de personagens neste servidor?
 *
 * <p>O core sabe <b>o que</b> e um personagem; quem decide <b>quando</b> cobrar um e o mod que
 * instala a tela de criacao — e a politica dele depende de config que o core nao le. Por isso aqui
 * nao mora regra nenhuma: mora so o que os outros mods precisam perguntar para nao atrapalhar.
 *
 * <p>Sem criador instalado, tudo aqui responde "nao" e o ecossistema segue como sempre.
 */
public final class CharacterGate {
    /** Raiz de comando do criador. Regras terminais em outros mods precisam deixar ela passar. */
    public static final String COMMAND = "personagem";

    private static boolean creationEnabled;

    /**
     * Quem esta no meio de outra cena e nao pode ser interrompido por uma pergunta de nome.
     *
     * <p>Sao poucas e registradas na carga do mod; a lista e percorrida por indice para nao alocar
     * iterador num caminho chamado a cada login e a cada segundo.
     */
    private static final List<Predicate<ServerPlayer>> BUSY = new CopyOnWriteArrayList<>();

    private CharacterGate() {
    }

    public static void enableCreation() {
        creationEnabled = true;
    }

    public static boolean creationEnabled() {
        return creationEnabled;
    }

    /**
     * Adia a criacao enquanto outra coisa acontece com a mesma conta.
     *
     * <p>E o que impede a tela de nome de aparecer por cima do epilogo de quem acabou de morrer: o
     * {@code aurorion_limbo} avisa que aquela pessoa esta assistindo, sem que o criador precise
     * conhecer o Limbo nem o Limbo precise conhecer o criador.
     */
    public static void deferWhile(Predicate<ServerPlayer> busy) {
        BUSY.add(busy);
    }

    public static boolean deferred(ServerPlayer player) {
        for (int i = 0; i < BUSY.size(); i++) {
            if (BUSY.get(i).test(player)) return true;
        }
        return false;
    }

    /** Um morto ainda pode falar com o criador; todo o resto continua recusado. */
    public static boolean allowsCommand(String rootLiteral) {
        return creationEnabled && COMMAND.equals(rootLiteral);
    }
}
