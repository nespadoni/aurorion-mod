package com.aurorion.profissoes.server;

import com.aurorion.economia.api.InteractionMenuEvent;
import com.aurorion.economia.api.BrokerAuthorizationEvent;
import com.aurorion.economia.server.LandSaleManager;
import com.aurorion.profissoes.AurorionProfissoes;
import com.aurorion.profissoes.api.ProfessionApi;
import com.aurorion.profissoes.config.ProfessionsConfig;
import com.aurorion.profissoes.data.Profession;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Coloca os atendimentos existentes dentro do menu presencial da Economia. */
@EventBusSubscriber(modid = AurorionProfissoes.MOD_ID)
public final class ProfessionInteractionMenu {
    private static final String ACTION = "aurorion_profissoes:service";
    private static final String HEAL_ACTION = "aurorion_profissoes:heal";

    private ProfessionInteractionMenu() { }

    @SubscribeEvent
    public static void collect(InteractionMenuEvent.Collect event) {
        if (ProfessionsConfig.enabled() && ProfessionApi.of(event.actor()) == Profession.BROKER)
            event.add("land_sale", "Vender terreno", "Selecione um lote retangular, a zona e o preço. Pagamento ao sistema.", true);
        if (ProfessionsConfig.enabled() && ProfessionApi.of(event.actor()) == Profession.DOCTOR) {
            // O menu so apresenta; a acao confere tudo de novo em DoctorHealing.heal.
            String refusal = DoctorHealing.refusal(event.actor(), event.target());
            String detail = refusal != null ? refusal
                    : "Restaura a saúde e os ferimentos do alvo. Consome 1 insumo médico da mão secundária.";
            event.add(HEAL_ACTION, "Curar pessoa", detail.length() > 256 ? detail.substring(0, 256) : detail, refusal == null);
        }
        Profession profession = ProfessionApi.of(event.target());
        if (!ProfessionsConfig.enabled() || !offersServices(profession)) return;
        event.add(ACTION, "Solicitar atendimento",
                "Ofício: " + profession.label() + ". Os itens e materiais serão conferidos antes do aceite.", true);
    }

    @SubscribeEvent
    public static void action(InteractionMenuEvent.Action event) {
        if (HEAL_ACTION.equals(event.action())) {
            event.markHandled();
            DoctorHealing.heal(event.actor(), event.target());
            return;
        }
        if ("land_sale".equals(event.action())) {
            event.markHandled();
            LandSaleManager.open(event.actor(), event.target());
            return;
        }
        if (!ACTION.equals(event.action())) return;
        event.markHandled();
        if (!ProfessionsConfig.enabled() || !offersServices(ProfessionApi.of(event.target()))) {
            ServiceManager.status(event.actor(), "Atendimento indisponível",
                    "Esse personagem não oferece um atendimento profissional.");
            return;
        }
        ServiceManager.open(event.actor(), event.target());
    }

    private static boolean offersServices(Profession profession) {
        return profession == Profession.DOCTOR || profession == Profession.SMITH
                || profession == Profession.CHEF || profession == Profession.ARCANIST;
    }

    @SubscribeEvent
    public static void authorizeBroker(BrokerAuthorizationEvent event) {
        if (ProfessionsConfig.enabled() && ProfessionApi.of(event.player()) == Profession.BROKER) event.authorize();
    }
}
