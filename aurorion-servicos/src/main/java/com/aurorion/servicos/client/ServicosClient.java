package com.aurorion.servicos.client;

import com.aurorion.servicos.AurorionServicos;
import com.aurorion.servicos.network.ServicosActionPayload;
import com.aurorion.servicos.network.ServicosNotifyPayload;
import com.aurorion.servicos.network.ServicosPagePayload;
import com.aurorion.servicos.network.ServicosQueryPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * O lado do cliente do app: abre a tela, fala com o servidor e recebe as respostas.
 *
 * <p>Classe de cliente: so e citada pelo {@code ServicosNetwork} dentro do teste de {@code Dist}.
 */
@EventBusSubscriber(modid = AurorionServicos.MOD_ID, value = Dist.CLIENT)
public final class ServicosClient {
    public static final String APP_ID = "servicos";
    /** O nome do app nas notificacoes do celular; e por ele que o clique na notificacao volta para ca. */
    public static final String APP_NAME = "Serviços";
    public static final String SEARCH_TERMS = "servicos servico vagas vaga pedidos trabalho emprego profissoes ifood";
    public static final int APP_COLOR = 0xFFF97316;
    public static final ResourceLocation ICON =
            ResourceLocation.fromNamespaceAndPath(AurorionServicos.MOD_ID, "textures/gui/phone/servicos.png");

    private static int badge;

    private ServicosClient() {
    }

    /** Pedidos esperando resposta, para o numerinho no icone. */
    public static int badge() {
        return badge;
    }

    public static void open(String tab, String filter) {
        Minecraft.getInstance().setScreen(new ServicosScreen(tab, filter));
    }

    static void query(String tab, String filter) {
        if (Minecraft.getInstance().getConnection() == null) return;
        PacketDistributor.sendToServer(new ServicosQueryPayload(tab, filter));
    }

    static void action(String action, long id, String category, String title, String text, String price,
                       String tab, String filter) {
        if (Minecraft.getInstance().getConnection() == null) return;
        PacketDistributor.sendToServer(new ServicosActionPayload(action, id, category, title, text, price, tab, filter));
    }

    public static void receive(ServicosPagePayload page) {
        badge = page.badge();
        Minecraft minecraft = Minecraft.getInstance();
        if (!page.chatNick().isEmpty() && minecraft.screen instanceof ServicosScreen) {
            // A acao terminou numa conversa: o celular vai direto para ela, e o resultado fica no aviso.
            if (PhoneBridge.openChat(page.chatNick(), page.chatDraft())) {
                if (!page.message().isEmpty() && minecraft.player != null) {
                    minecraft.player.displayClientMessage(Component.literal("☎ " + page.message()), true);
                }
                return;
            }
        }
        if (minecraft.screen instanceof ServicosScreen screen) screen.accept(page);
    }

    public static void notify(ServicosNotifyPayload payload) {
        if (payload.badge() >= 0) badge = payload.badge();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof ServicosScreen screen) screen.refresh();
        // Sem o celular no bolso, nao ha como saber do pedido: o aviso e do aparelho.
        if (!PhoneBridge.hasPhone()) return;
        PhoneBridge.notification(APP_NAME, payload.title(), payload.text());
        if (minecraft.player != null && !(minecraft.screen instanceof ServicosScreen)) {
            minecraft.player.displayClientMessage(Component.literal("☎ " + APP_NAME + ": " + payload.title()), true);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        badge = 0;
    }
}
