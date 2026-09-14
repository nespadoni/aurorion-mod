package com.aurorion.mundos.mixin;

import com.aurorion.mundos.portal.DimensionLink;
import com.aurorion.mundos.portal.LinkCatalog;
import com.aurorion.mundos.portal.PortalRouter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Deixa o destino de um portal ser declarado em datapack.
 *
 * <p>O vanilla decide isso com uma linha: {@code level.dimension() == NETHER ? OVERWORLD : NETHER}.
 * Com mais de um overworld, essa pergunta passa a ter mais de uma resposta possivel.
 *
 * <h2>Por que aqui, e nao num bloco de portal proprio</h2>
 *
 * <p>Um bloco novo exigiria registro de conteudo (que pela SDD §6.1 nem moraria neste mod), um
 * acendedor proprio, textura, e ensinar o jogador uma mecanica nova. Aqui a mecanica e a que todo
 * mundo ja sabe: obsidiana, isqueiro, atravessa.
 *
 * <h2>Sem ligacao declarada, nada muda</h2>
 *
 * <p>Este metodo e o mesmo por onde passa o portal do Nether de todo jogador do servidor. Por isso a
 * primeira coisa que ele faz e sair: sem ligacao para a dimensao de origem, o {@code return} devolve
 * o controle ao vanilla e o Nether continua funcionando exatamente como antes — inclusive para os
 * mods do modpack que dependem disso (SDD §2, nunca interferir fora do escopo proprio).
 *
 * <p>O custo desse caminho de saida e uma consulta a um {@code Map} por travessia de portal. Nao ha
 * caminho por tick aqui: o vanilla so chama isto quando uma entidade completou o tempo dentro do
 * portal.
 */
@Mixin(NetherPortalBlock.class)
public abstract class NetherPortalDestinationMixin {

    @Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
    private void aurorion_mundos$routeByDatapack(ServerLevel level, Entity entity, BlockPos pos,
                                                 CallbackInfoReturnable<DimensionTransition> cir) {
        DimensionLink link = LinkCatalog.route(level.dimension(), entity.getX(), entity.getZ());
        if (link == null) return;

        cir.setReturnValue(PortalRouter.destination(level, entity, pos, link));
    }
}
