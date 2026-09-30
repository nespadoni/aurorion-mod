package com.aurorion.profissoes.server;

import com.aurorion.profissoes.AurorionProfissoes;
import com.aurorion.profissoes.api.ProfessionApi;
import com.aurorion.profissoes.compat.LsoCompat;
import com.aurorion.profissoes.config.ProfessionsConfig;
import com.aurorion.profissoes.data.Profession;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Regras dos consumiveis do LSO que tratam todas as partes do corpo de uma vez. */
@EventBusSubscriber(modid = AurorionProfissoes.MOD_ID)
public final class MedicalItemEvents {
    private static final TagKey<Item> DOCTOR_ONLY = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(AurorionProfissoes.MOD_ID, "doctor_only_healing"));

    private MedicalItemEvents() { }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void rightClick(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || allowed(player, event.getItemStack())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        explain(player);
    }

    /** Segunda barreira para usos iniciados por outro mod ou por um pacote fora do fluxo normal. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void start(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || allowed(player, event.getItem())) return;
        event.setCanceled(true);
        explain(player);
    }

    /**
     * Medkit e tonico usam cura global diferida no LSO. Ao terminar nas maos de um medico, o
     * tratamento clinico e aplicado ao attachment real e sincronizado imediatamente. Isso tambem
     * remove a marca de lesao grave que primeiros socorros comuns apenas estabilizam.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void finish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !restricted(event.getItem())
                || !ProfessionsConfig.enabled() || ProfessionApi.of(player) != Profession.DOCTOR) return;
        LsoCompat.healAll(player);
    }

    private static boolean allowed(ServerPlayer player, ItemStack stack) {
        return !restricted(stack) || !ProfessionsConfig.enabled() || ProfessionApi.of(player) == Profession.DOCTOR;
    }

    private static boolean restricted(ItemStack stack) {
        return !stack.isEmpty() && stack.is(DOCTOR_ONLY);
    }

    private static void explain(ServerPlayer player) {
        player.displayClientMessage(Component.literal(
                "Este tratamento completo so pode ser aplicado por um medico."), true);
    }
}
