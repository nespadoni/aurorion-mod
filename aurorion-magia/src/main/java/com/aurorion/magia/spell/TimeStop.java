package com.aurorion.magia.spell;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.compat.FrozenLink;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import com.aurorion.magia.registry.MagiaEffects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A zona do Tempus Sistere: o tempo parado num salao inteiro, ate quem o parou conjurar de novo.
 *
 * <p>A zona e <b>fixa no ponto da conjuracao</b>, e nao presa ao corpo de quem conjurou: o que para
 * e o lugar. Ela e um cilindro — {@code radius} blocos na horizontal e {@value #HEIGHT} para cima e
 * para baixo —, e nao uma esfera: com raio de 30 a 40, uma esfera congelaria tambem o andar de
 * cima e o porao de quem so esta morando no mesmo predio.
 *
 * <h2>O relogio</h2>
 *
 * <p>Mesmo desenho da Presenca Aterradora: um efeito infinito e invisivel no conjurador
 * ({@code tempo_suspenso}) tica a zona uma vez por segundo, e so enquanto ela existe. Sem zona
 * ligada, custo zero. O congelamento em quem esta dentro dura pouco mais que o intervalo entre
 * pulsos e e renovado a cada um — quem for arrastado para fora descongela sozinho, e quem entrar
 * depois congela no pulso seguinte. O centro e o raio moram no {@code persistentData} do conjurador.
 *
 * <h2>Custo, por zona ligada</h2>
 *
 * <ul>
 *   <li><b>Gente, 1x/s</b>: uma volta em {@code level.players()}, sem busca espacial.</li>
 *   <li><b>Criaturas, 1x/2 s</b>: uma busca no cilindro, teto de {@value #MAX_CREATURES}.</li>
 *   <li><b>Projeteis, 1x/s</b>: uma busca no cilindro; flecha que entra perde o impulso.</li>
 *   <li>Um payload de visual por pulso, para quem tem o chunk do centro.</li>
 * </ul>
 *
 * <p>Acaba com a segunda conjuracao, com a morte, ao deslogar e ao trocar de dimensao. Leite e totem
 * nao a desfazem.
 */
public final class TimeStop {
    public static final int INTERVAL_TICKS = 20;
    /** Altura do cilindro para cada lado do centro. Cobre salao de pe-direito alto e mezanino. */
    public static final double HEIGHT = 12;

    /**
     * O congelamento dura tres pulsos e so e renovado quando cai abaixo de um pulso e um quarto: um
     * pacote de efeito a cada ~2 s por pessoa, e nao um por segundo. Quem sai da zona solta em ate 3 s
     * (ou na hora, quando o tempo e devolvido — ver {@link #release}).
     */
    private static final int PLAYER_FREEZE_TICKS = INTERVAL_TICKS * 3;
    private static final int PLAYER_RENEW_BELOW = INTERVAL_TICKS + 5;
    private static final int MOB_INTERVAL_TICKS = INTERVAL_TICKS * 2;
    private static final int MOB_FREEZE_TICKS = MOB_INTERVAL_TICKS * 2;
    private static final int MOB_RENEW_BELOW = MOB_INTERVAL_TICKS + 5;
    /** Tres pulsos: um pacote perdido nao apaga o selo de quem esta olhando. */
    private static final int VISUAL_TICKS = INTERVAL_TICKS * 3;
    private static final int MAX_CREATURES = 64;

    private static final String KEY = AurorionMagia.MOD_ID + ":tempo_suspenso";
    private static final String KEY_X = "X";
    private static final String KEY_Y = "Y";
    private static final String KEY_Z = "Z";
    private static final String KEY_DIM = "Dim";
    private static final String KEY_RADIUS = "Radius";

    private TimeStop() {
    }

    public static boolean isActive(LivingEntity caster) {
        return caster.hasEffect(MagiaEffects.TIME_STOP) && tagOf(caster) != null;
    }

    public static void start(ServerLevel level, LivingEntity caster, float radius) {
        Vec3 center = caster.position();
        CompoundTag tag = new CompoundTag();
        tag.putDouble(KEY_X, center.x);
        tag.putDouble(KEY_Y, center.y);
        tag.putDouble(KEY_Z, center.z);
        tag.putString(KEY_DIM, dimension(level));
        tag.putFloat(KEY_RADIUS, radius);
        caster.getPersistentData().put(KEY, tag);
        caster.addEffect(new MobEffectInstance(MagiaEffects.TIME_STOP, MobEffectInstance.INFINITE_DURATION, 0,
                false, false, false));

        sound(level, center, SoundEvents.BELL_RESONATE, 3.0f, 0.35f);
        sound(level, center, SoundEvents.SCULK_SHRIEKER_SHRIEK, 2.0f, 0.45f);
        sound(level, center, SoundEvents.WARDEN_NEARBY_CLOSEST, 2.0f, 0.5f);
        pulse(caster, level, tag, true);
    }

    /**
     * Desliga. Tirar o efeito dispara o {@code MobEffectEvent.Remove}, e e ele que chama
     * {@link #release} — o mesmo caminho do fim por morte ou por {@code /effect clear}.
     */
    public static void stop(LivingEntity caster) {
        if (caster.hasEffect(MagiaEffects.TIME_STOP)) {
            caster.removeEffect(MagiaEffects.TIME_STOP);
        } else {
            release(caster);
        }
    }

    /**
     * Um pulso, chamado pelo tick do efeito do conjurador.
     *
     * @return {@code false} quando a zona tem que acabar; o efeito devolve isso ao vanilla, que o
     *         remove sem ninguem chamar {@code removeEffect} de dentro do proprio tick
     */
    public static boolean pulse(LivingEntity caster) {
        if (!(caster.level() instanceof ServerLevel level)) return true;
        CompoundTag tag = tagOf(caster);
        if (tag == null || !caster.isAlive() || !tag.getString(KEY_DIM).equals(dimension(level))) return false;
        // Metade dos pulsos cuida de criatura; o relogio e o tick do proprio conjurador.
        pulse(caster, level, tag, caster.tickCount % MOB_INTERVAL_TICKS < INTERVAL_TICKS);
        return true;
    }

    private static void pulse(LivingEntity caster, ServerLevel level, CompoundTag tag, boolean creatures) {
        Vec3 center = center(tag);
        float radius = tag.getFloat(KEY_RADIUS);
        double radiusSqr = radius * radius;

        for (ServerPlayer player : level.players()) {
            if (player != caster && player.isAlive() && !player.isSpectator() && !player.isCreative()
                    && inside(player, center, radiusSqr)) {
                FrozenLink.freezeRenewing(player, PLAYER_FREEZE_TICKS, PLAYER_RENEW_BELOW, caster);
            }
        }

        AABB box = box(center, radius);
        if (creatures) {
            List<LivingEntity> mobs = level.getEntitiesOfClass(LivingEntity.class, box,
                    target -> !(target instanceof Player) && target != caster && target.isAlive()
                            && !AurorionSpell.untouchable(target) && !Displacement.isImmune(target)
                            && inside(target, center, radiusSqr));
            for (int i = 0; i < Math.min(MAX_CREATURES, mobs.size()); i++) {
                FrozenLink.freezeRenewing(mobs.get(i), MOB_FREEZE_TICKS, MOB_RENEW_BELOW, caster);
            }
        }

        // O que entra voando para no ar. As flechas de quem parou o tempo continuam: so ele se move.
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, box,
                projectile -> projectile.getOwner() != caster && inside(projectile, center, radiusSqr))) {
            AurorionSpell.launch(projectile, Vec3.ZERO);
        }

        MagiaNetwork.sendVisualAt(level, caster, SpellVisualPayload.Kind.TEMPUS_SISTERE, VISUAL_TICKS, center, radius);
    }

    /**
     * O tempo volta a correr: todo mundo na zona descongela na hora, em vez de esperar o prazo do
     * ultimo pulso. So sai o congelamento curto que a propria zona poe — quem a staff congelou por
     * {@code /freeze} (sem prazo, ou com prazo longo) continua congelado.
     */
    public static void release(LivingEntity caster) {
        CompoundTag tag = tagOf(caster);
        if (tag == null) return;
        caster.getPersistentData().remove(KEY);
        if (!(caster.level() instanceof ServerLevel level) || !tag.getString(KEY_DIM).equals(dimension(level))) return;

        Vec3 center = center(tag);
        float radius = tag.getFloat(KEY_RADIUS);
        double radiusSqr = radius * radius;
        for (ServerPlayer player : level.players()) {
            if (player != caster && inside(player, center, radiusSqr)) FrozenLink.thaw(player, MOB_FREEZE_TICKS);
        }
        for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, box(center, radius),
                target -> !(target instanceof Player) && inside(target, center, radiusSqr))) {
            FrozenLink.thaw(mob, MOB_FREEZE_TICKS);
        }

        sound(level, center, SoundEvents.BEACON_DEACTIVATE, 2.0f, 0.5f);
        sound(level, center, SoundEvents.BELL_RESONATE, 1.5f, 0.8f);
        // ttl zero: "este visual acabou" (ver ClientSpellVisuals.accept).
        MagiaNetwork.sendVisualAt(level, caster, SpellVisualPayload.Kind.TEMPUS_SISTERE, 0, center, radius);
        if (caster instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("aurorion_magia.tempo_volta"), true);
        }
    }

    private static boolean inside(Entity entity, Vec3 center, double radiusSqr) {
        double dx = entity.getX() - center.x;
        double dz = entity.getZ() - center.z;
        return dx * dx + dz * dz <= radiusSqr && Math.abs(entity.getY() - center.y) <= HEIGHT;
    }

    private static AABB box(Vec3 center, double radius) {
        return new AABB(center.x - radius, center.y - HEIGHT, center.z - radius,
                center.x + radius, center.y + HEIGHT, center.z + radius);
    }

    private static Vec3 center(CompoundTag tag) {
        return new Vec3(tag.getDouble(KEY_X), tag.getDouble(KEY_Y), tag.getDouble(KEY_Z));
    }

    private static String dimension(ServerLevel level) {
        return level.dimension().location().toString();
    }

    private static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    @Nullable
    private static CompoundTag tagOf(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        return data.contains(KEY, Tag.TAG_COMPOUND) ? data.getCompound(KEY) : null;
    }
}
