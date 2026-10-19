package com.aurorion.mundos.perfil;

import com.aurorion.mundos.AurorionMundos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

/**
 * Os monstros do perfil de mundo: vida, armadura, velocidade e faro na hora em que entram no mundo;
 * dano e experiencia na hora em que acontecem.
 *
 * <h2>Por que assim, e nao por tick</h2>
 *
 * <ul>
 *   <li><b>Atributo</b> e um modificador permanente posto quando o monstro entra no mundo
 *       ({@code EntityJoinLevelEvent}) — nasceu, saiu de spawner ou voltou com o chunk. O vanilla salva o
 *       modificador com o monstro, e o recalculo do valor e do proprio vanilla. Um monstro que carrega
 *       com o chunk depois de a staff mudar o perfil e ajustado ali mesmo (o modificador e trocado pelo
 *       id), com a vida na mesma proporcao em que estava.</li>
 *   <li><b>Dano</b> nao e atributo: esqueleto, creeper, ghast e blaze nao tem {@code ATTACK_DAMAGE}, e o
 *       que fere e a flecha, a explosao, a bola de fogo. Multiplicar no {@code LivingIncomingDamageEvent}
 *       pelo monstro <i>dono</i> do golpe pega todos eles com uma regra so — e vale na hora em que a
 *       staff troca o numero.</li>
 * </ul>
 *
 * <p>A marca {@value #MARK} (tag de entidade do vanilla, salva com ela) diz "este monstro esta sob
 * perfil". E ela que deixa o caso comum barato: monstro sem perfil e sem marca sai na primeira linha,
 * sem tocar em atributo nenhum.
 */
public final class MonsterScaling {
    /** Tag de entidade: o monstro esta sob um perfil de mundo. */
    public static final String MARK = "aurorion_mundos.perfil";
    /** Dado persistente: nasceu de spawner (o perfil pode deixa-lo de fora). */
    public static final String FROM_SPAWNER = "aurorion_mundos:de_spawner";

    private static final ResourceLocation HEALTH = AurorionMundos.id("perfil_vida");
    private static final ResourceLocation ARMOR = AurorionMundos.id("perfil_armadura");
    private static final ResourceLocation TOUGHNESS = AurorionMundos.id("perfil_resistencia_armadura");
    private static final ResourceLocation KNOCKBACK = AurorionMundos.id("perfil_empurrao");
    private static final ResourceLocation SPEED = AurorionMundos.id("perfil_velocidade");
    private static final ResourceLocation FOLLOW = AurorionMundos.id("perfil_alcance");

    private MonsterScaling() {
    }

    /**
     * Ajusta um monstro ao perfil da dimensao em que ele esta.
     *
     * @param fresh acabou de nascer: entra com a vida cheia. Carregado do disco: mantem a proporcao.
     */
    public static void apply(Mob mob, boolean fresh) {
        if (!(mob instanceof Enemy) || !(mob.level() instanceof ServerLevel level)) return;
        WorldProfile.Monsters monsters = monstersFor(mob, WorldProfiles.forLevel(level.dimension()));
        boolean marked = mob.getTags().contains(MARK);
        if (monsters == null) {
            if (!marked) return;
            mob.removeTag(MARK);
            monsters = WorldProfile.Monsters.VANILLA;
        } else if (!marked) {
            mob.addTag(MARK);
        }

        float maxBefore = mob.getMaxHealth();
        float health = mob.getHealth();
        boolean healthChanged = set(mob, Attributes.MAX_HEALTH, HEALTH, monsters.health() - 1,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        set(mob, Attributes.ARMOR, ARMOR, monsters.armor(), AttributeModifier.Operation.ADD_VALUE);
        set(mob, Attributes.ARMOR_TOUGHNESS, TOUGHNESS, monsters.toughness(), AttributeModifier.Operation.ADD_VALUE);
        set(mob, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK, monsters.knockback(), AttributeModifier.Operation.ADD_VALUE);
        set(mob, Attributes.MOVEMENT_SPEED, SPEED, monsters.speed() - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        set(mob, Attributes.FOLLOW_RANGE, FOLLOW, monsters.followRange() - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        if (!healthChanged) return;
        float max = mob.getMaxHealth();
        if (fresh || maxBefore <= 0) {
            mob.setHealth(max);
        } else {
            mob.setHealth(Math.min(max, health * max / maxBefore));
        }
    }

    /** O multiplicador de dano do perfil para um golpe deste atacante, ou 1. */
    public static float damageFactor(@Nullable Entity attacker) {
        if (!(attacker instanceof Mob mob) || !mob.getTags().contains(MARK)) return 1;
        WorldProfile profile = WorldProfiles.forLevel(mob.level().dimension());
        return profile == null ? 1 : (float) profile.monsters().damage();
    }

    public static double experienceFactor(Entity entity) {
        if (!entity.getTags().contains(MARK)) return 1;
        WorldProfile profile = WorldProfiles.forLevel(entity.level().dimension());
        return profile == null ? 1 : profile.monsters().experience();
    }

    /** Os numeros que valem para este monstro, ou {@code null} se o perfil nao o alcanca. */
    @Nullable
    private static WorldProfile.Monsters monstersFor(Mob mob, @Nullable WorldProfile profile) {
        if (profile == null || profile.monsters().equals(WorldProfile.Monsters.VANILLA)) return null;
        WorldProfile.Monsters monsters = profile.monsters();
        if (!monsters.bosses() && mob.getType().is(Tags.EntityTypes.BOSSES)) return null;
        if (!monsters.spawners() && mob.getPersistentData().getBoolean(FROM_SPAWNER)) return null;
        return monsters;
    }

    /** @return se o valor final do atributo pode ter mudado */
    private static boolean set(Mob mob, Holder<Attribute> attribute, ResourceLocation id, double amount,
                               AttributeModifier.Operation operation) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance == null) return false;
        AttributeModifier current = instance.getModifier(id);
        if (amount == 0) {
            return current != null && instance.removeModifier(id);
        }
        if (current != null && current.amount() == amount && current.operation() == operation) return false;
        instance.addOrReplacePermanentModifier(new AttributeModifier(id, amount, operation));
        return true;
    }
}
