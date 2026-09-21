package com.aurorion.essentials.privacy;

/**
 * Quem ve um aviso automatico do jogo (entrada/saida, conquista, morte).
 *
 * <p>Tres estados, e nao um booleano, porque as tres mensagens querem coisas diferentes e a
 * diferenca importa: entrada/saida e conquista somem para todo mundo (sao meta-gaming e ruido), mas
 * a morte precisa continuar chegando <b>a quem modera</b> — um servidor com sistema de vidas nao
 * pode perder o registro de quem morreu so porque o chat ficou limpo.
 *
 * <p>Nomes em ingles como no resto dos {@code enum} do ecossistema (ver {@code Decision} no
 * {@code aurorion-areas}); o texto em portugues fica no comentario da config, que e o que quem
 * administra le.
 */
public enum Visibility {
    /** Broadcast normal do vanilla: todo mundo no servidor ve. */
    EVERYONE,
    /** So quem tem OP (nivel 2+). O jogador envolvido nao recebe copia por ser o envolvido. */
    ADMINS,
    /** Ninguem ve no chat. */
    NOBODY
}
