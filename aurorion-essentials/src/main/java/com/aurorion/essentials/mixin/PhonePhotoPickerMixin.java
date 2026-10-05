package com.aurorion.essentials.mixin;

import com.aurorion.essentials.client.PhoneIcons;
import com.aurorion.essentials.client.PhonePhotoPicker;
import com.aurorion.essentials.client.PhoneUi;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.nio.file.Path;

/**
 * Seletor de fotos do DM do Gram aberto por {@link PhonePhotoPicker} para outro destino (Mensagens ou
 * novo post do Gram). Sem marca, o seletor funciona exatamente como no telefone.
 *
 * <p>Ao tocar numa foto o telefone le os bytes dela para enviar no DM; com marca, a foto vai para o
 * destino e a leitura devolve um array <b>vazio</b>, o sinal do proprio telefone para "nao deu para
 * ler a foto": ele para ali (o som de erro que viria em seguida tambem e suprimido). Nunca
 * {@code null}: o telefone le {@code bytes.length} sem conferir null e o cliente cai. O botao voltar
 * leva de volta ao destino em vez do DM.
 */
@Pseudo
@Mixin(targets = "com.mattupolis.phone.client.gui.PhoneInstagramDmPhotoPickerScreen", remap = false)
public abstract class PhonePhotoPickerMixin {
    @Unique
    private static final byte[] NO_BYTES = new byte[0];

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void aurorion_essentials$backToTarget(double mouseX, double mouseY, int button,
                                                  CallbackInfoReturnable<Boolean> cir) {
        PhonePhotoPicker.Target target = PhonePhotoPicker.targetOf(this);
        if (target == null) return;
        Screen self = (Screen) (Object) this;
        int x = PhoneUi.phoneLeft(self);
        int y = PhoneUi.phoneTop(self);
        if (PhoneIcons.inside(mouseX, mouseY, x + 6, y + 25, 24, 24)) {
            PhoneUi.playBack();
            PhoneUi.open(target.back());
            cir.setReturnValue(true);
        }
    }

    @Redirect(method = "mouseClicked", at = @At(value = "INVOKE",
            target = "Lcom/mattupolis/phone/client/gui/PhoneInstagramStore;readImageBytesForNetwork(Ljava/nio/file/Path;)[B"),
            require = 0)
    private byte[] aurorion_essentials$sendToTarget(Path photo) {
        PhonePhotoPicker.Target target = PhonePhotoPicker.targetOf(this);
        if (target != null) {
            target.pick(photo);
            return NO_BYTES;
        }
        try {
            Method read = Class.forName("com.mattupolis.phone.client.gui.PhoneInstagramStore")
                    .getDeclaredMethod("readImageBytesForNetwork", Path.class);
            read.setAccessible(true);
            return read.invoke(null, photo) instanceof byte[] bytes ? bytes : NO_BYTES;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return NO_BYTES;
        }
    }

    @Redirect(method = "mouseClicked", at = @At(value = "INVOKE",
            target = "Lcom/mattupolis/phone/client/gui/PhoneSoundManager;playError()V"), require = 0)
    private void aurorion_essentials$quietWhenHandled() {
        if (PhonePhotoPicker.targetOf(this) == null) PhoneUi.playError();
    }
}
