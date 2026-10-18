package com.aurorion.essentials.client;

/**
 * Marca uma tela do essentials como tela do celular. O telefone so poe o jogo em escala 3 (o tamanho
 * em que o celular foi desenhado) para telas que estendem a base dele; o {@code PhoneScreenScaleMixin}
 * estende a regra para quem tem esta marca. Sem ela, abrir o menu de opcoes ou a tela do grupo por
 * cima de uma tela do telefone voltava a escala do jogador e o celular mudava de tamanho.
 *
 * <p>O {@code aurorion-servicos} tem a propria marca e o proprio mixin: os mods nao dependem um do
 * outro.
 */
public interface EssentialsPhoneScreen {
}
