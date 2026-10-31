package com.aurorion.ficha.client;

import com.aurorion.ficha.AurorionFicha;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code /ficha teste}: as telas e o HUD reais com dados locais, sem servidor nem site.
 *
 * <pre>
 * /ficha teste             a ficha fictícia (e, 6 s depois, um pedido do mestre no HUD)
 * /ficha teste alt         um alt sem ficha: aviso no HUD e o convite (PROMPT) abrindo o Vínculo sozinho
 * /ficha teste altlogin    o mesmo convite 3 s depois: com outra tela aberta, espera ela fechar
 * /ficha teste mudou       a tela aberta recebe a ficha nova sem OPEN ("A ficha mudou no site…")
 * /ficha teste recusado    vincular o Capitão Ordrik volta forbidden e a lista nova chega sem ele
 * /ficha teste semficha    a próxima rolagem volta needs_binding e a tela vai para o Vínculo
 * /ficha teste ocupado    a próxima rolagem volta busy (o site segurou): aviso de espera e repetir
 * /ficha teste auth|missing erros de configuração do servidor ("avise a staff")
 * /ficha teste cancelado   um pedido no HUD que o mestre cancela 4 s depois
 * /ficha teste dados       um pedido de dados no HUD ("Rolar 2d6", CD comparada ao total)
 * /ficha teste aprovacao   a própria ficha com a construção aguardando aprovação no site
 * /ficha teste antiga      ficha que o jogador ainda não atualizou no site (botão "Atualizar no site")
 * /ficha teste vazio       ficha sem condições, técnicas nem história; Vínculo sem candidatos
 * /ficha teste erro        conta do site não vinculada (a explicação no lugar da ficha)
 * /ficha teste mestre      a mesa do mestre com jogadores inventados
 * /ficha teste pedido      o pedido do mestre no HUD (de novo: o segundo, com CD oculta)
 * /ficha teste roda        a roda da ficha (segurar a tecla) de um jogador, sem pedido: Ficha, Dados, Habilidades
 * /ficha teste rodapedido  a mesma roda com um pedido do mestre: o cartão no miolo, Rolar e Recusar
 * /ficha teste rodamestre  a roda do mestre (com a Mesa do mestre), sem pedido
 * /ficha teste rodamestrepedido  a roda do mestre com um pedido de outro mestre: as seis fatias
 * /ficha teste pedidos     três pedidos de uma vez ("+2 pedidos"), o do topo na reta final
 * /ficha teste prazo       um pedido com 24 s: a contagem fica âmbar e ele sai sozinho
 * /ficha teste hud         cartões de exemplo de todos os tipos, em sequência
 * /ficha teste critico     natural 20 (seu e de um vizinho)
 * /ficha teste falha       natural 1 e uma falha comum contra a CD
 * /ficha teste vantagem    vantagem e depois desvantagem: os dois d20, o descartado riscado
 * /ficha teste pilha       oito cartões em um segundo: empilhar, limite e a barra de itens
 * /ficha teste aviso       só o aviso de alt sem ficha no HUD
 * /ficha teste avisoantiga o aviso de ficha antiga do login da conta principal (com a tecla configurada)
 * /ficha teste carregando  o "Abrindo a ficha…" do HUD e a ficha chegando depois
 * /ficha teste sitefora    o "Abrindo…" e o erro de site fora na barra de ação, sem tela
 * </pre>
 *
 * <h2>Não sombrear o {@code /ficha} do servidor</h2>
 * O {@code ClientCommandHandler} do NeoForge tenta o comando aqui primeiro e só o passa ao servidor
 * quando o despachante do cliente responde "comando desconhecido" ou "argumento desconhecido". Por
 * isso o nó {@code ficha} daqui <b>não tem {@code executes}</b> nem argumento próprio: {@code /ficha}
 * sozinho e {@code /ficha rolar …} não casam com nada no cliente e seguem para o servidor. Só o ramo
 * {@code teste} é daqui — inclusive {@code /ficha teste qualquer-coisa}, que lista as variantes em vez
 * de ir ao servidor (que não conhece {@code teste}).
 */
@EventBusSubscriber(modid = AurorionFicha.MOD_ID, value = Dist.CLIENT)
public final class FichaTestCommand {
    /** Variante → o que faz, na ordem da ajuda. */
    private static final Map<String, Runnable> VARIANTS = new LinkedHashMap<>();
    private static final Map<String, String> HELP = new LinkedHashMap<>();

