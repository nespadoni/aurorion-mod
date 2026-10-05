package com.aurorion.essentials.mixin;

import com.aurorion.essentials.client.PhoneChoiceSheet;
import com.aurorion.essentials.client.PhonePhotoPicker;
import com.aurorion.essentials.client.PhoneUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * O "+" de novo post do Gram abria direto a camera. Agora pergunta "Camera" ou "Galeria"; a galeria
 * segue para a mesma tela de legenda que a camera abre ao tirar a foto. A tela de camera ja criada
 * pelo telefone e guardada para a opcao Camera, sem mudar nada no caminho original.
 */
@Pseudo
@Mixin(targets = "com.mattupolis.phone.client.gui.PhoneInstagramScreen", remap = false)
public abstract class PhoneGramPostChoiceMixin {
    @Redirect(method = "mouseClicked", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"),
            require = 0)
    private void aurorion_essentials$askCameraOrGallery(Minecraft minecraft, Screen next) {
        if (!PhoneUi.is(next, "PhoneInstagramCameraScreen")) {
            minecraft.setScreen(next);
            return;
        }
        Screen gram = (Screen) (Object) this;
        minecraft.setScreen(new PhoneChoiceSheet(gram, "Novo post", List.of(
                new PhoneChoiceSheet.Option("Câmera", () -> minecraft.setScreen(next)),
                new PhoneChoiceSheet.Option("Galeria", () -> PhonePhotoPicker.open(new PhonePhotoPicker.GramPost())))));
    }
}
