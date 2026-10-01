package com.aurorion.integracao.link;

import com.aurorion.core.integration.GameFacts;
import com.aurorion.integracao.AurorionIntegracao;
import com.aurorion.integracao.bridge.FactBridge;
import com.aurorion.integracao.outbox.SiteApi;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /vincular CODIGO}: liga a conta do site (Discord) a este perfil Minecraft.
 *
 * <p>O código nasce no site, com a pessoa logada, e vale minutos. Aqui o servidor só repassa o
 * código junto com o perfil da sessão — quem decide é o backend, que limita tentativas por perfil.
 * A chamada é assíncrona; a resposta volta para a thread do servidor antes de falar com o jogador.
 *
 * <p>Mensagens literais em português: este mod roda só no servidor, e o cliente não tem os arquivos
 * de idioma dele.
 */
@EventBusSubscriber(modid = AurorionIntegracao.MOD_ID)
public final class VincularCommand {
    private static final long COOLDOWN_MS = 5_000L;
    private static final Map<UUID, Long> LAST_TRY = new HashMap<>();

    private VincularCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("vincular")
                .executes(context -> help(context.getSource()))
                .then(Commands.argument("codigo", StringArgumentType.greedyString())
                        .executes(VincularCommand::claim)));
    }

    private static int help(CommandSourceStack source) {
        source.sendSystemMessage(info("No site, abra Perfil → Diário e clique em \"Vincular minha conta Minecraft\". "
                + "Depois digite aqui /vincular e o código que aparecer."));
        return 1;
    }

    private static int claim(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return 0;
        Optional<SiteApi> site = FactBridge.site();
        if (site.isEmpty()) {
            player.sendSystemMessage(error("A integração com o site está desligada neste servidor."));
            return 0;
        }
        long now = System.currentTimeMillis();
        Long last = LAST_TRY.get(player.getUUID());
        if (last != null && now - last < COOLDOWN_MS) {
            player.sendSystemMessage(error("Aguarde alguns segundos antes de tentar de novo."));
            return 0;
        }
        LAST_TRY.put(player.getUUID(), now);

        String code = StringArgumentType.getString(context, "codigo");
        GameFacts.Subject subject = GameFacts.subject(player.server, player.getUUID());
        JsonObject body = new JsonObject();
        body.addProperty("code", code.length() > 32 ? code.substring(0, 32) : code);
        body.addProperty("profile_uuid", player.getUUID().toString());
        body.addProperty("profile_name", player.getGameProfile().getName());
        if (subject.character() != null) {
            body.addProperty("character_id", subject.character().toString());
            body.addProperty("character_name", subject.characterName());
        }

        player.sendSystemMessage(info("Conferindo o código com o site…"));
        MinecraftServer server = player.server;
        UUID playerId = player.getUUID();
        site.get().post("/link/claim", body).thenAccept(response -> server.execute(() -> {
            ServerPlayer online = server.getPlayerList().getPlayer(playerId);
            if (online != null) online.sendSystemMessage(result(response));
        }));
        return 1;
    }

    private static Component result(SiteApi.Response response) {
        return switch (response.code()) {
            case "linked" -> {
                String account = response.body() != null && response.body().has("account") ? response.body().get("account").getAsString() : "";
                yield Component.literal("✔ Conta vinculada" + (account.isEmpty() ? "" : " a " + account) + ". Agora você pode escrever com /diario.")
                        .withStyle(ChatFormatting.GREEN);
            }
            case "invalid_code" -> error("Código inválido ou vencido. Gere outro no site, em Perfil → Diário.");
            case "profile_taken" -> error("Este perfil Minecraft já está vinculado a outra conta do site. Desvincule por lá antes.");
            case "too_many_attempts" -> error("Tentativas demais. Gere um código novo no site e aguarde alguns minutos.");
            default -> error("O site não respondeu agora. Tente de novo em instantes.");
        };
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_TRY.remove(event.getEntity().getUUID());
    }

    private static Component info(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }

    private static Component error(String text) {
        return Component.literal(text).withStyle(ChatFormatting.RED);
    }
}
