package com.aurorion.aeonita.registry;

import com.aurorion.aeonita.AurorionAeonita;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AeonitaCreativeTab {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AurorionAeonita.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> AEONITA_TAB = CREATIVE_MODE_TABS.register(
            "aeonita",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.aurorion_aeonita"))
                    .icon(() -> AeonitaItems.AEONITA_INGOT_YELLOW.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(AeonitaItems.AEONITA_INGOT_YELLOW.get());
                        output.accept(AeonitaItems.AEONITA_INGOT_BLUE.get());
                        output.accept(AeonitaItems.AEONITA_INGOT_RED.get());
                        output.accept(AeonitaItems.AEONITA_BLOCK_YELLOW.get());
                        output.accept(AeonitaItems.AEONITA_BLOCK_BLUE.get());
                        output.accept(AeonitaItems.AEONITA_BLOCK_RED.get());
                        output.accept(AeonitaItems.SELECTION_ALTAR.get());
                        output.accept(AeonitaItems.UNIFORM_CAPE_SEM_CASA.get());
                        output.accept(AeonitaItems.UNIFORM_CAPE_VENTHRA.get());
                        output.accept(AeonitaItems.UNIFORM_CAPE_SYLVARA.get());
                        output.accept(AeonitaItems.UNIFORM_CAPE_NYX.get());
                        output.accept(AeonitaItems.UNIFORM_CAPE_IGNIVAR.get());
                        output.accept(AeonitaItems.UNIFORM_CAPE_AETHERIS.get());
                    })
                    .build());

    private AeonitaCreativeTab() {
    }
}
