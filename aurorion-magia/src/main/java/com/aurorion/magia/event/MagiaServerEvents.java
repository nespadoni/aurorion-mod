package com.aurorion.magia.event;

import com.aurorion.core.character.CharacterResetEvent;
import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.registry.MagiaEffects;
import com.aurorion.magia.spell.Domination;
import com.aurorion.magia.unlock.SpellAccess;
import com.aurorion.magia.unlock.SpellUnlockData;
import io.redspace.ironsspellbooks.api.events.InscribeSpellEvent;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Todos os pontos em que o servidor diz "nao". Tudo aqui e reacao a evento, disparado por uma acao
 * de alguem: nenhum listener de tick, nenhuma varredura de jogadores.
 */
@EventBusSubscriber(modid = AurorionMagia.MOD_ID)
public final class MagiaServerEvents {
    private MagiaServerEvents() {
    }

    // --- Liberacao ---------------------------------------------------------------------------

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

        if (player.hasEffect(MagiaEffects.DISORIENTED)) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("aurorion_magia.mente_turva")
                    .withStyle(ChatFormatting.DARK_AQUA), true);
            return;
        }

        AbstractSpell spell = SpellRegistry.getSpell(event.getSpellId());
        if (!SpellAccess.canCast(player, spell)) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("aurorion_magia.nao_liberada",
                    spell.getDisplayName(player)).withStyle(ChatFormatting.RED), true);
        }
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

    /**
     * Espelha a lista oficial no Iron's a cada entrada. {@code LOWEST} para rodar depois do login do
     * Iron's Restrictions, que ensina as {@code DefaultLearntSpells} dele — com
     * {@code authoritative}, o que ele ensinou por fora e esquecido aqui mesmo.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SpellAccess.reconcile(player);
    }

    /** Aula e do personagem, nao da conta: personagem novo nao sabe magia nenhuma. */
    @SubscribeEvent
    public static void onCharacterReset(CharacterResetEvent event) {
        SpellUnlockData.get(event.server()).clear(event.account());
        ServerPlayer player = event.player();
        if (player != null) SpellAccess.reconcile(player);
    }

    // --- Desorientacao: sem item -------------------------------------------------------------
    // Estes rodam nos dois lados de proposito. O efeito ja esta sincronizado com o cliente do
    // afetado, entao cancelar la tambem evita a animacao fantasma de "comecou a comer e parou".

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (!isDisoriented(event.getEntity())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    /** Porta e botao continuam funcionando; so o item na mao nao e usado no bloco. */
    @SubscribeEvent
    public static void onUseItemOnBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isDisoriented(event.getEntity())) event.setUseItem(TriState.FALSE);
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

    @SubscribeEvent
    public static void onEffectExpired(MobEffectEvent.Expired event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (instance != null) releaseIfDominated(event.getEntity(), instance.getEffect().value());
    }

    /** Leite, {@code /effect clear}, totem: o dominio acaba e o mob volta a pensar sozinho. */
    @SubscribeEvent
    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        releaseIfDominated(event.getEntity(), event.getEffect().value());
    }

    private static void releaseIfDominated(LivingEntity entity, @Nullable MobEffect effect) {
        if (effect == MagiaEffects.DOMINATED.get() && !entity.level().isClientSide && entity instanceof Mob mob) {
            Domination.release(mob);
        }
    }
}
