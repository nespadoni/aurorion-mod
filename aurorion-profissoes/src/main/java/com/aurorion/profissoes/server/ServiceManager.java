package com.aurorion.profissoes.server;

import com.aurorion.profissoes.AurorionProfissoes;
import com.aurorion.profissoes.api.*;
import com.aurorion.profissoes.config.ProfessionsConfig;
import com.aurorion.profissoes.data.Profession;
import com.aurorion.profissoes.network.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

public final class ServiceManager {
    public static final UUID ZERO = new UUID(0, 0);
    private record View(UUID professional, UUID customer, List<String> actions, ItemStack subject, ItemStack supplies) {}
    private record Request(View view, String action) {}
    private static final SessionBook<View> VIEWS = new SessionBook<>();
    private static final SessionBook<Request> REQUESTS = new SessionBook<>();
    private static final Map<UUID, Long> LAST_OPEN = new HashMap<>();
    private ServiceManager() {}
    public static void open(ServerPlayer customer, ServerPlayer professional) {
        long now = System.currentTimeMillis();
        if (now - LAST_OPEN.getOrDefault(customer.getUUID(), 0L) < 500) return;
        LAST_OPEN.put(customer.getUUID(), now);
        if (!validPair(professional, customer)) { status(customer, "Atendimento indisponível", "Aproxime-se do profissional para conversar."); return; }
        if (REQUESTS.busy(customer.getUUID(), now)) {
            var entry = REQUESTS.peek(customer.getUUID(), now);
            var requester = customer.server.getPlayerList().getPlayer(entry.value().view.customer);
            try {
                if (!validPair(customer, requester) || !unchanged(entry.value().view, customer, requester))
                    throw new ServiceActions.Refusal("O pedido mudou. Solicite novamente.");
                showApproval(customer, requester, entry.token(), ServiceActions.plan(customer, requester, entry.value().action));
            } catch (ServiceActions.Refusal refusal) {
                REQUESTS.take(customer.getUUID(), entry.token(), now);
                status(customer, "Pedido encerrado", refusal.getMessage());
            }
            return;
        }
        var profession = ProfessionApi.of(professional);
        var actions = ServiceActions.actions(profession, customer);
        var rows = new ArrayList<PanelPayload.Row>();
        for (String action : actions) {
            try {
                var plan = ServiceActions.plan(professional, customer, action);
                rows.add(new PanelPayload.Row(action, plan.title(), plan.detail(), true));
            } catch (ServiceActions.Refusal refusal) {
                rows.add(new PanelPayload.Row(action, ServiceActions.title(action), refusal.getMessage(), false));
            }
        }
        var view = new View(professional.getUUID(), customer.getUUID(), List.copyOf(actions), customer.getMainHandItem().copy(), professional.getOffhandItem().copy());
        UUID token = VIEWS.put(customer.getUUID(), view, now);
        String subtitle = profession == Profession.NONE ? "A staff ainda não atribuiu uma profissão a este personagem."
                : professional == customer ? "Seu ofício • materiais na mão secundária • sem cobrança nesta fase"
                : profession.label() + " • o profissional precisa aceitar seu pedido • sem cobrança nesta fase";
        if (profession == Profession.DOCTOR && actions.isEmpty()) subtitle = "A integração de ferimentos do LSO está desativada ou ausente.";
        send(customer, new PanelPayload(token, 0, displayName(professional), subtitle, rows));
    }
    public static void action(ServerPlayer sender, ActionPayload payload) {
        if (payload.token().equals(ZERO) && payload.action().equals("open")) { open(sender, sender); return; }
        long now = System.currentTimeMillis();
        var request = REQUESTS.take(sender.getUUID(), payload.token(), now);
        if (request != null) {
            var customer = sender.server.getPlayerList().getPlayer(request.view.customer);
            if (!payload.action().equals("accept")) {
                if (customer != null) status(customer, "Pedido encerrado", "O profissional não realizou o atendimento.");
                return;
            }
            perform(sender, customer, request, payload.token());
            return;
        }
        var view = VIEWS.take(sender.getUUID(), payload.token(), now);
        if (payload.action().equals("close")) return;
        if (view != null && payload.action().equals("refresh")) {
            var professional = sender.server.getPlayerList().getPlayer(view.professional);
            if (professional != null) open(sender, professional);
            return;
        }
        if (view == null || !view.actions.contains(payload.action())) { status(sender, "Pedido expirado", "Abra o atendimento novamente."); return; }
        var professional = sender.server.getPlayerList().getPlayer(view.professional);
        if (!validPair(professional, sender) || !unchanged(view, professional, sender)) {
            status(sender, "Pedido atualizado", "O profissional ou os itens mudaram. Abra o atendimento novamente."); return;
        }
        try {
            var plan = ServiceActions.plan(professional, sender, payload.action());
            var pending = new Request(view, payload.action());
            if (professional == sender) { perform(professional, sender, pending, payload.token()); return; }
            if (REQUESTS.busy(professional.getUUID(), now)) {
                status(sender, "Profissional ocupado", "Há outro pedido aguardando resposta. Tente novamente em instantes."); return;
            }
            // Um cliente tambem nao pode manter pedidos concorrentes em varios profissionais.
            REQUESTS.removeIf((owner, existing) -> existing.view.customer.equals(sender.getUUID()));
            UUID token = REQUESTS.put(professional.getUUID(), pending, now);
            showApproval(professional, sender, token, plan);
            status(sender, "Pedido enviado", "Aguarde a resposta de " + displayName(professional) + ". Mantenha o item na mão e permaneça próximo.");
        } catch (ServiceActions.Refusal refusal) { status(sender, "Não foi possível solicitar", refusal.getMessage()); }
    }
    private static void perform(ServerPlayer professional, ServerPlayer customer, Request request, UUID receipt) {
        try {
            if (!validPair(professional, customer) || !unchanged(request.view, professional, customer))
                throw new ServiceActions.Refusal("Os itens ou a distância mudaram. Solicite um novo atendimento.");
            var plan = ServiceActions.plan(professional, customer, request.action);
            var authorization = new ServiceEvent.Validate(receipt, professional, customer, request.action);
            NeoForge.EVENT_BUS.post(authorization);
            if (authorization.isCanceled()) throw new ServiceActions.Refusal("O serviço não foi autorizado.");
            // Uma integracao pode alterar estado dentro do evento; validar de novo antes de consumir.
            if (!validPair(professional, customer) || !unchanged(request.view, professional, customer))
                throw new ServiceActions.Refusal("O atendimento mudou durante a autorização.");
            plan = ServiceActions.plan(professional, customer, request.action);
            ServiceActions.execute(plan, professional, customer);
            AurorionProfissoes.LOGGER.info("Servico {}: {} -> {}, {}", receipt, professional.getUUID(), customer.getUUID(), request.action);
            status(customer, "Atendimento concluído", plan.title() + ".");
            if (professional != customer) status(professional, "Atendimento concluído", "Você atendeu " + displayName(customer) + ".");
            NeoForge.EVENT_BUS.post(new ServiceEvent.Completed(receipt, professional, customer, request.action));
        } catch (ServiceActions.Refusal refusal) {
            status(professional, "Atendimento interrompido", refusal.getMessage());
            if (customer != null && customer != professional) status(customer, "Atendimento interrompido", refusal.getMessage());
        }
    }
    private static void showApproval(ServerPlayer professional, ServerPlayer customer, UUID token, ServiceActions.Plan plan) {
        send(professional, new PanelPayload(token, 1, "Pedido de " + displayName(customer),
                "Confira o serviço • consome seus materiais e sua experiência • válido por 30 segundos",
                List.of(new PanelPayload.Row("accept", plan.title(), plan.detail(), true))));
    }
    public static boolean validPair(ServerPlayer professional, ServerPlayer customer) {
        return ProfessionsConfig.enabled() && professional != null && customer != null
                && !(professional instanceof FakePlayer) && !(customer instanceof FakePlayer)
                && professional.isAlive() && customer.isAlive() && !professional.isSpectator() && !customer.isSpectator()
                && professional.serverLevel() == customer.serverLevel() && professional.distanceToSqr(customer) <= 16
                && (professional == customer || professional.hasLineOfSight(customer))
                && professional.containerMenu == professional.inventoryMenu && customer.containerMenu == customer.inventoryMenu;
    }
    private static boolean unchanged(View view, ServerPlayer professional, ServerPlayer customer) {
        return ItemStack.matches(view.subject, customer.getMainHandItem()) && ItemStack.matches(view.supplies, professional.getOffhandItem());
    }
    public static void forget(UUID account) {
        VIEWS.removeIf((owner, view) -> owner.equals(account) || view.professional.equals(account) || view.customer.equals(account));
        REQUESTS.removeIf((owner, request) -> owner.equals(account) || request.view.customer.equals(account));
        LAST_OPEN.remove(account);
    }
    public static void clear() { VIEWS.clear(); REQUESTS.clear(); LAST_OPEN.clear(); }
    public static void status(ServerPlayer player, String title, String message) {
        send(player, new PanelPayload(ZERO, 2, title, message, List.of()));
    }
    private static String displayName(ServerPlayer player) {
        String name = player.getDisplayName().getString(); return name.length() > 80 ? name.substring(0, 80) : name;
    }
    private static void send(ServerPlayer player, PanelPayload payload) {
        if (player.connection.hasChannel(PanelPayload.TYPE.id())) PacketDistributor.sendToPlayer(player, payload);
    }
}
