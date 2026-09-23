package com.aurorion.magia.spell;

import com.aurorion.magia.AurorionMagia;
import io.redspace.ironsspellbooks.api.config.DefaultConfig;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.TargetEntityCastData;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * O que toda magia do Aurorion repete: identidade, config padrao do Iron's, mira e empurrao.
 *
 * <p>A config padrao (escola, nivel maximo, raridade, cooldown) e so o ponto de partida: o Iron's gera
 * {@code config/irons_spellbooks_spell_config/aurorion_magia/<magia>.json} e a staff ajusta ali.
 */
public abstract class AurorionSpell extends AbstractSpell {
    protected static final float AIM_ASSIST = 0.35f;

    private final ResourceLocation spellId;
    private final DefaultConfig defaultConfig;
    private final CastType castType;
    private final boolean forbidden;

    protected AurorionSpell(String name, ResourceLocation school, SpellRarity minRarity, int maxLevel,
                            double cooldownSeconds, CastType castType) {
        this(name, school, minRarity, maxLevel, cooldownSeconds, castType, false);
    }

    /**
     * @param forbidden magia proibida: nao se crafta, nao cai em loot e nao vem junto com a escola —
     *                  so a staff concede, uma a uma ({@code /aurorion spells unlock spell}), e o
     *                  pergaminho sai por {@code /createScroll}. Ver {@code SpellAccess}.
     */
    protected AurorionSpell(String name, ResourceLocation school, SpellRarity minRarity, int maxLevel,
                            double cooldownSeconds, CastType castType, boolean forbidden) {
        this.spellId = AurorionMagia.id(name);
        this.forbidden = forbidden;
        this.defaultConfig = new DefaultConfig()
                .setMinRarity(minRarity)
                .setSchoolResource(school)
                .setMaxLevel(maxLevel)
                .setCooldownSeconds(cooldownSeconds)
                .setAllowCrafting(!forbidden)
                .build();
        this.castType = castType;
    }

    public final boolean isForbidden() {
        return forbidden;
    }

    /** Proibida nunca e craftavel, nem que alguem ligue {@code allowCrafting} no config do Iron's. */
    @Override
    public boolean allowCrafting() {
        return !forbidden && super.allowCrafting();
    }

    @Override
    public boolean canBeCraftedBy(Player player) {
        return !forbidden && super.canBeCraftedBy(player);
    }

    /** Proibida nao aparece em bau de estrutura nem em drop. */
    @Override
    public boolean allowLooting() {
        return !forbidden && super.allowLooting();
    }

    @Override
    public final ResourceLocation getSpellResource() {
        return spellId;
    }

    @Override
    public final DefaultConfig getDefaultConfig() {
        return defaultConfig;
    }

    @Override
    public final CastType getCastType() {
        return castType;
    }

    /**
     * Mira do Iron's: UM raio com a hitbox inflada para ajudar quem mira. Grava o alvo no
     * {@code TargetEntityCastData} da conjuracao, que {@link #target} le depois.
     *
     * @param allowAllies {@code true} para magias que tambem servem para ajudar (Transpositio)
     */
    protected boolean aim(Level level, LivingEntity caster, MagicData data, int range, boolean allowAllies,
                          Predicate<LivingEntity> filter) {
        return Utils.preCastTargetHelper(level, caster, data, this, range, AIM_ASSIST, true,
                target -> target != caster
                        && (allowAllies || !DamageSources.isFriendlyFireBetween(caster, target))
                        && filter.test(target));
    }

    /** O alvo escolhido na mira; para mob conjurador, o alvo da IA. */
    @Nullable
    protected static LivingEntity target(ServerLevel level, LivingEntity caster, @Nullable MagicData data) {
        if (data != null && data.getAdditionalCastData() instanceof TargetEntityCastData cast) {
            LivingEntity target = cast.getTarget(level);
            if (target != null && target.isAlive()) return target;
            return null;
        }
        return caster instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive() ? mob.getTarget() : null;
    }

    /**
     * Troca a velocidade de uma entidade. {@code hurtMarked} faz o servidor mandar o novo vetor ao
     * cliente — obrigatorio para jogador, cujo movimento e decidido pelo proprio cliente.
     */
    public static void launch(Entity entity, Vec3 velocity) {
        entity.setDeltaMovement(velocity);
        entity.hasImpulse = true;
        entity.hurtMarked = true;
    }

    /**
     * Velocidade atual. Para jogador, o {@code deltaMovement} do servidor nao acompanha o andar (quem
     * move e o cliente); a diferenca de posicao desde o tick anterior e a melhor estimativa, e fica
     * a maior das duas.
     */
    public static Vec3 motionOf(Entity entity) {
        Vec3 delta = entity.getDeltaMovement();
        if (!(entity instanceof ServerPlayer)) return delta;
        Vec3 moved = new Vec3(entity.getX() - entity.xo, entity.getY() - entity.yo, entity.getZ() - entity.zo);
        return moved.lengthSqr() > delta.lengthSqr() ? moved : delta;
    }

    protected static void sound(Entity at, SoundEvent sound, float volume, float pitch) {
        at.level().playSound(null, at.getX(), at.getY(), at.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    protected static void sound(Level level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }
}
