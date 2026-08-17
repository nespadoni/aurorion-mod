package com.aurorion.core;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Biblioteca compartilhada do ecossistema Aurorion. <b>Nao faz nada sozinha.</b>
 *
 * <p>Nao registra item, bloco, comando ou evento; nao tem config e nao toca no jogo. Existe porque
 * varios mods do monorepo estavam repetindo o mesmo codigo — no caso mais grave, o <em>mesmo
 * algoritmo</em> de "achar um lugar seguro para colocar o jogador" escrito duas vezes, em
 * {@code aurorion-portais} e {@code aurorion-vidas}, com um bug em potencial para consertar em dois
 * lugares.
 *
 * <p>O que entra aqui tem um criterio: <b>ja estava duplicado</b>. Utilidade que so um mod usa fica
 * no mod, para esta biblioteca nao virar um deposito de codigo especulativo.
 *
 * <p>Consequencia para o servidor: este jar precisa estar sempre no pack. E a contrapartida
 * assumida da SDD §3 — os mods continuam podendo ser ligados e desligados um a um, mas o core nao.
 */
@Mod(AurorionCore.MOD_ID)
public class AurorionCore {
    public static final String MOD_ID = "aurorion_core";
    public static final String MOD_NAME = "Aurorion Core";

    public static final Logger LOGGER = LogUtils.getLogger();

    public AurorionCore(IEventBus modEventBus, ModContainer container) {
    }
}
