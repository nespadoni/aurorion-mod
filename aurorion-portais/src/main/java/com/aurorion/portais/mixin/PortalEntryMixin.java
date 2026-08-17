package com.aurorion.portais.mixin;

import com.aurorion.portais.runtime.TransitGate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Barra a travessia de portal <b>antes</b> de o servidor calcular o destino.
 *
 * <p>Este mixin e otimizacao, nao regra — a regra e o {@code EntityTravelToDimensionEvent}, que
 * continua sendo a palavra final. O motivo de ele existir esta em
 * {@code NetherPortalBlock#getPortalDestination}, que o vanilla chama antes da troca de dimensao:
 * ele varre o destino atras de um portal existente e, quando nao acha, <b>escava um portal novo</b>.
 * Cancelar so no evento significaria cavar um portal no Nether para cada jogador que encostasse num
 * portal fechado — trabalho pesado e lixo permanente no mundo, multiplicado pela populacao online.
 *
 * <p>O ponto de injecao e o mais raso possivel: {@code canUsePortal} e consultado por
 * {@code Entity#handlePortal} so quando a entidade ja esta dentro de um portal, e devolver
 * {@code false} faz o vanilla apenas deixar o contador do portal decair. Nada de estado inconsistente
 * — e o mesmo caminho que o vanilla usa para um jogador morto ou de carona (SDD §7.2: cancelar onde
 * o vanilla ja sabe lidar com a negativa).
 *
 * <p>Vale para jogadores no servidor e mais ninguem: mob, item e veiculo vazio continuam com o
 * comportamento original, porque o escopo deste mod e o transito de pessoas (SDD §2).
 */
@Mixin(Entity.class)
public abstract class PortalEntryMixin {

    @Inject(method = "canUsePortal", at = @At("HEAD"), cancellable = true)
    private void aurorion_portais$blockLockedPortal(boolean allowPassengers, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayer player && !TransitGate.mayEnterPortal(player)) {
            cir.setReturnValue(false);
        }
    }
}
