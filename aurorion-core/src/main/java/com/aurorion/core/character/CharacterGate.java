package com.aurorion.core.character;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Existe um criador de personagens neste servidor?
 *
 * <p>O core sabe <b>o que</b> e um personagem; quem decide <b>quando</b> cobrar um e o mod que
 * instala a tela de criacao. Enquanto ninguem instalar, este portao nao barra ninguem por falta de
 * nome — os outros mods do ecossistema continuam funcionando sozinhos, como sempre.
 *
 * <p>A politica vem de fora de proposito: ela depende de config de servidor que o core nao le. Sem
 * isso, ou o core teria que conhecer a config do criador, ou a mesma decisao ficaria escrita nos dois.
 */
public final class CharacterGate {
    /** Raiz de comando do criador. Regras terminais em outros mods precisam deixar ela passar. */
    public static final String COMMAND = "personagem";

    private static Predicate<ServerPlayer> onboarding;

    /**
     * Quem esta no meio de outra cena e nao pode ser interrompido por uma pergunta de nome.
     *
     * <p>Sao poucas e registradas na carga do mod; a lista e percorrida por indice para nao alocar
     * iterador em caminho chamado a cada login.
     */
    private static final List<Predicate<ServerPlayer>> BUSY = new CopyOnWriteArrayList<>();

    private CharacterGate() {
    }

    /** @param policy quem ainda deve um personagem; consultada na thread do servidor. */
    public static void enableCreation(Predicate<ServerPlayer> policy) {
        onboarding = policy;
    }

    public static boolean creationEnabled() {
        return onboarding != null;
    }

    /** True enquanto esta conta deve um personagem: nada de jogar ate ela responder. */
    public static boolean blocks(ServerPlayer player) {
        if (CharacterData.get(player.server).isDead(player.getUUID())) return true;
        return onboarding != null && onboarding.test(player);
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
        return onboarding != null && COMMAND.equals(rootLiteral);
    }
}
