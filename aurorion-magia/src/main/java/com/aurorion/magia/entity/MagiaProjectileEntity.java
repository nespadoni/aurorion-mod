package com.aurorion.magia.entity;

import com.aurorion.magia.registry.MagiaEntities;
import com.aurorion.magia.spell.DoliumArdensSpell;
import com.aurorion.magia.spell.Hits;
import com.aurorion.magia.spell.IncusCaelestisSpell;
import com.aurorion.magia.spell.ManusRapaxSpell;
import com.aurorion.magia.spell.MorsExProfundisSpell;
import com.aurorion.magia.spell.PyrobolusInfernalisSpell;
import com.aurorion.magia.spell.SagittaExploratrixSpell;
import com.aurorion.magia.spell.SphaeraSpiritusSpell;
import com.aurorion.magia.spell.StellaeLaminataeSpell;
import com.aurorion.magia.spell.TemperiesFatiSpell;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Tudo o que as magias novas arremessam: a Esfera Espiritual, o barril do Gragas, a bomba do Ziggs, a
 * bigorna, o shuriken do Zed, o arco da Tempera do Destino e a garra do Blitz.
 *
 * <h2>Por que um tipo so</h2>
 *
 * <p>Pelo mesmo motivo da {@link SpellZoneEntity}: entidade registrada e conteudo sincronizado que nunca
 * mais sai do modpack (SDD §6.1). Sete tipos onde um resolve seria divida permanente. A forma e um
 * byte sincronizado e o tamanho visual um float, os dois definidos no nascimento; o resto (dano, raio
 * da explosao, nivel) vive so no servidor, que e quem decide o que acontece no impacto.
 *
 * <h2>Custo</h2>
 *
 * <p>Cada projetil faz por tick um {@code clip} de blocos do tamanho do proprio passo e uma busca de
 * entidades na caixa desse passo — o mesmo que uma flecha do vanilla. Vive no maximo alguns segundos e
 * nao e salvo no chunk ({@code noSave}). As particulas saem do proprio tick, so no cliente.
 */
public class MagiaProjectileEntity extends Projectile {
    private static final EntityDataAccessor<Byte> DATA_SHAPE =
            SynchedEntityData.defineId(MagiaProjectileEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> DATA_SIZE =
            SynchedEntityData.defineId(MagiaProjectileEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_LANDED =
            SynchedEntityData.defineId(MagiaProjectileEntity.class, EntityDataSerializers.BOOLEAN);

    /** Quanto tempo a bigorna fica no chao antes de sumir. */
    private static final int LANDED_TICKS = 50;
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0f, 0.82f, 0.3f), 1.0f);
    private static final DustParticleOptions SPIRIT = new DustParticleOptions(new Vector3f(0.55f, 0.85f, 1.0f), 1.4f);

    /** As formas. A ordem vai no byte sincronizado: entradas novas entram no fim. */
    public enum Shape {
        /** Esfera Espiritual: reta, lenta, explode no primeiro toque. */
        SPHAERA(0),
        /** Barril Explosivo: arco ate o ponto mirado. */
        DOLIUM(0.05),
        /** Bomba Megainfernal: arco alto e longo. */
        PYROBOLUS(0.05),
        /** Bigorna Celeste: cai do ceu, esmaga, pousa e some. */
        INCUS(0.08),
        /** Shuriken Laminado: reto, atravessa quem acertar. */
        STELLA(0),
        /** Tempera do Destino: arco dourado ate o ponto mirado. */
        TEMPERIES(0.05),
        /** Puxao Bionico: reto, agarra o primeiro que tocar. */
        RAPAX(0),
        /** Morte Vinda das Profundezas: parado no chao, invisivel; detona quando o tempo acaba. */
        ABYSSUS(0),
        /** Flecha de Reconhecimento: parado onde a flecha cravou, pulsa a cada segundo. */
        SPECULA(0);

        private static final Shape[] VALUES = values();
        final double gravity;

