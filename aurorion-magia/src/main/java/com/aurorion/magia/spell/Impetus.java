package com.aurorion.magia.spell;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import com.aurorion.magia.registry.MagiaEffects;
import com.aurorion.magia.registry.MagiaSpells;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A investida do Impeto do Vento: o avanco imparavel e a chegada que ergue todo mundo em volta.
 *
 * <h2>A investida</h2>
 *
 * <p>Quem conjura dispara para a frente, na horizontal, a {@value #DASH_SPEED} blocos por tick. O
 * impulso e reposto a cada tick pelo efeito {@code investida}, e nao dado uma vez so: assim o avanco e
 * uma linha reta do tamanho certo, que nao depende de atrito nem de ping. Enquanto dura, quem avanca
 * nao sofre empurrao nenhum — golpe, flecha, explosao ou magia de deslocamento ({@code Displacement}).
 *
 * <p>Termina ao percorrer a distancia, ao bater numa parede ou num degrau alto, ou no teto de
 * {@value #MAX_TICKS} ticks. O ponto de chegada e onde a pessoa esta nesse instante.
 *
 * <h2>A chegada</h2>
 *
 * <p>O chao racha e todos num raio de {@value #RADIUS} blocos sao lancados para o alto e ficam no ar
 * cerca de 1,5 s ({@code lancado}: sem conjurar, e sem dano de queda ao descer). Do nivel 2 em diante,
 * o impacto tambem fere.
 *
 * <p>Custo: um efeito que tica so em quem esta avancando, por no maximo {@value #MAX_TICKS} ticks, e
 * uma busca no raio na chegada, com teto de {@value #MAX_TARGETS} alvos.
 */
public final class Impetus {
    public static final double RADIUS = 5;
    public static final int MAX_TICKS = 16;
    /** Duracao maxima do "no ar"; o efeito sai antes, ao tocar o chao. */
    public static final int AIRBORNE_TICKS = 120;

    private static final double DASH_SPEED = 1.4;
    /** Impulso vertical do lancamento: sobe uns seis blocos e fica ~1,5 s no ar. */
    private static final double LIFT = 1.15;
    private static final int MAX_TARGETS = 24;
    /** Andou menos que isto num tick depois de embalar: bateu em algo. */
    private static final double STALL = 0.3;

    private static final String KEY = AurorionMagia.MOD_ID + ":impeto";

    private Impetus() {
    }

    public static void start(ServerLevel level, LivingEntity caster, double distance, float damage) {
        Vec3 look = caster.getLookAngle().multiply(1, 0, 1);
        Vec3 direction = look.lengthSqr() < 1.0E-4
                ? Vec3.directionFromRotation(0, caster.getYRot())
                : look.normalize();

        CompoundTag tag = new CompoundTag();
        tag.putDouble("X", caster.getX());
        tag.putDouble("Z", caster.getZ());
        tag.putDouble("LastX", caster.getX());
        tag.putDouble("LastZ", caster.getZ());
        tag.putDouble("DirX", direction.x);
        tag.putDouble("DirZ", direction.z);
        tag.putDouble("Distance", distance);
        tag.putFloat("Damage", damage);
        caster.getPersistentData().put(KEY, tag);

        caster.addEffect(new MobEffectInstance(MagiaEffects.DASHING, MAX_TICKS + 5, 0, false, false, true));
        push(caster, direction);

        AurorionSpell.sound(caster, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.2f, 0.7f);
        AurorionSpell.sound(caster, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.6f);
        MagiaNetwork.sendVisual(caster, caster, SpellVisualPayload.Kind.IMPETUS_DASH, MAX_TICKS + 2,
                caster.position(), 0);
    }

    /**
     * Um tick da investida, chamado pelo efeito {@code investida}.
     *
     * @return false quando ela acabou — o efeito devolve isso ao vanilla, que o remove
     */
    public static boolean tick(LivingEntity caster) {
        if (!(caster.level() instanceof ServerLevel level)) return true;
        CompoundTag tag = tagOf(caster);
        if (tag == null || !caster.isAlive()) return false;

        int ticks = tag.getInt("Tick") + 1;
        tag.putInt("Tick", ticks);
        double traveled = horizontal(caster, tag.getDouble("X"), tag.getDouble("Z"));
        double moved = horizontal(caster, tag.getDouble("LastX"), tag.getDouble("LastZ"));
        tag.putDouble("LastX", caster.getX());
        tag.putDouble("LastZ", caster.getZ());
        caster.resetFallDistance();

        // Parado dois ticks seguidos, e nao um: a posicao de um jogador no servidor vem dos pacotes de
        // movimento dele, e um tick sem pacote (lag) pareceria uma parede e encerraria cedo demais.
        int stalls = ticks > 2 && moved < STALL ? tag.getInt("Stalls") + 1 : 0;
        tag.putInt("Stalls", stalls);
        boolean blocked = ticks > 2 && (caster.horizontalCollision || stalls >= 2);
        if (traveled >= tag.getDouble("Distance") || blocked || ticks >= MAX_TICKS) {
            caster.getPersistentData().remove(KEY);
            impact(level, caster, tag.getFloat("Damage"));
            return false;
        }

        push(caster, new Vec3(tag.getDouble("DirX"), 0, tag.getDouble("DirZ")));
        return true;
    }

    /** Morreu, relogou ou teve o efeito tirado no meio: a investida acaba sem impacto. */
    public static void cancel(LivingEntity caster) {
        caster.getPersistentData().remove(KEY);
    }

    public static boolean isDashing(LivingEntity entity) {
        return entity.hasEffect(MagiaEffects.DASHING);
    }

    private static void impact(ServerLevel level, LivingEntity caster, float damage) {
        Vec3 center = caster.position();
        AurorionSpell.launch(caster, Vec3.ZERO);
        caster.resetFallDistance();

        for (LivingEntity victim : AreaCast.victims(level, caster, center, RADIUS, MAX_TARGETS, target -> true)) {
            // O dano primeiro: o recuo do golpe mexe na velocidade, e o lancamento logo depois a substitui.
            if (damage > 0) {
                FriendlyFire.applyDamage(victim, damage, MagiaSpells.IMPETUS_VENTI.get().getDamageSource(caster));
            }
            if (!victim.isAlive()) continue;
            victim.addEffect(new MobEffectInstance(MagiaEffects.AIRBORNE, AIRBORNE_TICKS, 0, false, false, true), caster);
            AurorionSpell.launch(victim, new Vec3(0, LIFT, 0));
            victim.resetFallDistance();
        }

        AurorionSpell.sound(level, center, SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 0.6f);
        AurorionSpell.sound(level, center, SoundEvents.ANVIL_LAND, 0.6f, 0.5f);
        AurorionSpell.sound(level, center, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.4f, 0.5f);
        MagiaNetwork.sendVisualAt(level, caster, SpellVisualPayload.Kind.IMPETUS_IMPACT, 60, center, (float) RADIUS);
    }

    /** O impulso da investida: horizontal, rente ao chao; no ar, desce um pouco a cada tick. */
    private static void push(LivingEntity caster, Vec3 direction) {
        double fall = caster.onGround() ? 0 : -0.25;
        AurorionSpell.launch(caster, new Vec3(direction.x * DASH_SPEED, fall, direction.z * DASH_SPEED));
    }

    private static double horizontal(LivingEntity entity, double x, double z) {
        double dx = entity.getX() - x, dz = entity.getZ() - z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    @Nullable
    private static CompoundTag tagOf(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        return data.contains(KEY, Tag.TAG_COMPOUND) ? data.getCompound(KEY) : null;
    }
}
