package com.aurorion.profissoes.craft.compat;

import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Qual máquina do Create está trabalhando agora. O {@code rollResults} do Create não recebe o bloco, então
 * o {@code tick} de cada {@code SmartBlockEntity} do servidor se marca aqui ao começar, e o fim dos ticks
 * de bloco do mundo limpa. Prensa, moedor, serra, ventilador, bacia, crafter e montagem sequencial rodam
 * dentro do tick da própria máquina; o que roda fora (aplicação manual com a mão, contraption em movimento)
 * encontra {@code null} e cai no bônus de máquina.
 *
 * <p>Custo: uma escrita de campo por tick de bloco do Create e uma por tick de mundo. Sem alocação. Só a
 * thread do servidor escreve (o cliente também tica blocos, e é ignorado).
 */
public final class MachineContext {
    @Nullable
    private static BlockEntity current;

    private MachineContext() {
    }

    public static void enter(BlockEntity block) {
        current = block;
    }

    public static void clear() {
        current = null;
    }

    @Nullable
    public static BlockEntity current() {
        return current;
    }
}
