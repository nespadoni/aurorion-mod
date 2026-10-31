package com.aurorion.essentials.mixin;

import com.aurorion.essentials.perf.AdvancementFile;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Login sem o congelamento das conquistas: o arquivo e lido em tempo linear ({@link AdvancementFile}) e
 * o resto e o {@code load} vanilla, passo a passo — {@code applyFrom} (na mesma ordem, pela data do
 * progresso), depois as conquistas automaticas e os listeners.
 *
 * <p>Quando o leitor rapido devolve {@code null} (versao antiga, arquivo estranho, erro), o metodo
 * vanilla roda inteiro, do mesmo jeito de antes.
 */
@Mixin(PlayerAdvancements.class)
public abstract class AdvancementLoadMixin {
    @Shadow @Final private static Logger LOGGER;
    @Shadow @Final private Path playerSavePath;
    @Shadow @Final private Set<AdvancementHolder> progressChanged;

    @Shadow
    private void startProgress(AdvancementHolder advancement, AdvancementProgress advancementProgress) {
        throw new AssertionError();
    }

    @Shadow
    private void markForVisibilityUpdate(AdvancementHolder advancement) {
        throw new AssertionError();
    }

    @Shadow
    private void checkForAutomaticTriggers(ServerAdvancementManager manager) {
        throw new AssertionError();
    }

    @Shadow
    private void registerListeners(ServerAdvancementManager manager) {
        throw new AssertionError();
    }

    @Inject(method = "load", at = @At("HEAD"), cancellable = true)
    private void aurorion_essentials$linearLoad(ServerAdvancementManager manager, CallbackInfo ci) {
        Map<ResourceLocation, AdvancementProgress> progress = AdvancementFile.read(playerSavePath);
        if (progress == null) return;
        progress.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach(entry -> {
            AdvancementHolder holder = manager.get(entry.getKey());
            if (holder == null) {
                LOGGER.warn("Ignored advancement '{}' in progress file {} - it doesn't exist anymore?",
                        entry.getKey(), playerSavePath);
                return;
            }
            startProgress(holder, entry.getValue());
            progressChanged.add(holder);
            markForVisibilityUpdate(holder);
        });
        checkForAutomaticTriggers(manager);
        registerListeners(manager);
        ci.cancel();
    }
}
