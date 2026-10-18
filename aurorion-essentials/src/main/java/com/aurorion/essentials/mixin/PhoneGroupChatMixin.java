package com.aurorion.essentials.mixin;

import com.aurorion.essentials.client.PhoneGroupsClient;
import com.aurorion.essentials.client.PhoneUi;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A conversa de grupo usa a mesma tela de conversa do Mensagens. So o que e de jogador muda:
 *
 * <ul>
 *   <li><b>Enviar</b> ({@code sendMessage}, botao e Enter): para conversa que nao e de jogador o
 *       telefone so guarda o texto e responde sozinho "Mensagem recebida" depois de 35 ticks. No grupo
 *       o texto vai ao servidor ({@link PhoneGroupsClient#handleChatSend}).</li>
 *   <li><b>Camera e localizacao</b>: grupo e so texto (a galeria e barrada no
 *       {@code PhoneMessageAttachMixin}, que chama estes mesmos metodos para camera e localizacao).</li>
 *   <li><b>Cabecalho</b>: tocar no nome do grupo abre os detalhes (membros, adicionar, sair), como
 *       nos apps de mensagem de verdade. A faixa vai do avatar ate antes dos botoes de ligacao.</li>
 * </ul>
 *
 * <p>Conversa de jogador passa intacta por todos os ganchos: o primeiro teste e o id da conversa.
 */
@Pseudo
@Mixin(targets = "com.mattupolis.phone.client.gui.PhoneMessageChatScreen", remap = false)
public abstract class PhoneGroupChatMixin {
    @Inject(method = "sendMessage()V", at = @At("HEAD"), cancellable = true, require = 0)
    private void aurorion_essentials$sendToGroup(CallbackInfo ci) {
        String threadId = PhoneGroupsClient.chatThreadId(this);
        if (!PhoneGroupsClient.isGroupThread(threadId)) return;
        ci.cancel();
        PhoneGroupsClient.handleChatSend(threadId, PhoneGroupsClient.chatInput(this));
    }

    @Inject(method = {"sendLocation()V", "openCamera()V"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void aurorion_essentials$textOnly(CallbackInfo ci) {
        if (PhoneGroupsClient.blockAttachment(PhoneGroupsClient.chatThreadId(this))) ci.cancel();
    }

    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void aurorion_essentials$openGroupInfo(double mouseX, double mouseY, int button,
                                                   CallbackInfoReturnable<Boolean> cir) {
        if (button != 0) return;
        Screen self = (Screen) (Object) this;
        int x = PhoneUi.phoneLeft(self);
        int y = PhoneUi.phoneTop(self);
        // Avatar e nome: de x+32 ate antes do video (x+W-74); altura do cabecalho (y+22 a y+68).
        if (mouseX < x + 32 || mouseX >= x + PhoneUi.PHONE_WIDTH - 76 || mouseY < y + 26 || mouseY >= y + 66) return;
        String threadId = PhoneGroupsClient.chatThreadId(this);
        if (PhoneGroupsClient.openInfo(threadId)) cir.setReturnValue(true);
    }
}
