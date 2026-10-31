package com.aurorion.intencao.client;

import com.aurorion.intencao.AurorionIntencao;
import com.aurorion.intencao.network.DuelPayloads;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import com.aurorion.core.client.menu.CharacterMenu;
import org.jetbrains.annotations.Nullable;

/**
 * O duelo do lado do cliente: guarda o último estado e a vez, abre o menu em Ações, e transforma o clique
 * na janela do golpe em reação — esquerdo apara, direito esquiva — medindo o desvio em relação ao instante
 * do impacto. Durante a janela o clique não ataca nem usa nada no mundo.
 */
@EventBusSubscriber(modid = AurorionIntencao.MOD_ID, value = Dist.CLIENT)
public final class ClientDuel {
    @Nullable static DuelPayloads.State state;
    @Nullable static DuelPayloads.Prompt prompt;
    /** Quando o prompt chegou (ms), para o relógio da tela. */
    static long promptAt;
    @Nullable static DuelPayloads.React react;
    /** Instante do impacto do golpe (ms do relógio do cliente). */
    static long impactAt;
    static long reactShownAt;
    static boolean reacted;
    /** Resultado do último clique, só para o anel piscar (0 nada, 1 aparo, 2 esquiva). */
    static int lastReaction;
    /** Quando chegou o resultado próprio: o aviso de fim da arena (para a plateia) não repete para quem lutou. */
    private static long resultAt;

    private ClientDuel() {
    }

    static boolean active() {
        return state != null && !state.fighters().isEmpty();
    }

    static boolean myTurn() {
        return state != null && state.phase() == 1 && state.actor() == state.self() && prompt != null;
    }

    public static void accept(CustomPacketPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (payload instanceof DuelPayloads.State next) {
            boolean was = state != null;
            state = next.fighters().isEmpty() ? null : next;
            CharacterMenu.refresh();
            // O duelo começou (aceito pelo chat ou pela janela): o convite na frente da pessoa some.
            if (!was && state != null) CharacterMenu.dismissPrompt();
            if (state == null || state.phase() != 1 || state.actor() != state.self()) {
                // A sua vez acabou (escolheu, ou o prazo passou): o menu aberto para ela fecha.
                boolean hadTurn = prompt != null;
                prompt = null;
                if (hadTurn) CharacterMenu.refresh();
            }
            if (state == null || state.phase() != 2) react = null;
        } else if (payload instanceof DuelPayloads.Prompt next) {
            prompt = next;
            promptAt = Util.getMillis();
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.4f));
            // A sua vez: o menu abre já em Ações (sem o aurorion-menu, só o som e o HUD avisam).
            if (minecraft.screen == null) CharacterMenu.open(DuelSections.ACTIONS);
        } else if (payload instanceof DuelPayloads.React next) {
            react = next;
            reactShownAt = Util.getMillis();
            impactAt = reactShownAt + next.ticks() * 50L;
            reacted = false;
            lastReaction = 0;
            CharacterMenu.close();
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.TRIDENT_RIPTIDE_1.value(), 1.2f));
        } else if (payload instanceof DuelPayloads.Invite invite) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.2f));
            // No meio da visão, para responder apontando; com o chat aberto também (responde ao fechar).
            CharacterMenu.prompt(DuelSections.invite(invite));
        } else if (payload instanceof DuelPayloads.Bolt bolt) {
            ClientArena.bolt(bolt);
        } else if (payload instanceof DuelPayloads.Arena arena) {
            ClientArena.accept(arena);
            // A plateia vê o fim no cartão; quem lutou já recebeu o resultado completo.
            if (!arena.open() && !arena.headline().isEmpty() && Util.getMillis() - resultAt > 3000) {
                CharacterMenu.notice(new CharacterMenu.Notice("DUELO ENCERRADO", arena.headline(), java.util.List.of(), null, 6));
            }
        } else if (payload instanceof DuelPayloads.Result result) {
            resultAt = Util.getMillis();
            String kicker = result.outcome() == 1 ? "VITÓRIA" : result.outcome() == 2 ? "DERROTA" : "EMPATE";
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(result.outcome() == 1
                    ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.NOTE_BLOCK_BASS.value(), result.outcome() == 1 ? 1f : 0.7f));
            boolean shown = CharacterMenu.notice(new CharacterMenu.Notice(kicker, result.headline(), result.rows(), null, 12));
            if (!shown) {
                minecraft.gui.setTimes(5, 60, 15);
                minecraft.gui.setSubtitle(net.minecraft.network.chat.Component.literal(result.headline()));
                minecraft.gui.setTitle(net.minecraft.network.chat.Component.literal(kicker));
            }
        }
    }

    static void choose(int kind, int index, int target) {
        PacketDistributor.sendToServer(new DuelPayloads.Input(0, kind, index, target));
        prompt = null;
    }

    /** Clique na janela do golpe: aparo (esquerdo) ou esquiva (direito). Só o primeiro conta. */
    @SubscribeEvent
    public static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
        DuelPayloads.React current = react;
        if (current == null || !active()) return;
        long now = Util.getMillis();
        // Muito antes de o anel começar a fechar não é reação: deixa o clique passar.
        if (now < impactAt - current.ticks() * 50L - 200) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        if (reacted) return;
        int kind = event.isAttack() ? 0 : event.isUseItem() ? 1 : -1;
        if (kind < 0) return;
        reacted = true;
        lastReaction = kind + 1;
        PacketDistributor.sendToServer(new DuelPayloads.Input(1, kind, (int) (now - impactAt), 0));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                kind == 0 ? SoundEvents.SHIELD_BLOCK : SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f));
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (react != null && Util.getMillis() > impactAt + 1500) react = null;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        state = null;
        prompt = null;
        react = null;
    }
}
