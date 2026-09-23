package com.aurorion.magia.event;

import com.aurorion.core.character.CharacterResetEvent;
import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.compat.EmotecraftCompat;
import com.aurorion.magia.compat.FrozenLink;
import com.aurorion.magia.compat.VoiceMute;
import com.aurorion.magia.registry.MagiaEffects;
import com.aurorion.magia.spell.Binding;
import com.aurorion.magia.spell.Domination;
import com.aurorion.magia.spell.IronBinding;
import com.aurorion.magia.spell.Momentum;
import com.aurorion.magia.spell.Seals;
import com.aurorion.magia.unlock.SpellAccess;
import com.aurorion.magia.unlock.SpellUnlockData;
import io.redspace.ironsspellbooks.api.events.InscribeSpellEvent;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Todos os pontos em que o servidor diz "nao". Tudo aqui e reacao a evento, disparado por uma acao
 * de alguem: nenhum listener de tick, nenhuma varredura de jogadores.
 */
@EventBusSubscriber(modid = AurorionMagia.MOD_ID)
public final class MagiaServerEvents {
    private MagiaServerEvents() {
    }

    // --- Conjuracao ---------------------------------------------------------------------------

    /**
     * O gate de conjuracao. Vale para livro, scroll e arma imbuida — o {@link CastSource} nao importa,
     * so o {@code COMMAND} (o {@code /cast} da staff) passa direto.
     *
     * <p>Prioridade alta para cancelar antes de outros addons reagirem a uma conjuracao que nao vai
     * acontecer.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPreCast(SpellPreCastEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getCastSource() == CastSource.COMMAND) return;

        if (FrozenLink.isFrozen(player)) {
            deny(event, player, Component.translatable("aurorion_magia.congelado").withStyle(ChatFormatting.AQUA));
            return;
        }
        if (player.hasEffect(MagiaEffects.SILENCED)) {
            deny(event, player, Component.translatable("aurorion_magia.sem_voz").withStyle(ChatFormatting.DARK_PURPLE));
            return;
        }
        if (player.hasEffect(MagiaEffects.DISORIENTED)) {
            deny(event, player, Component.translatable("aurorion_magia.mente_turva").withStyle(ChatFormatting.DARK_AQUA));
            return;
        }

        AbstractSpell spell = SpellRegistry.getSpell(event.getSpellId());
        if (!SpellAccess.canCast(player, spell)) {
            deny(event, player, Component.translatable("aurorion_magia.nao_liberada",
                    spell.getDisplayName(player)).withStyle(ChatFormatting.RED));
        }
    }

    private static void deny(SpellPreCastEvent event, ServerPlayer player, Component message) {
        event.setCanceled(true);
        player.displayClientMessage(message, true);
    }

    /** "Equipar": gravar no livro uma magia que o personagem nao aprendeu em aula. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInscribe(InscribeSpellEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        AbstractSpell spell = event.getSpellData().getSpell();
        if (SpellAccess.canCast(player, spell)) return;

        event.setCanceled(true);
        player.displayClientMessage(Component.translatable("aurorion_magia.inscricao_negada",
                spell.getDisplayName(player)).withStyle(ChatFormatting.RED), true);
    }

    // --- Entrada, dimensao e personagem -------------------------------------------------------

    /**
     * Espelha a lista oficial no Iron's a cada entrada. {@code LOWEST} para rodar depois do login do
     * Iron's Restrictions, que ensina as {@code DefaultLearntSpells} dele — com
     * {@code authoritative}, o que ele ensinou por fora e esquecido aqui mesmo.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        SpellAccess.reconcile(player);
        Seals.sendAll(player);
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) Seals.sendAll(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) Seals.sendAll(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        IronBinding.release(event.getEntity().getUUID());
    }

    /** Aula e do personagem, nao da conta: personagem novo nao sabe magia nenhuma. */
    @SubscribeEvent
    public static void onCharacterReset(CharacterResetEvent event) {
        SpellUnlockData.get(event.server()).clear(event.account());
        ServerPlayer player = event.player();
        if (player != null) SpellAccess.reconcile(player);
    }

