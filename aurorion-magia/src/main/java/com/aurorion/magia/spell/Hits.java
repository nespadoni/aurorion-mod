package com.aurorion.magia.spell;

import com.aurorion.magia.compat.AreasMagic;
import com.aurorion.magia.registry.MagiaEffects;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Mira e acerto das magias de dano: onde quem conjura esta olhando, quem um feixe atravessa e quem
 * esta dentro de um raio.
 *
 * <p>Diferente do {@link AreaCast}, que serve as magias de <i>deslocamento</i> e por isso deixa chefe
 * de fora, aqui chefe entra: dano e dano, e uma luta de chefe com a Centelha Final e luta normal.
 * Ficam de fora so quem conjurou, espectador, jogador em criativo e entidade invulneravel (NPC de
 * oficio, manequim).
 *
 * <h2>Custo</h2>
 *
 * <p>Uma busca espacial por chamada, com teto de alvos, sempre no instante do golpe. Nenhuma destas
 * funcoes guarda estado ou roda depois.
 */
public final class Hits {
    private Hits() {
    }

    /**
     * Pode levar o golpe de {@code caster}: vivo, visivel ao jogo, nao protegido, nao inalvejavel e,
     * entre jogadores, num lugar onde PvP vale.
     */
    public static boolean hittable(LivingEntity caster, LivingEntity target) {
        return target != caster && target.isAlive() && !target.isSpectator()
                && !AurorionSpell.untouchable(target)
                && !target.hasEffect(MagiaEffects.UNTARGETABLE)
                && !(target instanceof Player player && player.isCreative())
                && pvpAllowed(caster, target);
    }

    /**
     * Jogador contra jogador so onde o servidor e o {@code aurorion-areas} permitem PvP, conferido na
     * posicao da vitima. Vale tambem para o que nao fere — puxar, silenciar, virar bicho, possuir —,
     * que o cancelamento de dano da area nao alcanca. Criatura e sempre alvo.
     */
    public static boolean pvpAllowed(LivingEntity caster, LivingEntity target) {
        if (!(caster instanceof Player) || !(target instanceof ServerPlayer victim)) return true;
        return victim.server.isPvpAllowed() && AreasMagic.pvpAt(victim.serverLevel(), victim.position(), victim.getUUID());
    }

    /**
     * Alvo valido para quem conjurou. Com {@code magiasIgnoramTime} ligado (padrao), todo mundo e; com
     * ele desligado, o proprio time fica de fora. Ver {@link FriendlyFire}.
     */
    public static boolean enemy(LivingEntity caster, LivingEntity target) {
        return !FriendlyFire.spares(caster, target);
    }

    /**
     * O ponto do mundo para onde quem conjura olha: o primeiro bloco no caminho, ou o fim do alcance.
     *
     * <p>O alcance encolhe ate o ultimo trecho com chunk carregado: ler bloco em chunk descarregado no
     * servidor carrega o chunk na hora, na thread principal — e a Bomba Megainfernal mira longe.
     */
    public static Vec3 aimPoint(Level level, LivingEntity caster, double range) {
        Vec3 from = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        while (range > 16 && !level.isLoaded(BlockPos.containing(from.add(look.scale(range))))) range -= 16;
        Vec3 to = from.add(look.scale(range));
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        return hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
    }

    /** O mesmo ponto, assentado no chao mais proximo (ate 12 blocos acima ou abaixo). */
    public static Vec3 aimGround(Level level, LivingEntity caster, double range) {
        return Utils.moveToRelativeGroundLevel(level, aimPoint(level, caster, range), 12);
    }

    /**
     * A ponta de um feixe que sai de {@code from} na direcao {@code direction}: o primeiro bloco, ou o
     * alcance inteiro quando o feixe atravessa paredes.
     */
    public static Vec3 beamEnd(Level level, LivingEntity caster, Vec3 from, Vec3 direction, double range,
                               boolean throughWalls) {
        Vec3 to = from.add(direction.normalize().scale(range));
        if (throughWalls) return to;
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        return hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
    }

    /** Os vivos que o segmento {@code from}–{@code to} atravessa, do mais perto ao mais longe. */
    public static List<LivingEntity> along(ServerLevel level, LivingEntity caster, Vec3 from, Vec3 to, double width,
                                           int max, Predicate<LivingEntity> extra) {
        AABB box = new AABB(from, to).inflate(width + 1);
        List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, box, target -> {
            if (!hittable(caster, target) || !extra.test(target)) return false;
            AABB body = target.getBoundingBox().inflate(width);
            return body.contains(from) || body.clip(from, to).isPresent();
        });
        found.sort(Comparator.comparingDouble(target -> target.distanceToSqr(from)));
        return found.size() <= max ? found : found.subList(0, max);
    }

    /** Os vivos dentro da esfera, do mais perto ao mais longe. */
    public static List<LivingEntity> around(ServerLevel level, LivingEntity caster, Vec3 center, double radius,
                                            int max, Predicate<LivingEntity> extra) {
        double radiusSqr = radius * radius;
        List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                target -> hittable(caster, target) && target.position().distanceToSqr(center) <= radiusSqr
                        && extra.test(target));
        found.sort(Comparator.comparingDouble(target -> target.position().distanceToSqr(center)));
        return found.size() <= max ? found : found.subList(0, max);
    }
}
