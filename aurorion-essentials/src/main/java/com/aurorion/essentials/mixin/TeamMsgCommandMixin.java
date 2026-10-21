package com.aurorion.essentials.mixin;

import com.aurorion.essentials.privacy.PrivacyConfig;
import com.aurorion.essentials.privacy.PrivateMessageCommands;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.commands.TeamMsgCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Restringe {@code /teammsg} e o atalho {@code /tm} junto com o {@code /msg}, pela mesma
 * {@link PrivacyConfig#RESTRICT_PRIVATE_MESSAGES}: e o mesmo canal privado, so que para o time do
 * scoreboard. Mesmo truque do {@link WhisperCommandMixin} — o redirect casa com os dois
 * {@code Commands.literal(...)} do {@code register}.
 *
 * <p>{@code require = 0} de proposito, apesar de a config ser obrigatoria: este alvo nao tinha mixin
 * antes, e perde-lo deve deixar o {@code /teammsg} aberto (e o {@code PrivacyMixinTargetTest}
 * vermelho), nao derrubar o servidor no boot.
 */
@Mixin(TeamMsgCommand.class)
public abstract class TeamMsgCommandMixin {
    @Redirect(method = "register", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/commands/Commands;literal(Ljava/lang/String;)Lcom/mojang/brigadier/builder/LiteralArgumentBuilder;"))
    private static LiteralArgumentBuilder<CommandSourceStack> aurorion_essentials$restrictTeamMessage(String name) {
        return Commands.literal(name).requires(PrivateMessageCommands::allowed);
    }
}