    /** Estado so em memoria: um mundo nao herda lacre, retrato de armadura nem mudo de outro. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        Seals.clear();
        IronBinding.clear();
        VoiceMute.clear();
    }

    // --- Vox Interdicta -----------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onChat(ServerChatEvent event) {
        if (!event.getPlayer().hasEffect(MagiaEffects.SILENCED)) return;
        event.setCanceled(true);
        event.getPlayer().displayClientMessage(Component.translatable("aurorion_magia.sem_voz")
                .withStyle(ChatFormatting.DARK_PURPLE), true);
    }

    // --- Vinculum Carnificis ------------------------------------------------------------------

    /**
     * Perola, chorus, teleporte de magia do Iron's (o {@code SpellTeleportEvent} estende este evento)
     * e o proprio enderman: a corrente nao deixa. So o {@code /tp} da staff passa.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onTeleport(EntityTeleportEvent event) {
        if (event instanceof EntityTeleportEvent.TeleportCommand
                || event instanceof EntityTeleportEvent.SpreadPlayersCommand) return;
        Entity entity = event.getEntity();
        if (entity.level().isClientSide || !Binding.isBound(entity)) return;
        event.setCanceled(true);
        Binding.refuseTeleport((LivingEntity) entity);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onTravelDimension(EntityTravelToDimensionEvent event) {
        if (!event.getEntity().level().isClientSide && Binding.isBound(event.getEntity())) {
            event.setCanceled(true);
            Binding.refuseTeleport((LivingEntity) event.getEntity());
        }
    }

    // --- Sigillum Clausum ---------------------------------------------------------------------

    /**
     * Abrir bloco lacrado. O cliente nao sabe dos lacres e chega a prever a porta abrindo; o
     * servidor recusa e a atualizacao de bloco desfaz a previsao no mesmo tick.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof ServerPlayer player) {
            Seals.Seal seal = Seals.get(level, event.getPos());
            if (seal != null && !Seals.mayOpen(player, seal)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                Seals.deny(player, seal);
                return;
            }
        }
        // Desorientado: porta e botao continuam funcionando; so o item na mao nao e usado no bloco.
        if (isDisoriented(event.getEntity())) event.setUseItem(TriState.FALSE);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Seals.Seal seal = Seals.get(level, event.getPos());
        if (seal == null) return;
        if (event.getPlayer() instanceof ServerPlayer player && !Seals.mayOpen(player, seal)) {
            event.setCanceled(true);
            Seals.deny(player, seal);
            return;
        }
        Seals.onBroken(level, event.getPos());
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel() instanceof ServerLevel level) {
            event.getAffectedBlocks().removeIf(pos -> Seals.get(level, pos) != null);
        }
    }

    // --- Ferrum Ligatum -----------------------------------------------------------------------

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            IronBinding.onEquipmentChange(player, event.getSlot(), event.getFrom(), event.getTo());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onToss(ItemTossEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && IronBinding.onToss(player, event.getEntity().getItem())) {
            event.setCanceled(true);
        }
    }

    // --- Desorientacao: sem item --------------------------------------------------------------
    // Estes rodam nos dois lados de proposito. O efeito ja esta sincronizado com o cliente do
    // afetado, entao cancelar la tambem evita a animacao fantasma de "comecou a comer e parou".

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (!isDisoriented(event.getEntity())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent
    public static void onStartUsing(LivingEntityUseItemEvent.Start event) {
        if (isDisoriented(event.getEntity())) event.setCanceled(true);
    }

    private static boolean isDisoriented(LivingEntity entity) {
        return entity instanceof Player player && player.hasEffect(MagiaEffects.DISORIENTED);
    }

    // --- Dominio -----------------------------------------------------------------------------

    /** Dispara so quando algum mob troca de alvo; para quem nao esta dominado, uma consulta de efeito. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getEntity().level().isClientSide || !(event.getEntity() instanceof Mob mob)) return;
        if (!Domination.allowsTargetChange(mob, event.getNewAboutToBeSetTarget())) event.setCanceled(true);
    }

    /** Segunda trava: dano em area ou projetil perdido de um dominado nao fere o mestre nem os aliados. */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (attacker != null && Domination.isHarmingProtected(attacker, event.getEntity())) {
            event.setCanceled(true);
        }
    }

    // --- Fim de efeito ------------------------------------------------------------------------

    @SubscribeEvent
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (instance != null) onEffectEnded(event.getEntity(), instance.getEffect().value());
    }

    /** Leite, {@code /effect clear}, totem: o estado ligado ao efeito acaba junto. */
    @SubscribeEvent
    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        onEffectEnded(event.getEntity(), event.getEffect().value());
    }

    /**
     * Cada efeito carrega um pedaco de estado fora dele (mestre, ancora, emote, microfone...). Aqui
     * esse estado e solto. Nenhum destes chama {@code removeEffect}: isso dispararia este mesmo
     * evento de novo.
     */
    private static void onEffectEnded(LivingEntity entity, @Nullable MobEffect effect) {
        if (effect == null || entity.level().isClientSide) return;
        if (effect == MagiaEffects.DOMINATED.get() && entity instanceof Mob mob) {
            Domination.release(mob);
        } else if (effect == MagiaEffects.BOUND.get()) {
            Binding.release(entity);
        } else if (effect == MagiaEffects.MOMENTUM.get()) {
            Momentum.forget(entity);
        } else if (effect == MagiaEffects.KNEELING.get() && entity instanceof ServerPlayer player) {
            EmotecraftCompat.stop(player);
        } else if (effect == MagiaEffects.SILENCED.get()) {
            VoiceMute.unmute(entity.getUUID());
        } else if (effect == MagiaEffects.IRON_BOUND.get()) {
            IronBinding.release(entity.getUUID());
        }
    }
}