        Shape(double gravity) {
            this.gravity = gravity;
        }

        static Shape byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : SPHAERA;
        }
    }

    private float power = 1;
    private float reach = 1;
    private float aux;
    private int life = 100;
    private long castStamp;
    private int landedAt;
    private final IntSet struck = new IntOpenHashSet();

    public MagiaProjectileEntity(EntityType<? extends MagiaProjectileEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /**
     * @param size  tamanho visual, sincronizado (raio da esfera, escala do bloco)
     * @param power dano no impacto, ou a duracao em ticks quando a forma nao fere (Tempera)
     * @param reach raio do efeito no impacto
     * @param life  ticks ate se desfazer sozinho (o que explode, explode ali mesmo)
     */
    public static MagiaProjectileEntity launch(ServerLevel level, LivingEntity owner, Shape shape, Vec3 at, Vec3 velocity,
                                               float size, float power, float reach, int life) {
        MagiaProjectileEntity projectile = new MagiaProjectileEntity(MagiaEntities.PROJECTILE.get(), level);
        projectile.setOwner(owner);
        projectile.setPos(at.x, at.y, at.z);
        projectile.setDeltaMovement(velocity);
        projectile.entityData.set(DATA_SHAPE, (byte) shape.ordinal());
        projectile.entityData.set(DATA_SIZE, size);
        projectile.power = power;
        projectile.reach = reach;
        projectile.life = Math.max(1, life);
        projectile.castStamp = level.getGameTime();
        projectile.turn(velocity);
        level.addFreshEntity(projectile);
        return projectile;
    }

    /** Um numero a mais para o impacto, quando dano e raio nao bastam (limiar de execucao do Pyke). */
    public MagiaProjectileEntity aux(float value) {
        this.aux = value;
        return this;
    }

    /**
     * Velocidade inicial para cair em {@code to} depois de {@code ticks}, com a gravidade da forma. A
     * conta e a do passo discreto deste tick (gravidade antes de andar), e nao a da parabola continua:
     * assim o barril cai onde a mira mostrou, e nao meio bloco antes.
     */
    public static Vec3 arc(Vec3 from, Vec3 to, int ticks, Shape shape) {
        double t = Math.max(1, ticks);
        double vy = (to.y - from.y + shape.gravity * t * (t + 1) / 2) / t;
        return new Vec3((to.x - from.x) / t, vy, (to.z - from.z) / t);
    }

    // --- Estado -------------------------------------------------------------------------------

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SHAPE, (byte) 0);
        builder.define(DATA_SIZE, 0.5F);
        builder.define(DATA_LANDED, false);
    }

    public Shape shape() {
        return Shape.byId(entityData.get(DATA_SHAPE));
    }

    public float size() {
        return entityData.get(DATA_SIZE);
    }

    public boolean landed() {
        return entityData.get(DATA_LANDED);
    }

    /** O instante da conjuracao: dois shurikens da mesma conjuracao tem o mesmo. */
    public long castStamp() {
        return castStamp;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double reach = 64 + size() * 16;
        return distance < reach * reach;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Nullable
    private LivingEntity owner() {
        return getOwner() instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (!(target instanceof LivingEntity living) || !super.canHitEntity(target)) return false;
        Entity owner = getOwner();
        if (owner == null) return living.isAlive();
        if (target == owner || owner.isPassengerOfSameVehicle(target)) return false;
        return !(owner instanceof LivingEntity caster) || Hits.hittable(caster, living);
    }

    // --- Relogio ------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (landed()) {
            if (!level().isClientSide && tickCount - landedAt > LANDED_TICKS) discard();
            return;
        }
        Shape shape = shape();
        Vec3 motion = getDeltaMovement();
        if (shape.gravity > 0) motion = motion.add(0, -shape.gravity, 0);
        setDeltaMovement(motion);

        if (level() instanceof ServerLevel server) {
            if (step(server, shape, motion)) return;
        } else {
            particles(shape);
        }
        setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
        turn(motion);
        if (!level().isClientSide && tickCount >= life) expire(shape);
    }

    /** Um passo no servidor. {@code true} quando o projetil acabou (explodiu, pousou, sumiu). */
    private boolean step(ServerLevel level, Shape shape, Vec3 motion) {
        Vec3 from = position();
        Vec3 to = from.add(motion);
        switch (shape) {
            case ABYSSUS -> {
                return false;
            }
            case SPECULA -> {
                LivingEntity owner = owner();
                if (owner != null && tickCount > 0 && tickCount % SagittaExploratrixSpell.PULSE_TICKS == 0) {
                    SagittaExploratrixSpell.pulse(level, owner, position(), reach, (int) power);
                }
                return false;
            }
            case STELLA -> pierce(level, from, to);
            case INCUS -> crush(level, from, to);
            default -> {
                double inflate = shape == Shape.SPHAERA ? size() : 0.3;
                EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, this, from, to,
                        getBoundingBox().expandTowards(motion).inflate(inflate + 1), this::canHitEntity, (float) inflate);
                if (hit != null && hit.getEntity() instanceof LivingEntity victim) {
                    impact(level, shape, hit.getLocation(), victim);
                    return true;
                }
            }
        }
        BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (block.getType() == HitResult.Type.MISS) return false;
        if (shape == Shape.INCUS) {
            land(level, block.getLocation());
        } else {
            impact(level, shape, block.getLocation(), null);
        }
        return true;
    }

    /** O tempo acabou no ar: o que explode, explode ali; o resto some. */
    private void expire(Shape shape) {
        if (!(level() instanceof ServerLevel level)) return;
        switch (shape) {
            case SPHAERA, DOLIUM, PYROBOLUS, TEMPERIES, ABYSSUS -> impact(level, shape, position(), null);
            default -> discard();
        }
    }

    private void impact(ServerLevel level, Shape shape, Vec3 at, @Nullable LivingEntity hit) {
        LivingEntity owner = owner();
        if (owner != null) {
            switch (shape) {
                case SPHAERA -> SphaeraSpiritusSpell.detonate(level, owner, at, reach, power);
                case DOLIUM -> DoliumArdensSpell.burst(level, owner, at, reach, power);
                case PYROBOLUS -> PyrobolusInfernalisSpell.blast(level, owner, at, reach, power);
                case TEMPERIES -> TemperiesFatiSpell.land(level, owner, at, reach, (int) power);
                case RAPAX -> {
                    if (hit != null) ManusRapaxSpell.grab(level, owner, hit, power);
                }
                case ABYSSUS -> MorsExProfundisSpell.detonate(level, owner, at, getYRot(), reach, power, aux);
                default -> {
                }
            }
        }
        discard();
    }

    /** Shuriken: corta todo mundo que o passo atravessa, uma vez cada. Para no primeiro bloco. */
    private void pierce(ServerLevel level, Vec3 from, Vec3 to) {
        LivingEntity owner = owner();
        if (owner == null) return;
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().expandTowards(to.subtract(from)).inflate(0.35), this::canHitEntity)) {
            if (struck.add(victim.getId())) StellaeLaminataeSpell.cut(level, owner, victim, power, castStamp);
        }
    }

    /** Bigorna: esmaga quem estiver embaixo dela enquanto cai de verdade. */
    private void crush(ServerLevel level, Vec3 from, Vec3 to) {
        if (getDeltaMovement().y > -0.3) return;
        LivingEntity owner = owner();
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().expandTowards(to.subtract(from)), this::canHitEntity)) {
            if (struck.add(victim.getId())) IncusCaelestisSpell.crush(level, owner, victim, power);
        }
    }

    /** A bigorna bate no chao: fica parada, e some depois de {@value #LANDED_TICKS} ticks. */
    private void land(ServerLevel level, Vec3 at) {
        setPos(at.x, at.y, at.z);
        setDeltaMovement(Vec3.ZERO);
        entityData.set(DATA_LANDED, true);
        landedAt = tickCount;
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.6F, 0.7F);
    }

    private void turn(Vec3 motion) {
        if (motion.horizontalDistanceSqr() < 1.0E-6) return;
        setYRot((float) (Mth.atan2(motion.x, motion.z) * Mth.RAD_TO_DEG));
        setXRot((float) (Mth.atan2(motion.y, motion.horizontalDistance()) * Mth.RAD_TO_DEG));
        yRotO = getYRot();
        xRotO = getXRot();
    }

    // --- Cliente ------------------------------------------------------------------------------

    private boolean dustDone;

    private void particles(Shape shape) {
        Level level = level();
        RandomSource random = level.random;
        double x = getX(), y = getY(), z = getZ();
        switch (shape) {
            case SPHAERA -> {
                float radius = size();
                for (int i = 0; i < 3; i++) {
                    Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize();
                    Vec3 p = position().add(dir.scale(radius * (0.9 + random.nextDouble() * 0.3)));
                    level.addParticle(i == 0 ? ParticleTypes.ELECTRIC_SPARK : SPIRIT, p.x, p.y, p.z,
                            -dir.x * 0.05, -dir.y * 0.05, -dir.z * 0.05);
                }
            }
            case DOLIUM -> {
                level.addParticle(ParticleTypes.SMOKE, x, y + 0.4, z, 0, 0.02, 0);
                if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.FLAME, x, y + 0.5, z, 0, 0.02, 0);
            }
            case PYROBOLUS -> {
                level.addParticle(ParticleTypes.LARGE_SMOKE, x, y + 0.6, z, 0, 0.03, 0);
                level.addParticle(ParticleTypes.FLAME, x, y + 0.8, z, 0, 0.04, 0);
            }
            case TEMPERIES -> {
                level.addParticle(GOLD, x, y, z, 0, 0, 0);
                if (random.nextInt(2) == 0) level.addParticle(ParticleTypes.END_ROD, x, y, z,
                        random.nextGaussian() * 0.02, random.nextGaussian() * 0.02, random.nextGaussian() * 0.02);
            }
            case RAPAX -> {
                Entity owner = getOwner();
                if (owner != null) {
                    Vec3 hand = owner.position().add(0, owner.getBbHeight() * 0.7, 0);
                    for (int i = 0; i < 3; i++) {
                        Vec3 p = hand.lerp(position(), random.nextDouble());
                        level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 0, 0, 0);
                    }
                }
            }
            case STELLA -> level.addParticle(ParticleTypes.CRIT, x, y, z, 0, 0, 0);
            case ABYSSUS, SPECULA -> {
            }
            case INCUS -> {
                if (landed() && !dustDone) {
                    dustDone = true;
                    BlockState under = level.getBlockState(BlockPos.containing(x, y - 0.5, z));
                    BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK,
                            under.isAir() ? Blocks.ANVIL.defaultBlockState() : under);
                    for (int i = 0; i < 24; i++) {
                        double angle = i * Math.PI * 2 / 24;
                        level.addParticle(dust, x, y + 0.1, z, Math.cos(angle) * 0.3, 0.15, Math.sin(angle) * 0.3);
                    }
                }
            }
        }
    }

    // --- Persistencia -------------------------------------------------------------------------
    // O tipo e noSave(): um barril em pleno voo nao volta depois de um restart.

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_SHAPE, tag.getByte("Shape"));
        entityData.set(DATA_SIZE, tag.getFloat("Size"));
        power = tag.getFloat("Power");
        reach = tag.getFloat("Reach");
        aux = tag.getFloat("Aux");
        life = Math.max(1, tag.getInt("Life"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("Shape", entityData.get(DATA_SHAPE));
        tag.putFloat("Size", size());
        tag.putFloat("Power", power);
        tag.putFloat("Reach", reach);
        tag.putFloat("Aux", aux);
        tag.putInt("Life", life);
    }
}
