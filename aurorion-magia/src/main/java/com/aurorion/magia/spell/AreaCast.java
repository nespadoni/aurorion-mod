package com.aurorion.magia.spell;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * "Agachado, em area." A regra vale igual em toda magia que tem as duas formas — Queda Forcada,
 * Olhar Cativo, Sentenca Final: conjurada em pe, atinge um alvo; conjurada <b>agachada</b>, atinge
 * todos em volta. Quem conjura nunca esta na lista.
 *
 * <h2>Custo</h2>
 *
 * <p>Uma unica busca espacial no instante da conjuracao, com teto de alvos — o mesmo que o Tempus
 * Sistere e o Tormento Coletivo ja fazem. Nada roda depois: o que sobra e efeito de status, que o
 * vanilla tica sozinho em cada afetado.
 */
public final class AreaCast {
    private AreaCast() {
    }

    /** O gatilho da versao em area, um so em todo o mod: agachar. */
    public static boolean wide(LivingEntity caster) {
        return caster.isShiftKeyDown();
    }

    /**
     * Os vivos ao alcance, do mais perto para o mais longe, ate {@code max}.
     *
     * <p>Sempre de fora: quem conjurou, espectador, jogador em criativo, entidade invulneravel
     * (NPC de oficio, manequim) e o que estiver em {@code imune_deslocamento} — chefe nao entra em
     * magia de area.
     */
    public static List<LivingEntity> victims(ServerLevel level, LivingEntity caster, Vec3 center, double radius,
                                             int max, Predicate<LivingEntity> extra) {
        double radiusSqr = radius * radius;
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(center, center).inflate(radius),
                target -> target != caster && target.isAlive() && !target.isSpectator()
                        && !AurorionSpell.untouchable(target)
                        && !(target instanceof Player player && player.isCreative())
                        && !Displacement.isImmune(target)
                        && target.position().distanceToSqr(center) <= radiusSqr
                        && extra.test(target));
        victims.sort(Comparator.comparingDouble(target -> target.position().distanceToSqr(center)));
        return victims.size() <= max ? victims : victims.subList(0, max);
    }
}
