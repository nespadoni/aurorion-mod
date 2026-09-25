package com.aurorion.profissoes.server;

import com.aurorion.economia.server.ChargeManager;
import com.aurorion.profissoes.api.ProfessionApi;
import com.aurorion.profissoes.compat.LsoCompat;
import com.aurorion.profissoes.compat.LivesBridge;
import com.aurorion.profissoes.config.ProfessionsConfig;
import com.aurorion.profissoes.data.Profession;
import net.minecraft.server.level.ServerPlayer;

public final class DoctorHealing {
    private DoctorHealing() { }

    public static boolean needsHealing(ServerPlayer target) {
        return target.getHealth() < target.getMaxHealth()
                || LsoCompat.hasWounds(target) || LivesBridge.missingLives(target);
    }

    public static void heal(ServerPlayer doctor, ServerPlayer target) {
        if (!ProfessionsConfig.enabled() || ProfessionApi.of(doctor) != Profession.DOCTOR) {
            ChargeManager.status(doctor, "Cura indisponível", "Somente médicos podem curar outra pessoa.", false);
            return;
        }
        String refusal = ChargeManager.validatePair(doctor, target, true);
        if (refusal != null) {
            ChargeManager.status(doctor, "Cura indisponível", refusal, false);
            return;
        }
        if (!needsHealing(target)) {
            ChargeManager.status(doctor, "Cura indisponível", "Essa pessoa já está saudável.", false);
            return;
        }
        LsoCompat.healAll(target);
        target.setHealth(target.getMaxHealth());
        LivesBridge.restore(target);
        ChargeManager.status(doctor, "Cura concluída", "Você curou " + target.getDisplayName().getString() + ".", true);
        ChargeManager.status(target, "Cura concluída", doctor.getDisplayName().getString() + " cuidou dos seus ferimentos.", true);
    }
}
