package com.aurorion.ethereal.registry;

import com.aurorion.ethereal.AurorionEthereal;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class EtherealCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AurorionEthereal.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "ethereal",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.aurorion_ethereal"))
                    .icon(() -> EtherealItems.AEONIC_PROJECTOR.get().getDefaultInstance())
                    .displayItems((parameters, output) -> output.accept(EtherealItems.AEONIC_PROJECTOR.get()))
                    .build());

    private EtherealCreativeTab() {
    }
}