    static {
        variant("alt", FichaTestSession::openAlt, "alt sem ficha: aviso e a ficha abrindo sozinha no Vínculo");
        variant("altlogin", FichaTestSession::openAltLater, "o mesmo convite em 3 s: com o inventário aberto, espera ele fechar");
        variant("mudou", FichaTestSession::openChanged, "tela aberta recebendo a ficha nova (construção aprovada)");
        variant("recusado", FichaTestSession::openRefused, "vincular recusado pelo site e a lista nova chegando");
        variant("semficha", FichaTestSession::openLostBinding, "rolagem que volta needs_binding e leva ao Vínculo");
        variant("ocupado", FichaTestSession::openBusy, "rolagem que o site segura (busy): \"ocupado\" e repetir");
        variant("auth", () -> FichaTestSession.openSetupError("auth"), "erro de credencial do servidor (avise a staff)");
        variant("missing", () -> FichaTestSession.openSetupError("missing"), "site sem a ficha do jogo ativada (avise a staff)");
        variant("cancelado", FichaTestSession::fakeCancelledRequest, "pedido no HUD que o mestre cancela");
        variant("dados", FichaTestSession::fakeDiceRequest, "pedido de dados do mestre no HUD (Rolar 2d6, CD no total)");
        variant("aprovacao", FichaTestSession::openPending, "construção aguardando aprovação");
        variant("antiga", FichaTestSession::openOutdated, "ficha ainda não atualizada no site");
        variant("vazio", FichaTestSession::openEmpty, "ficha mínima, Vínculo sem candidatos");
        variant("erro", FichaTestSession::openError, "conta do site não vinculada");
        variant("mestre", FichaTestSession::openTable, "mesa do mestre");
        variant("pedido", FichaTestSession::fakeRequest, "pedido do mestre no HUD (responder segurando a tecla da ficha)");
        variant("roda", () -> FichaTestSession.openWheel(false, false), "a roda da ficha de um jogador, sem pedido");
        variant("rodapedido", () -> FichaTestSession.openWheel(false, true), "a roda com um pedido do mestre: cartão no miolo, Rolar/Recusar");
        variant("rodamestre", () -> FichaTestSession.openWheel(true, false), "a roda do mestre (com a Mesa), sem pedido");
        variant("rodamestrepedido", () -> FichaTestSession.openWheel(true, true), "a roda do mestre com um pedido: as seis fatias");
        variant("pedidos", FichaTestSession::fakeRequests, "três pedidos de uma vez, o do topo na reta final");
        variant("prazo", FichaTestSession::fakeUrgentRequest, "pedido vencendo: contagem âmbar e saída");
        variant("hud", FichaTestSession::demoHud, "todos os tipos de cartão, em sequência");
        variant("critico", FichaTestSession::demoCritical, "natural 20");
        variant("falha", FichaTestSession::demoFumble, "natural 1 e falha contra a CD");
        variant("vantagem", FichaTestSession::demoAdvantage, "vantagem e desvantagem (dois d20)");
        variant("pilha", FichaTestSession::demoStack, "oito cartões de uma vez");
        variant("aviso", FichaTestSession::demoNotice, "aviso de alt sem ficha, sem abrir tela");
        variant("avisoantiga", FichaTestSession::demoOutdatedNotice, "aviso de ficha antiga do login (com a tecla da ficha)");
        variant("carregando", FichaTestSession::demoLoading, "\"Abrindo a ficha…\" e a ficha chegando");
        variant("sitefora", FichaTestSession::demoSiteDown, "erro de site fora sem tela aberta");
    }

    @Nullable
    private static Runnable pending;

    private FichaTestCommand() {
    }

    private static void variant(String name, Runnable action, String help) {
        VARIANTS.put(name, action);
        HELP.put(name, help);
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> teste = Commands.literal("teste")
                .executes(context -> queue(FichaTestSession::openSheet));
        VARIANTS.forEach((name, action) -> teste.then(Commands.literal(name).executes(context -> queue(action))));
        // Literal casa antes de argumento no Brigadier: este só pega o que não é variante conhecida.
        teste.then(Commands.argument("variante", StringArgumentType.greedyString()).executes(context -> {
            help(StringArgumentType.getString(context, "variante"));
            return 0;
        }));
        // Sem executes no "ficha": ver o javadoc (o /ficha do servidor não pode ser sombreado).
        event.getDispatcher().register(Commands.literal("ficha").then(teste));
    }

    private static int queue(Runnable action) {
        // O chat fecha a própria tela ao concluir o comando; a nossa só abre no próximo tick.
        pending = action;
        return 1;
    }

    /** Variante desconhecida: a lista clicável no chat. */
    private static void help(String unknown) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        minecraft.player.displayClientMessage(Component.literal("Não existe /ficha teste " + unknown.strip()
                + ". Variantes (clique para rodar):").withStyle(ChatFormatting.GOLD), false);
        MutableComponent line = Component.literal("");
        boolean first = true;
        for (Map.Entry<String, String> e : HELP.entrySet()) {
            if (!first) line.append(Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY));
            first = false;
            line.append(Component.literal(e.getKey()).withStyle(style -> style.withColor(ChatFormatting.YELLOW)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ficha teste " + e.getKey()))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(e.getValue())))));
        }
        minecraft.player.displayClientMessage(line, false);
    }

    /** Desconectou: um comando enfileirado não roda no próximo mundo. */
    static void clear() {
        pending = null;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        FichaTestSession.tick();
        if (pending == null) return;
        Runnable action = pending;
        pending = null;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.player != null) action.run();
    }
}
