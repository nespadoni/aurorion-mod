package com.aurorion.profissoes.skill;

import com.aurorion.core.character.SheetCheck;
import com.aurorion.profissoes.AurorionProfissoes;
import com.aurorion.profissoes.config.ProfessionsConfig;
import com.aurorion.profissoes.server.SkillCheck;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;

/**
 * Pesca com a ficha (Natureza): crítico pega um peixe a mais; falha crítica, o peixe escapa. O resto da
 * pesca (sorte da vara, encantamento, tabela do mod) não muda.
 *
 * <p>Vale para a pesca vanilla e a do Aquaculture (as duas postam {@link ItemFishedEvent}) e para a do
 * Starcatcher, que troca o evento pelo minijogo dele e entrega o peixe por conta própria (ver
 * {@code StarcatcherMixin}). Um teste por peixe pescado.
 */
@EventBusSubscriber(modid = AurorionProfissoes.MOD_ID)
public final class FishingCheck {
    private FishingCheck() {
    }

    public static SheetCheck.Outcome roll(ServerPlayer player) {
        if (!ProfessionsConfig.SPEC.isLoaded() || player.isCreative()) return SheetCheck.Outcome.SUCCESS;
        return SkillCheck.roll(player, ProfessionsConfig.FISHING_SKILL.get(), ProfessionsConfig.FISHING_DC.get());
    }

    public static void escaped(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("aurorion_profissoes.pesca.escapou").withStyle(ChatFormatting.GRAY), true);
    }

    public static void doubled(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("aurorion_profissoes.pesca.dobro").withStyle(ChatFormatting.AQUA), true);
    }

    /** Depois de quem troca a pesca (Starcatcher cancela o evento para o minijogo dele): só pesca que vai sair. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFished(ItemFishedEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player) || event.getDrops().isEmpty()) return;
        switch (roll(player)) {
            case CRIT -> {
                ItemStack first = event.getDrops().get(0);
                if (!first.isEmpty()) player.getInventory().placeItemBackInInventory(first.copy());
                doubled(player);
            }
            case CRIT_FAIL -> {
                event.setCanceled(true);
                escaped(player);
            }
            default -> {
            }
        }
    }
}
