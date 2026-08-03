package com.aurorion.essentials.mixin;

import com.aurorion.essentials.privacy.PrivacyConfig;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.commands.MessageCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Restringe /msg, /tell e /w a operadores quando {@link PrivacyConfig#RESTRICT_PRIVATE_MESSAGES}
 * esta ligado. O vanilla registra os tres sem nenhum {@code requires(...)} — intercepta a
 * construcao do literal para anexar a exigencia de nivel 2, valendo pros tres de uma vez so (o
 * mesmo redirect casa com as tres chamadas de {@code Commands.literal(...)} do metodo).
 */
@Mixin(MessageCommand.class)
public abstract class WhisperCommandMixin {

    private static final int OP_PERMISSION_LEVEL = 2;

    @Redirect(method = "register", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/commands/Commands;literal(Ljava/lang/String;)Lcom/mojang/brigadier/builder/LiteralArgumentBuilder;"))
    private static LiteralArgumentBuilder<CommandSourceStack> aurorion_essentials$restrictWhisper(String name) {
        LiteralArgumentBuilder<CommandSourceStack> literal = Commands.literal(name);
        if (PrivacyConfig.RESTRICT_PRIVATE_MESSAGES.get()) {
            literal.requires(source -> source.hasPermission(OP_PERMISSION_LEVEL));
        }
        return literal;
    }
}
