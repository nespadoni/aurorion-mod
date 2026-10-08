package com.aurorion.essentials.death;

import com.aurorion.essentials.AurorionEssentials;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.jetbrains.annotations.Nullable;

/**
 * A vida nunca vira NaN. Ver {@code HealthNaNGuardMixin}, que chama isto.
 *
 * <h2>O defeito</h2>
 *
 * <p>Um jogador ficava com vida e absorcao {@code NaN}: a barra mostra zero, mas
 * {@code isDeadOrDying()} e {@code getHealth() <= 0}, e {@code NaN <= 0} e falso — entao ele nunca
 * morre, nem com {@code /kill}. O unico remedio era apagar o playerdata.
 *
 * <p>A origem mais comum e dano <b>infinito</b>. O {@code /kill} aplica {@code Float.MAX_VALUE}; se
 * qualquer mod multiplicar isso por mais de 1, vira {@code Infinity}, e o {@code actuallyHurt} do
 * NeoForge calcula a reducao de armadura como {@code dano - danoDepoisDaArmadura} =
 * {@code Infinity - Infinity} = {@code NaN}. Dali em diante, {@code Math.max(0, NaN)} e
 * {@code Mth.clamp(NaN, ...)} devolvem {@code NaN}, e a absorcao e a vida sao gravadas assim. Uma
 * absorcao {@code NaN} contamina a vida no golpe seguinte, mesmo pequeno.
 *
 * <h2>A regra</h2>
 *
 * <ul>
 *   <li><b>No meio de um golpe</b> (ha um {@code DamageContainer} na pilha): aplica o dano
 *       <i>original</i> do golpe, que e finito ou infinito, mas nunca o {@code NaN} do meio do
 *       caminho. {@code /kill} volta a matar; um golpe comum tira o que tiraria.</li>
 *   <li><b>Fora de golpe</b> (cura, comando, carga do save): a escrita e ignorada e a vida fica como
 *       estava.</li>
 *   <li><b>Ja corrompido</b> (save antigo, escrita direta no entity data): o tick restaura a vida
 *       maxima e zera a absorcao. Quem estava preso volta a jogar sem apagar o playerdata.</li>
 * </ul>
 *
 * <p>Cada correcao vai para o log com stack trace (no maximo um a cada {@link #TRACE_INTERVAL_MS}),
 * para que o mod que produz o NaN apareca e possa ser consertado na origem.
 */
public final class HealthGuard {
    private static final long TRACE_INTERVAL_MS = 10_000L;
    private static long lastTrace;
    private static int suppressed;

    private HealthGuard() {
    }

    /** O valor que entra no lugar de uma vida {@code NaN}. */
    public static float health(LivingEntity entity, @Nullable DamageContainer hit) {
        float current = entity.getHealth();
        float base = Float.isFinite(current) ? current : restoredHealth(entity);

        float result;
        String detail;
        if (hit != null && !Float.isNaN(hit.getOriginalDamage()) && hit.getOriginalDamage() > 0.0F) {
            float original = Math.min(hit.getOriginalDamage(), Float.MAX_VALUE);
            result = Math.max(0.0F, base - original);
            detail = "golpe " + hit.getSource().getMsgId() + " de " + hit.getOriginalDamage()
                    + " aplicado pelo valor original";
        } else {
            result = base;
            detail = hit != null ? "golpe " + hit.getSource().getMsgId() + " com dano NaN ignorado"
                    : "escrita ignorada";
        }

        report(entity, "vida NaN (" + detail + "; vida " + current + " -> " + result + ")");
        return result;
    }

    /** O valor que entra no lugar de uma absorcao {@code NaN}. */
    public static float absorption(LivingEntity entity) {
        report(entity, "absorcao NaN zerada");
        return 0.0F;
    }

    /** Conserta, no tick, o que ja chegou corrompido. So no servidor. */
    public static void repair(LivingEntity entity) {
        if (Float.isNaN(entity.getAbsorptionAmount())) {
            entity.setAbsorptionAmount(0.0F);
        }
        if (Float.isNaN(entity.getHealth())) {
            float restored = restoredHealth(entity);
            entity.setHealth(restored);
            AurorionEssentials.LOGGER.warn("{} estava com vida NaN; restaurada para {}.", name(entity), restored);
        }
    }

    private static float restoredHealth(LivingEntity entity) {
        float max = entity.getMaxHealth();
        if (Float.isFinite(max) && max > 0.0F) return max;
        // Vida maxima NaN e um modificador de atributo quebrado: setHealth ainda grava NaN, porque o
        // clamp usa a maxima. O log aponta para o atributo, que e onde o conserto tem de acontecer.
        report(entity, "atributo max_health invalido (" + max + ")");
        return 1.0F;
    }

    private static void report(LivingEntity entity, String what) {
        if (entity.level().isClientSide()) return;

        long now = System.currentTimeMillis();
        if (now - lastTrace < TRACE_INTERVAL_MS) {
            suppressed++;
            return;
        }
        lastTrace = now;
        int skipped = suppressed;
        suppressed = 0;
        AurorionEssentials.LOGGER.warn("Corrigido em {}: {}. O stack trace abaixo mostra quem escreveu o valor{}.",
                name(entity), what, skipped > 0 ? " (" + skipped + " ocorrencias anteriores sem trace)" : "",
                new Throwable("origem do NaN"));
    }

    private static String name(LivingEntity entity) {
        return entity instanceof Player player ? player.getGameProfile().getName() : entity.getType().toShortString();
    }
}
