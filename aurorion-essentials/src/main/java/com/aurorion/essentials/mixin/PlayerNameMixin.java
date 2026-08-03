package com.aurorion.essentials.mixin;

import com.aurorion.essentials.fakename.FakeNameRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ponto unico de verdade do "nome exibido". Quase todo texto que menciona um jogador — chat,
 * mensagem de morte, broadcast de conquista, entrada/saida do servidor, feedback de comando —
 * e construido em cima de {@code Player#getName()} / {@code Player#getDisplayName()} (que por sua
 * vez chama {@code getName()}). Sobrescrever so aqui vale para o jogo inteiro, em vez de precisar
 * cacar e sobrescrever cada mensagem individualmente.
 *
 * <p>{@code getTabListDisplayName()} e um metodo separado (nao chama {@code getName()}) usado na
 * hora de montar o pacote da tab list, entao precisa do proprio override.</p>
 *
 * <p>O que <b>nao</b> passa por aqui, de proposito: {@code getScoreboardName()} (usado por
 * scoreboard e seletores de alvo como {@code @p}) continua devolvendo o nome real — sem isso,
 * comandos administrativos e integracoes de scoreboard quebrariam. Essa e exatamente a garantia
 * que sustenta o {@code /realname}: a identidade tecnica nunca muda, so a exibida.</p>
 */
@Mixin(Player.class)
public abstract class PlayerNameMixin {

    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private void aurorion_essentials$fakeGetName(CallbackInfoReturnable<Component> cir) {
        Component fakeName = FakeNameRegistry.getDisplayName(((Player) (Object) this).getUUID());
        if (fakeName != null) cir.setReturnValue(fakeName);
    }

    @Inject(method = "getTabListDisplayName", at = @At("HEAD"), cancellable = true)
    private void aurorion_essentials$fakeTabListName(CallbackInfoReturnable<Component> cir) {
        Component fakeName = FakeNameRegistry.getDisplayName(((Player) (Object) this).getUUID());
        if (fakeName != null) cir.setReturnValue(fakeName);
    }
}
