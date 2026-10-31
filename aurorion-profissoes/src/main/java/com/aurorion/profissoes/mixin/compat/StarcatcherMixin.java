package com.aurorion.profissoes.mixin.compat;

import com.aurorion.profissoes.skill.FishingCheck;
import com.aurorion.profissoes.skill.StarcatcherBridge;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Starcatcher: o peixe do minijogo sai de {@code FishApi.spawnFishFromPlayerFishing}. O teste de Natureza
 * vem antes: falha crítica cancela (o peixe escapa); crítico deixa sair e chama de novo, uma vez, para um
 * segundo peixe (a mesma tabela do mod sorteia).
 */
@Pseudo
@Mixin(targets = "com.wdiscute.starcatcher.fish.FishApi", remap = false)
public abstract class StarcatcherMixin {
    /** A segunda chamada do crítico não testa de novo. Só a thread do servidor. */
    @Unique private static boolean aurorion$bonus;

    @Inject(method = "spawnFishFromPlayerFishing(Lnet/minecraft/server/level/ServerPlayer;ZIZZI)V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void aurorion$fish(ServerPlayer player, boolean a, int b, boolean c, boolean d, int e, CallbackInfo ci) {
        if (aurorion$bonus) return;
        switch (FishingCheck.roll(player)) {
            case CRIT_FAIL -> {
                FishingCheck.escaped(player);
                ci.cancel();
            }
            case CRIT -> {
                // O primeiro peixe sai pelo próprio método (depois deste HEAD); o segundo, por esta chamada.
                aurorion$bonus = true;
                try {
                    StarcatcherBridge.spawnAgain(player, a, b, c, d, e);
                } finally {
                    aurorion$bonus = false;
                }
                FishingCheck.doubled(player);
            }
            default -> {
            }
        }
    }
}
