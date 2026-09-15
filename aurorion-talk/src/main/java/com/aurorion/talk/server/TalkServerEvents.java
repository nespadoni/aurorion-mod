package com.aurorion.talk.server;

import com.aurorion.talk.AurorionTalk;
import com.aurorion.talk.config.TalkServerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = AurorionTalk.MOD_ID)
public final class TalkServerEvents {
    private TalkServerEvents() {
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            StyleManager.onPlayerJoin(player);
        }
    }

    /**
     * O sussurro e a saida de emergencia do balao, e por isso ele fecha aqui.
     *
     * <p>Esconder a fala do HUD e decisao do cliente (o {@code ChatComponentMixin}), e cliente e
     * opcional: quem entra sem o mod nunca leu aquela config. Enquanto {@code /w} existir, falar
     * sem aparecer acima da propria cabeca continua sendo uma tecla de distancia — e a regra que
     * sustenta o mod inteiro vira sugestao. Recusar o comando no servidor e o unico lugar em que
     * ela vale para todo mundo igual.
     *
     * <p>Fonte que nao e jogador passa direto: console, bloco de comando e funcao de datapack
     * precisam do {@code /msg} para avisar uma pessoa so, e nenhum deles esta fugindo de balao.
     * A staff passa pelo nivel de permissao — moderar as vezes exige falar reservado.
     *
     * <p>Olhamos so a raiz digitada, entao {@code /execute run msg ...} escapa. E de proposito:
     * {@code /execute} ja exige nivel 2, o mesmo do {@code bypassPermissionLevel} padrao, entao
     * quem consegue chegar la por esse caminho e exatamente quem tem licenca para sussurrar.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onCommand(CommandEvent event) {
        if (!(event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer player)) return;

        var nodes = event.getParseResults().getContext().getNodes();
        if (nodes.isEmpty()) return;

        if (!PrivateChatGate.denies(player, nodes.get(0).getNode().getName())) return;

        event.setCanceled(true);

        // Cancelar um CommandEvent nao devolve nada ao jogador — sem esta linha, o comando some sem
        // resposta e parece queda de servidor. Vai para o chat, e nao para a actionbar como a
        // negativa de portal: aqui e uma resposta unica a uma linha que a pessoa digitou, no mesmo
        // lugar em que o vanilla responderia, e nao um aviso que se repete a cada tick.
        String notice = TalkServerConfig.DENY_MESSAGE.get();
        if (!notice.isBlank()) {
            player.sendSystemMessage(Component.literal(notice).withStyle(ChatFormatting.GRAY));
        }
    }
}
