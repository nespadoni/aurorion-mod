package com.aurorion.profissoes.server;

import com.aurorion.core.rate.ActionCooldown;
import com.aurorion.core.text.TimeFormat;
import com.aurorion.economia.server.ChargeManager;
import com.aurorion.profissoes.AurorionProfissoes;
import com.aurorion.profissoes.api.ProfessionApi;
import com.aurorion.profissoes.compat.LsoCompat;
import com.aurorion.profissoes.config.ProfessionsConfig;
import com.aurorion.profissoes.data.Profession;
import net.minecraft.Util;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * A cura completa do medico pelo menu presencial (Shift+G).
 *
 * <p>Antes restaurava vida e todos os membros de qualquer pessoa, de graca, sem limite e no meio da
 * luta — o medico virava um botao de imortalidade de grupo. Agora e um atendimento com custo e
 * contexto:</p>
 *
 * <ul>
 *   <li><b>Insumo</b>: consome 1 item de {@code aurorion_profissoes:medical_supplies} (medkit/tonico
 *       do LSO) da mao secundaria do medico — o mesmo insumo que o tratamento por membro
 *       ({@code treat:}) exige. A cura e completa, entao custa o item de cura completa.</li>
 *   <li><b>Recarga por paciente</b>: {@value #TARGET_COOLDOWN_MS} ms por alvo, contada no alvo e nao no
 *       medico, para que dois medicos revezando nao contornem a regra. Fica em memoria e nao zera no
 *       logout (o {@link ActionCooldown} e por UUID); so um reinicio do servidor a limpa.</li>
 *   <li><b>Trava de combate</b>: recusa se o medico ou o paciente tomaram ou causaram dano nos
 *       ultimos {@value #COMBAT_LOCK_MS} ms ({@link CombatClock}). Cura e para depois da briga.</li>
 * </ul>
 *
 * <p>Cobrar pelo atendimento continua sendo RP: o medico usa a cobranca do mesmo menu.</p>
 */
public final class DoctorHealing {
    static final long TARGET_COOLDOWN_MS = 60_000L;
    static final long COMBAT_LOCK_MS = 15_000L;
    private static final ActionCooldown TARGETS = new ActionCooldown(TARGET_COOLDOWN_MS);

    private DoctorHealing() { }

    public static boolean needsHealing(ServerPlayer target) {
        return target.getHealth() < target.getMaxHealth()
                || LsoCompat.hasWounds(target);
    }

    /** O motivo de a cura nao poder acontecer agora, ou {@code null}. Usado pelo menu e pela acao. */
    public static @Nullable String refusal(ServerPlayer doctor, ServerPlayer target) {
        if (!ProfessionsConfig.enabled() || ProfessionApi.of(doctor) != Profession.DOCTOR)
            return "Somente médicos podem curar outra pessoa.";
        String pair = ChargeManager.validatePair(doctor, target, true);
        if (pair != null) return pair;
        if (!needsHealing(target)) return "Essa pessoa já está saudável.";
        long doctorLock = CombatClock.lockedFor(doctor, COMBAT_LOCK_MS);
        long targetLock = CombatClock.lockedFor(target, COMBAT_LOCK_MS);
        if (doctorLock > 0 || targetLock > 0)
            return "Ninguém é tratado no meio do combate. Aguarde "
                    + TimeFormat.duration(Math.max(doctorLock, targetLock)).getString() + " sem tomar ou causar dano.";
        long wait = TARGETS.remainingMillis(target.getUUID(), Util.getMillis());
        if (wait > 0)
            return "Essa pessoa acabou de ser tratada. Nova cura em " + TimeFormat.duration(wait).getString() + ".";
        if (!doctor.getOffhandItem().is(ServiceActions.MEDICAL_SUPPLIES))
            return "Segure 1 insumo de cura completa do LSO (medkit ou tônico) na mão secundária.";
        return null;
    }

    public static void heal(ServerPlayer doctor, ServerPlayer target) {
        String refusal = refusal(doctor, target);
        // A recarga so e gravada aqui, depois de tudo conferido: recusa nao gasta a vez de ninguem.
        if (refusal == null && !TARGETS.acquire(target.getUUID(), Util.getMillis()))
            refusal = "Essa pessoa acabou de ser tratada. Tente novamente em instantes.";
        if (refusal != null) {
            ChargeManager.status(doctor, "Cura indisponível", refusal, false);
            return;
        }
        doctor.getOffhandItem().shrink(1);
        LsoCompat.healAll(target);
        target.setHealth(target.getMaxHealth());
        doctor.inventoryMenu.broadcastChanges();
        AurorionProfissoes.LOGGER.info("Cura completa: medico {} -> paciente {} (1 insumo).",
                doctor.getUUID(), target.getUUID());
        ChargeManager.status(doctor, "Cura concluída", "Você curou " + target.getDisplayName().getString()
                + ". 1 insumo médico foi usado.", true);
        ChargeManager.status(target, "Cura concluída", doctor.getDisplayName().getString() + " cuidou dos seus ferimentos.", true);
    }
}
