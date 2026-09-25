package com.aurorion.magia.event;

import com.aurorion.core.character.CharacterResetEvent;
import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.compat.EmotecraftCompat;
import com.aurorion.magia.compat.FrozenLink;
import com.aurorion.magia.compat.VoiceMute;
import com.aurorion.magia.effect.EffectCleanup;
import com.aurorion.magia.passive.DreadAura;
import com.aurorion.magia.passive.HealingTouch;
import com.aurorion.magia.passive.Passive;
import com.aurorion.magia.passive.PassiveData;
import com.aurorion.magia.passive.Passives;
import com.aurorion.magia.registry.MagiaEffects;
import com.aurorion.magia.spell.Binding;
import com.aurorion.magia.spell.Domination;
import com.aurorion.magia.spell.Drowning;
import com.aurorion.magia.spell.Gaze;
import com.aurorion.magia.spell.IronBinding;
import com.aurorion.magia.spell.Seals;
import com.aurorion.magia.spell.WaterCage;
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
import net.minecraft.server.TickTask;
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
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
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
        // Sob tortura nao se conjura: toda magia e voz firme e mao parada, e a pessoa nao tem
        // nenhuma das duas. Vale para o Dolor Cruciatus e para o Tormento Coletivo, que usam o
        // mesmo efeito.
        if (player.hasEffect(MagiaEffects.CRUCIATUS)) {
            deny(event, player, Component.translatable("aurorion_magia.dor_demais").withStyle(ChatFormatting.DARK_RED));
            return;
        }
        // De joelhos tambem nao: a Prostracao tira as maos, e nao so as pernas.
        if (player.hasEffect(MagiaEffects.KNEELING)) {
            deny(event, player, Component.translatable("aurorion_magia.de_joelhos").withStyle(ChatFormatting.LIGHT_PURPLE));
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
        EffectCleanup.sweep(player);
        Passives.onLogin(player);
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) Seals.sendAll(player);
    }

    /**
     * Renascer perde todo efeito, inclusive o marcador da aura. Quem morreu com a Presenca
     * Aterradora ligada volta com ela ligada — a passiva e do personagem, e nao da vida dele.
     */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Seals.sendAll(player);
            EffectCleanup.sweep(player);
            Passives.onLogin(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        IronBinding.release(event.getEntity().getUUID());
    }

    /**
     * Aula e do personagem, nao da conta: personagem novo nao sabe magia nenhuma — e nao herda marca
     * nenhuma. A aura sai junto com o cadastro; deixa-la ligada num personagem que nem existe mais
     * seria o unico estado do mod capaz de sobreviver a morte definitiva.
     */
    @SubscribeEvent
    public static void onCharacterReset(CharacterResetEvent event) {
        SpellUnlockData.get(event.server()).clear(event.account());
        PassiveData.get(event.server()).clear(event.account());
        ServerPlayer player = event.player();
        if (player != null) {
            SpellAccess.reconcile(player);
            Passives.onLogin(player);
        }
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
     *
     * <p><b>{@code HIGHEST}, e nao {@code HIGH}</b>: o Carry On pega bau e mochila do chao neste mesmo
     * evento, tambem em {@code HIGH}, e entre dois listeners de mesma prioridade a ordem e a de
     * registro — ou seja, sorte. Em {@code HIGHEST} cancelamos antes, e sair carregando o bau lacrado
     * deixa de ser a brecha obvia do Lacre Profano.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
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
        // Maos atadas: porta e botao continuam funcionando; so o item na mao nao e usado no bloco.
        if (handsBound(event.getEntity())) event.setUseItem(TriState.FALSE);
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

    // --- Maos atadas: sem item ----------------------------------------------------------------
    // Estes rodam nos dois lados de proposito. O efeito ja esta sincronizado com o cliente do
    // afetado, entao cancelar la tambem evita a animacao fantasma de "comecou a comer e parou".

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (!handsBound(event.getEntity())) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent
    public static void onStartUsing(LivingEntityUseItemEvent.Start event) {
        if (handsBound(event.getEntity())) event.setCanceled(true);
    }

    /**
     * Os tres estados em que as maos nao respondem: mente dominada (Imperium), dor que paralisa
     * (Cruciatus e Tormento Coletivo) e joelhos no chao (Prostracao). Nenhum item e usado, nem comida,
     * nem arco, nem escudo, nem totem — e o gate de conjuracao ja recusa magia nos tres.
     *
     * <p>Botao, alavanca e porta continuam funcionando: quem trava isso e {@code onUseBlock}, que so
     * desliga o <i>item</i> na mao.
     */
    private static boolean handsBound(LivingEntity entity) {
        return entity instanceof Player player
                && (player.hasEffect(MagiaEffects.DISORIENTED)
                || player.hasEffect(MagiaEffects.CRUCIATUS)
                || player.hasEffect(MagiaEffects.KNEELING));
    }

    // --- Dominio -----------------------------------------------------------------------------

    /** Dispara so quando algum mob troca de alvo; para quem nao esta dominado, uma consulta de efeito. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getEntity().level().isClientSide || !(event.getEntity() instanceof Mob mob)) return;
        if (!Domination.allowsTargetChange(mob, event.getNewAboutToBeSetTarget())) {
            event.setCanceled(true);
            return;
        }
        // Bicho apavorado nao ataca quem o apavorou. A consulta de passiva so acontece depois da
        // consulta de efeito, entao ela nao pesa em mob nenhum que nao esteja dentro de uma aura.
        if (mob.hasEffect(MagiaEffects.TERRIFIED)
                && event.getNewAboutToBeSetTarget() instanceof ServerPlayer target
                && Passives.isActive(target, Passive.DREAD)) {
            event.setCanceled(true);
        }
    }

    /**
     * O ponto em que o dano recebido pode ser cancelado antes de ser calculado. Dois casos, nesta
     * ordem — o primeiro que decidir, decide:
     *
     * <ol>
     *   <li><b>Dominio</b>: dano em area ou projetil perdido de um dominado nao fere o mestre nem os
     *       aliados dele.</li>
     *   <li><b>Carcere de Agua</b>: bater na bolha a estoura, e a agua absorve o golpe. E o resgate da
     *       magia — quem foi capturado pode ser tirado de la por qualquer um.</li>
     * </ol>
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        Entity attacker = event.getSource().getEntity();
        if (attacker != null && Domination.isHarmingProtected(attacker, victim)) {
            event.setCanceled(true);
            return;
        }
        if (WaterCage.isCaged(victim) && WaterCage.breaksOn(victim, event.getSource())) {
            WaterCage.burst(victim);
            event.setCanceled(true);
            return;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDamagePre(LivingDamageEvent.Pre event) {
        HealingTouch.intercept(event);
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
        } else if (effect == MagiaEffects.KNEELING.get() && entity instanceof ServerPlayer player) {
            EmotecraftCompat.stop(player);
        } else if (effect == MagiaEffects.SILENCED.get()) {
            VoiceMute.unmute(entity.getUUID());
        } else if (effect == MagiaEffects.IRON_BOUND.get()) {
            IronBinding.release(entity.getUUID());
        } else if (effect == MagiaEffects.CAPTIVE.get()) {
            Gaze.release(entity);
        } else if (effect == MagiaEffects.DROWNING.get()) {
            Drowning.release(entity);
            // Sair do afogamento e sair da agua: o pulmao volta cheio, e nao no fundo do poço.
            entity.setAirSupply(entity.getMaxAirSupply());
        } else if (effect == MagiaEffects.CAGED.get()) {
            WaterCage.release(entity);
        } else if (effect == MagiaEffects.GENUFLECTED.get() && entity instanceof ServerPlayer player) {
            EmotecraftCompat.stop(player);
        } else if (effect == MagiaEffects.DREAD_AURA.get() && entity instanceof ServerPlayer player) {
            // A remocao pode ocorrer durante o shutdown. So a passiva ainda ativa precisa voltar;
            // tell enfileira sem executar inline dentro do proprio evento de remocao.
            if (Passives.isActive(player, Passive.DREAD)) {
                player.server.tell(new TickTask(player.server.getTickCount(), () -> {
                    if (player.isAlive() && player.server.getPlayerList().getPlayer(player.getUUID()) == player
                            && Passives.isActive(player, Passive.DREAD)
                            && !player.hasEffect(MagiaEffects.DREAD_AURA)) DreadAura.enable(player);
                }));
            }
        }
    }
}
