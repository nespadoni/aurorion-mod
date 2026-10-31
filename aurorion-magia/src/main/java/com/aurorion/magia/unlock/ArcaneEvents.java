package com.aurorion.magia.unlock;

import com.aurorion.core.character.SkillTreeChangedEvent;
import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.config.MagiaConfig;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * O Despertar Arcano e a especialidade fora do gate de conjuração: o crítico e o desconto de mana na hora
 * em que a magia sai, a troca de nós vinda da Trama, e a oficina arcana (mesa de inscrição, forja de
 * pergaminhos, bigorna arcana, caldeirão do alquimista) que só responde a quem tem o Despertar I.
 */
@EventBusSubscriber(modid = AurorionMagia.MOD_ID)
public final class ArcaneEvents {
    /** {@code data/aurorion_magia/tags/block/oficina_arcana.json}. */
    public static final TagKey<Block> ARCANE_WORKSHOP = TagKey.create(Registries.BLOCK, AurorionMagia.id("oficina_arcana"));

    private ArcaneEvents() {
    }

    /** A magia está saindo: o nível a mais do crítico e a mana a menos do especialista. */
    @SubscribeEvent
    public static void onCast(SpellOnCastEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        AbstractSpell spell = SpellRegistry.getSpell(event.getSpellId());
        if (spell == null || spell == SpellRegistry.none()) return;
        int crit = SpellCheck.critLevel(player, spell, event.getSpellLevel());
        if (crit > event.getSpellLevel()) event.setSpellLevel(crit);
        int less = MagiaConfig.SPECIALIST_MANA.get();
        if (less > 0 && ArcaneProfile.isSpecialty(player, event.getSchoolType())) {
            event.setManaCost(Math.round(event.getManaCost() * (100 - less) / 100f));
        }
    }

    /** A Trama avisou que os nós mudaram: nível do Despertar, especialidade e o espelho do Iron's. */
    @SubscribeEvent
    public static void onSkillTree(SkillTreeChangedEvent event) {
        ArcaneProfile.refresh(event.player());
    }

    @SubscribeEvent
    public static void onUseWorkshop(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !ArcaneProfile.nodeMode()) return;
        if (!event.getLevel().getBlockState(event.getPos()).is(ARCANE_WORKSHOP)) return;
        if (SpellAccess.bypasses(player) || ArcaneProfile.of(player).tier() >= 1) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        player.displayClientMessage(Component.translatable("aurorion_magia.oficina_sem_no").withStyle(ChatFormatting.RED), true);
    }
}
