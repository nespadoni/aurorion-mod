package com.aurorion.essentials.mixin;

import com.aurorion.essentials.privacy.PrivacyConfig;
import com.aurorion.essentials.privacy.PrivateMessageCommands;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.commands.MsgCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Restringe /msg, /tell e /w a operadores quando {@link PrivacyConfig#RESTRICT_PRIVATE_MESSAGES}
 * esta ligado. O vanilla registra os tres sem nenhum {@code requires(...)} — intercepta a
 * construcao do literal para anexar a exigencia de nivel 2, valendo pros tres de uma vez so (o
 * mesmo redirect casa com as tres chamadas de {@code Commands.literal(...)} do metodo).
 */
@Mixin(MsgCommand.class)
public abstract class WhisperCommandMixin {

    @Redirect(method = "register", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/commands/Commands;literal(Ljava/lang/String;)Lcom/mojang/brigadier/builder/LiteralArgumentBuilder;"))
    private static LiteralArgumentBuilder<CommandSourceStack> aurorion_essentials$restrictWhisper(String name) {
        // O config so pode ser lido depois de carregado; register() roda cedo demais para isso
        // (ate na tela de titulo, antes do config estar pronto), entao a leitura fica dentro do
        // predicate, avaliado so quando Brigadier realmente checa permissao contra uma fonte real.
        // A troca da config com gente online reenvia a arvore de comandos: ver PrivateMessageCommands.
        return Commands.literal(name).requires(PrivateMessageCommands::allowed);
    }
}
