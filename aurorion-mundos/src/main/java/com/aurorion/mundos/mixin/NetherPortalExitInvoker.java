package com.aurorion.mundos.mixin;

import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Abre {@code NetherPortalBlock#getDimensionTransitionFromExit}, que e {@code private static}.
 *
 * <p>E o ultimo passo de toda travessia: pega o retangulo do portal de chegada e devolve a posicao,
 * a rotacao e a velocidade com que a entidade sai do outro lado, respeitando o eixo do portal e o
 * tamanho da entidade.
 *
 * <p>Reaproveitar em vez de reescrever nao e economia de linhas — e o que garante que sair por um
 * portal deste mod <b>e</b> sair por um portal, com a mesma fisica. Uma copia divergiria na primeira
 * mudanca de versao, e a divergencia apareceria como jogador nascendo dentro da obsidiana.
 */
@Mixin(NetherPortalBlock.class)
public interface NetherPortalExitInvoker {

    @Invoker("getDimensionTransitionFromExit")
    static DimensionTransition aurorion_mundos$fromExit(
            Entity entity,
            BlockPos pos,
            BlockUtil.FoundRectangle rectangle,
            ServerLevel level,
            DimensionTransition.PostDimensionTransition postDimensionTransition) {
        throw new AssertionError("Substituido pelo Mixin");
    }
}
