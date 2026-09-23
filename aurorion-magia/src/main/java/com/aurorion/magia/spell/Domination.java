package com.aurorion.magia.spell;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.config.MagiaConfig;
import com.aurorion.magia.registry.MagiaEffects;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A mente de um mob sob Imperium Mentis.
 *
 * <p>O dominio tem duas metades:
 *
 * <ul>
 *   <li><b>Quem ele ataca</b> e escolhido aqui: o vivo mais perto que nao seja o mestre, aliado de
 *       time do mestre, pet do mestre ou outro dominado do mesmo mestre. A escolha acontece no
 *       momento do feitico e depois so quando o alvo atual morre ou some — conferido uma vez por
 *       segundo pelo tick do proprio efeito.</li>
 *   <li><b>Quem ele NAO pode atacar</b> e garantido por evento ({@code LivingChangeTargetEvent} e
 *       dano), em {@code MagiaServerEvents}. Enquanto dominado, a IA vanilla nao troca de alvo por
 *       conta propria; so o que {@link #retarget} escolhe e aceito.</li>
 * </ul>
 *
 * <p>O mestre fica no {@code persistentData} do mob, ao lado do efeito que tambem e salvo: um mob
 * dominado que descarrega com o chunk volta dominado, pelo mesmo mestre, pelo tempo que faltava.
 *
 * <p>Mob sem IA de ataque (vaca, aldeao) recebe o alvo e nao faz nada com ele. Mob de IA por
 * "brain" (piglin, warden) nao usa {@code setTarget} para lutar — ficam na lista de imunes.
 */
public final class Domination {
    private static final String KEY_MASTER = AurorionMagia.MOD_ID + ":mestre";

    /** Chefes e mobs de IA por brain. Editavel por datapack. */
    public static final TagKey<EntityType<?>> IMMUNE =
            TagKey.create(Registries.ENTITY_TYPE, AurorionMagia.id("imune_dominacao"));

    /**
     * Ligado so durante o nosso {@code setTarget}. O servidor e uma thread so, entao um campo simples
     * basta para o listener do evento distinguir "fomos nos" de "foi a IA".
     */
    private static boolean assigning;

    private Domination() {
    }

    public static boolean canDominate(Mob mob) {
        return !mob.getType().is(IMMUNE);
    }

    public static void dominate(Mob mob, LivingEntity master, int durationTicks) {
        mob.getPersistentData().putUUID(KEY_MASTER, master.getUUID());
        mob.addEffect(new MobEffectInstance(MagiaEffects.DOMINATED, durationTicks, 0, false, false, true), master);
        retarget(mob, master.getUUID());
    }

    /** Chamado pelo tick do efeito, uma vez por segundo. */
    public static void maintain(Mob mob) {
        UUID master = masterOf(mob);
        if (master == null) return;

        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive() || isProtected(mob, target, master, masterEntity(mob, master))) {
            retarget(mob, master);
        }
    }

    /** Fim do dominio: esquece o mestre e larga o alvo imposto, e a IA vanilla volta a decidir. */
    public static void release(Mob mob) {
        if (!mob.getPersistentData().contains(KEY_MASTER)) return;
        mob.getPersistentData().remove(KEY_MASTER);
        mob.setTarget(null);
    }

    /** @return {@code true} se a troca de alvo pode acontecer. */
    public static boolean allowsTargetChange(Mob mob, @Nullable LivingEntity newTarget) {
        if (assigning || newTarget == null) return true;
        return masterOf(mob) == null;
    }

    /** @return {@code true} se {@code attacker} e um dominado tentando ferir quem ele protege. */
    public static boolean isHarmingProtected(Entity attacker, LivingEntity victim) {
        if (!(attacker instanceof Mob mob)) return false;
        UUID master = masterOf(mob);
        return master != null && isProtected(mob, victim, master, masterEntity(mob, master));
    }

    @Nullable
    public static UUID masterOf(LivingEntity entity) {
        if (!entity.hasEffect(MagiaEffects.DOMINATED)) return null;
        var data = entity.getPersistentData();
        return data.hasUUID(KEY_MASTER) ? data.getUUID(KEY_MASTER) : null;
    }

    /**
     * A unica busca espacial do Imperium. {@code getEntitiesOfClass} consulta so as secoes de chunk
     * que cruzam a caixa, entao o custo e o do raio, nao o da populacao do mundo — e roda por mob
     * dominado, no maximo uma vez por segundo.
     */
    private static void retarget(Mob mob, UUID master) {
        double radius = MagiaConfig.DOMINATION_RADIUS.get();
        Entity masterEntity = masterEntity(mob, master);

        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : mob.level().getEntitiesOfClass(LivingEntity.class,
                mob.getBoundingBox().inflate(radius), candidate -> candidate != mob && candidate.isAlive())) {
            if (isProtected(mob, candidate, master, masterEntity)) continue;
            double distance = mob.distanceToSqr(candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }

        assigning = true;
        try {
            mob.setTarget(best);
        } finally {
            assigning = false;
        }
    }

    private static boolean isProtected(Mob mob, LivingEntity candidate, UUID master, @Nullable Entity masterEntity) {
        if (candidate.getUUID().equals(master)) return true;
        if (masterEntity != null && candidate.isAlliedTo(masterEntity)) return true;
        if (candidate instanceof OwnableEntity pet && master.equals(pet.getOwnerUUID())) return true;
        if (candidate instanceof Player player && (player.isCreative() || player.isSpectator())) return true;
        if (!candidate.attackable() || !mob.canAttack(candidate)) return true;
        return master.equals(masterOf(candidate));
    }

    @Nullable
    private static Entity masterEntity(Mob mob, UUID master) {
        return mob.level() instanceof ServerLevel level ? level.getEntity(master) : null;
    }
}
