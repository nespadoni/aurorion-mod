package com.aurorion.profissoes.mixin;

import com.aurorion.profissoes.api.ProfessionApi;
import com.aurorion.profissoes.config.ProfessionsConfig;
import com.aurorion.profissoes.data.Profession;
import com.aurorion.profissoes.server.SkillCheck;
import com.aurorion.profissoes.server.SpecialtyRules;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Items;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.List;

@Mixin(EnchantmentMenu.class)
public abstract class EnchantmentMenuMixin {
    @Shadow @Final public int[] costs;
    @Shadow @Final public int[] enchantClue;
    @Shadow @Final public int[] levelClue;
    @Shadow @Final private net.minecraft.world.Container enchantSlots;
    @Unique private Player aurorion$owner;

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("RETURN"))
    private void aurorion$owner(int id, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) { aurorion$owner = inventory.player; }

    @Inject(method = "slotsChanged", at = @At("TAIL"))
    private void aurorion$lockThirdOption(net.minecraft.world.Container inventory, CallbackInfo ci) {
        if (aurorion$owner == null || SpecialtyRules.canUseTableOption(aurorion$owner, 2)) return;
        costs[2] = 0;
        enchantClue[2] = -1;
        levelClue[2] = -1;
        if (!aurorion$owner.level().isClientSide()) ((EnchantmentMenu)(Object)this).broadcastChanges();
    }

    @Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true)
    private void aurorion$validateThirdOption(Player player, int option, CallbackInfoReturnable<Boolean> cir) {
        if (!SpecialtyRules.canUseTableOption(player, option)) {
            if (option == 2) player.displayClientMessage(
                    Component.translatable("message.aurorion_profissoes.enchant.third_option_requires_arcanist"), true);
            cir.setReturnValue(false);
            return;
        }
        if (player instanceof ServerPlayer serverPlayer) aurorion$skillCheck(serverPlayer, option, cir);
    }

    /**
     * Teste de Astros &amp; Mistos (a perícia do config) a cada encantamento, com as mesmas condições que o
     * vanilla confere antes (não testa clique inválido). O arcanista rola com vantagem. Crítico: a lápis
     * volta; falha crítica: lápis e níveis se gastam e o item sai sem encanto.
     */
    @Unique
    private void aurorion$skillCheck(ServerPlayer player, int option, CallbackInfoReturnable<Boolean> cir) {
        if (!ProfessionsConfig.SPEC.isLoaded() || player.isCreative() || option < 0 || option >= costs.length) return;
        int lapis = option + 1;
        ItemStack item = enchantSlots.getItem(0);
        ItemStack fuel = enchantSlots.getItem(1);
        if (item.isEmpty() || costs[option] <= 0 || fuel.getCount() < lapis
                || player.experienceLevel < lapis || player.experienceLevel < costs[option]) return;
        boolean arcanist = ProfessionApi.has(player, Profession.ARCANIST);
        switch (SkillCheck.roll(player, ProfessionsConfig.ENCHANT_SKILL.get(), ProfessionsConfig.ENCHANT_DC.get() + 3 * option, arcanist)) {
            case CRIT -> {
                player.getInventory().placeItemBackInInventory(new ItemStack(Items.LAPIS_LAZULI, lapis));
                player.displayClientMessage(Component.translatable("aurorion_profissoes.encanto.critico").withStyle(ChatFormatting.GOLD), true);
            }
            case CRIT_FAIL -> {
                player.onEnchantmentPerformed(item, lapis);
                fuel.shrink(lapis);
                enchantSlots.setChanged();
                ((EnchantmentMenu) (Object) this).slotsChanged(enchantSlots);
                player.displayClientMessage(Component.translatable("aurorion_profissoes.encanto.falha_critica").withStyle(ChatFormatting.DARK_RED), true);
                player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 1.2f);
                cir.setReturnValue(true);
            }
            default -> {
            }
        }
    }

    @Inject(method = "getEnchantmentList", at = @At("RETURN"), cancellable = true)
    private void aurorion$levels(RegistryAccess access, ItemStack stack, int slot, int cost, CallbackInfoReturnable<List<EnchantmentInstance>> cir) {
        if (aurorion$owner != null) cir.setReturnValue(SpecialtyRules.tableOptions(aurorion$owner, cir.getReturnValue()));
    }
}
