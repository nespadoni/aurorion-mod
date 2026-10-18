package com.aurorion.essentials.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

/** O id de um post do Gram no cliente, para o {@link PhoneGramStoreMixin} achar a foto sob demanda. */
@Pseudo
@Mixin(targets = "com.mattupolis.phone.client.gui.PhoneInstagramStore$GramPost", remap = false)
public interface PhoneGramPostAccessor {
    @Accessor("id")
    String aurorion_essentials$postId();
}
