package com.aurorion.profissoes.mixin.compat;

import com.aurorion.core.character.SheetCheck;
import com.aurorion.profissoes.config.ProfessionsConfig;
import com.aurorion.profissoes.server.SkillCheck;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Locks Reforged, arrombar: o minijogo continua sendo do jogador, mas a mão da ficha (Furtividade, que soma
 * a Destreza) pesa em cada pino errado.
 * <ul>
 *   <li>o desgaste da gazua: sucesso no teste gasta metade, crítico não gasta, falha crítica gasta o dobro;</li>
 *   <li>o progresso: quando o erro zeraria os pinos já acertados, um sucesso no teste segura o progresso.</li>
 * </ul>
 * O {@code tryPin} chama {@code resetsProgress} duas vezes (para zerar e para avisar o cliente); a decisão
 * da primeira vale para a segunda.
 */
@Pseudo
@Mixin(targets = "melonslise.locks.common.container.LockPickingContainer", remap = false)
public abstract class LocksMixin {
    @Shadow @Final public Player player;
    @Unique private boolean aurorion$kept;

    @ModifyArg(method = "tryPin", require = 0, index = 1, at = @At(value = "INVOKE",
            target = "Lmelonslise/locks/common/container/LockPickingContainer;wearPick(Lnet/minecraft/world/entity/player/Player;I)Z"))
    private int aurorion$wear(int wear) {
        if (!(player instanceof ServerPlayer serverPlayer) || !ProfessionsConfig.SPEC.isLoaded()) return wear;
        return switch (SkillCheck.roll(serverPlayer, ProfessionsConfig.LOCKPICK_SKILL.get(), ProfessionsConfig.LOCKPICK_DC.get())) {
            case CRIT -> 0;
            case SUCCESS -> wear / 2;
            case FAIL -> wear;
            case CRIT_FAIL -> wear * 2;
        };
    }

    @ModifyExpressionValue(method = "tryPin", require = 0, at = @At(value = "INVOKE", ordinal = 0,
            target = "Lmelonslise/locks/common/container/LockPickingPolicy;resetsProgress(Lmelonslise/locks/common/container/LockPickingPolicy$PinOutcome;)Z"))
    private boolean aurorion$keepProgress(boolean resets) {
        aurorion$kept = false;
        if (!resets || !(player instanceof ServerPlayer serverPlayer) || !ProfessionsConfig.SPEC.isLoaded()) return resets;
        SheetCheck.Outcome outcome = SkillCheck.roll(serverPlayer, ProfessionsConfig.LOCKPICK_SKILL.get(), ProfessionsConfig.LOCKPICK_DC.get());
        aurorion$kept = outcome.success();
        return !aurorion$kept;
    }

    @ModifyExpressionValue(method = "tryPin", require = 0, at = @At(value = "INVOKE", ordinal = 1,
            target = "Lmelonslise/locks/common/container/LockPickingPolicy;resetsProgress(Lmelonslise/locks/common/container/LockPickingPolicy$PinOutcome;)Z"))
    private boolean aurorion$reportProgress(boolean resets) {
        boolean kept = aurorion$kept;
        aurorion$kept = false;
        return resets && !kept;
    }
}
