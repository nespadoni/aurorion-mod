package com.aurorion.profissoes.server;

import com.aurorion.economia.server.ChargeManager;
import com.aurorion.profissoes.api.ProfessionApi;
import com.aurorion.profissoes.compat.LsoCompat;
import com.aurorion.profissoes.config.ProfessionsConfig;
import com.aurorion.profissoes.data.Profession;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public final class DoctorHealing {
    /** Perícia da ficha (catálogo do site) que rege a cura. */
    public static final String MEDICINE = "medicina";

    private DoctorHealing() { }

    public static boolean needsHealing(ServerPlayer target) {
        return target.getHealth() < target.getMaxHealth()
                || LsoCompat.hasWounds(target);
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
        String doctorName = doctor.getDisplayName().getString();
        String targetName = target.getDisplayName().getString();
        // Teste de Medicina da ficha: a mão do médico decide quanto da cura pega.
        switch (SkillCheck.roll(doctor, MEDICINE, ProfessionsConfig.HEAL_DC.get())) {
            case CRIT -> {
                LsoCompat.healAll(target);
                target.setHealth(target.getMaxHealth());
                target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
                ChargeManager.status(doctor, "Cura impecável", "Você curou " + targetName + " com mão de mestre.", true);
                ChargeManager.status(target, "Cura impecável", doctorName + " cuidou de você como poucos sabem.", true);
            }
            case SUCCESS -> {
                LsoCompat.healAll(target);
                target.setHealth(target.getMaxHealth());
                ChargeManager.status(doctor, "Cura concluída", "Você curou " + targetName + ".", true);
                ChargeManager.status(target, "Cura concluída", doctorName + " cuidou dos seus ferimentos.", true);
            }
            case FAIL -> {
                target.heal((target.getMaxHealth() - target.getHealth()) * 0.5f);
                ChargeManager.status(doctor, "Cura incompleta", "Você estancou parte do estrago, mas os ferimentos de "
                        + targetName + " continuam.", false);
                ChargeManager.status(target, "Cura incompleta", doctorName + " ajudou, mas não deu conta de tudo.", false);
            }
            case CRIT_FAIL -> {
                target.heal((target.getMaxHealth() - target.getHealth()) * 0.25f);
                ChargeManager.status(doctor, "Cura malfeita", "A mão tremeu: " + targetName + " quase não melhorou.", false);
                ChargeManager.status(target, "Cura malfeita", doctorName + " errou o tratamento.", false);
            }
        }
    }
}
