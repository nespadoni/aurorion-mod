package com.aurorion.magia.entity;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.registry.MagiaEntities;
import com.aurorion.magia.spell.AurorionSpell;
import com.aurorion.magia.spell.Displacement;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * As tres magias de vento que duram mais de um instante: a <b>barreira</b>, a <b>coluna</b> e o
 * <b>furacao</b>.
 *
 * <h2>Por que uma entidade, e nao um efeito</h2>
 *
 * <p>Todo o resto deste mod e "efeito de status no alvo", porque o alvo ja e conhecido na
 * conjuracao. Estas tres nao tem alvo: elas sao <i>um lugar</i> que reage a quem passar por ele
 * depois — e o furacao ainda anda. Isso precisa de um relogio, e a regra do modulo e nao assinar
 * {@code ServerTickEvent} (SDD §5.1).
 *
 * <p>Entidade resolve exatamente isso pelo caminho do vanilla: o jogo ja tica entidade carregada, ja
 * a sincroniza para quem esta perto, ja a salva no chunk e ja a descarrega junto com ele. Uma zona
 * parada custa o mesmo que um item no chao; nao existindo zona nenhuma, custa zero.
 *
 * <h2>Zero pacote nosso</h2>
 *
 * <p>Nada aqui usa o {@code SpellVisualPayload}. O que o cliente precisa saber cabe em quatro campos
 * que so mudam <b>uma vez</b>, no nascimento: forma, raio, altura e duracao total. O relogio corre
 * sozinho dos dois lados, a partir do {@code tickCount} que o vanilla ja incrementa em cada entidade
 * — e por isso a zona nao gera um pacote de sincronizacao por tick. A posicao do furacao vem do
 * rastreio de entidade normal. As particulas saem do {@link #tick()} do lado do cliente e a
 * geometria, do {@code SpellZoneRenderer}.
 *
 * <p>Um unico {@link EntityType} para as tres formas, e nao tres: {@link Shape} e um byte
 * sincronizado. Registro sincronizado e conteudo que nunca mais sai do modpack (SDD §6.1) — tres
 * entradas onde uma resolve seria divida permanente por nada.
 */
public class SpellZoneEntity extends Entity {
    /** O golpe do furacao, com mensagem de morte propria. */
    public static final ResourceKey<DamageType> DAMAGE_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE, AurorionMagia.id("turbo_ventorum"));

    private static final EntityDataAccessor<Byte> DATA_SHAPE =
            SynchedEntityData.defineId(SpellZoneEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(SpellZoneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEIGHT =
            SynchedEntityData.defineId(SpellZoneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_DURATION =
            SynchedEntityData.defineId(SpellZoneEntity.class, EntityDataSerializers.INT);

    private static final String KEY_SHAPE = "Shape";
    private static final String KEY_RADIUS = "Radius";
    private static final String KEY_HEIGHT = "Height";
    private static final String KEY_DURATION = "Duration";
    private static final String KEY_AGE = "Age";
    private static final String KEY_OWNER = "Owner";
    private static final String KEY_POWER = "Power";
    private static final String KEY_DRIFT_X = "DriftX";
    private static final String KEY_DRIFT_Z = "DriftZ";

    /** Teto de quem uma zona mexe por tick. Uma praça cheia nao pode virar 80 vetores por tick. */
    private static final int MAX_AFFECTED = 20;
    /** Dano do furacao por segundo em quem fica dentro dele. */
    private static final float STORM_DAMAGE = 2;
    private static final int STORM_HURT_INTERVAL = 20;
    /** A que distancia a frente o furacao procura parede. */
    private static final double WALL_LOOKAHEAD = 2;
    /** Abertura e fechamento do desenho, em ticks. */
    private static final float OPEN_TICKS = 6;
    private static final float CLOSE_TICKS = 10;

    private float power = 1;
    @Nullable
    private UUID ownerId;
    private Vec3 drift = Vec3.ZERO;

    /**
     * Ultimo tick em que o cliente soltou particulas desta zona.
     *
     * <p>O renderizador roda <b>por quadro</b>, e nao por tick: a 120 fps ele passaria seis vezes pelo
     * mesmo tick e soltaria seis levas de particulas onde deveria haver uma. Este campo e o trinco.
     * Vive so no cliente, nao e sincronizado e nao e salvo — no servidor ele fica em -1 para sempre.
     */
    private int lastParticleTick = -1;

    public SpellZoneEntity(EntityType<? extends SpellZoneEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.blocksBuilding = false;
    }

    /** As tres formas. A ordem vai no byte sincronizado: entradas novas entram no fim. */
    public enum Shape {
        /** Barreira: empurra para fora e nao deixa reentrar. */
        WARD,
        /** Coluna: quem entra sobe e desce leve. */
        COLUMN,
        /** Furacao: anda em linha reta puxando quem esta no caminho. */
        STORM;

        private static final Shape[] VALUES = values();

        static Shape byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : WARD;
        }
    }

    // --- Criacao ------------------------------------------------------------------------------

    public static SpellZoneEntity create(ServerLevel level, LivingEntity owner, Shape shape, Vec3 at,
                                         float radius, float height, int duration, float power, Vec3 drift) {
        SpellZoneEntity zone = new SpellZoneEntity(MagiaEntities.SPELL_ZONE.get(), level);
        zone.setPos(at.x, at.y, at.z);
        zone.entityData.set(DATA_SHAPE, (byte) shape.ordinal());
        zone.entityData.set(DATA_RADIUS, radius);
        zone.entityData.set(DATA_HEIGHT, height);
        zone.entityData.set(DATA_DURATION, Math.max(1, duration));
        zone.power = power;
        zone.ownerId = owner.getUUID();
        zone.drift = drift;
        level.addFreshEntity(zone);
        return zone;
    }

    // --- Estado -------------------------------------------------------------------------------

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SHAPE, (byte) 0);
        builder.define(DATA_RADIUS, 3F);
        builder.define(DATA_HEIGHT, 3F);
        builder.define(DATA_DURATION, 1);
    }

    public Shape shape() {
        return Shape.byId(entityData.get(DATA_SHAPE));
    }

    public float radius() {
        return entityData.get(DATA_RADIUS);
    }

    public float height() {
        return entityData.get(DATA_HEIGHT);
    }

    public int duration() {
        return entityData.get(DATA_DURATION);
    }

    /** 0 ao nascer, 1 no auge, 0 de novo ao morrer: abre e fecha em vez de piscar na tela. */
    public float fade(float partial) {
        float age = tickCount + partial;
        return Mth.clamp(age / OPEN_TICKS, 0, 1) * Mth.clamp((duration() - age) / CLOSE_TICKS, 0, 1);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    /** Nao e alvo de nada: nem de flecha, nem de magia, nem de mob. */
    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 64 * 64;
    }

    /** A barreira protege quem a ergueu: o dono passa pelo proprio vento. */
    public boolean isOwner(Entity entity) {
        return ownerId != null && ownerId.equals(entity.getUUID());
    }

    /** {@code true} uma vez por tick, para o renderizador nao soltar uma leva de particulas por quadro. */
    public boolean claimParticleTick() {
        if (lastParticleTick == tickCount) return false;
        lastParticleTick = tickCount;
        return true;
    }

    // --- Relogio ------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        // O cliente so desenha; quem conta a vida e o tickCount, que corre igual dos dois lados.
        if (level().isClientSide) return;
        if (tickCount >= duration()) {
            expire();
            return;
        }
        switch (shape()) {
            case WARD -> ward();
            case COLUMN -> column();
            case STORM -> storm();
        }
    }

    private void expire() {
        if (shape() == Shape.WARD) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.9F, 1.4F);
        }
        discard();
    }

    /** "Saia de perto de mim": empurra para fora do circulo e nao deixa reentrar. */
    private void ward() {
        double radius = radius();
        for (LivingEntity victim : inside(radius, height())) {
            Vec3 outward = victim.position().subtract(position());
            double distance = Math.sqrt(outward.x * outward.x + outward.z * outward.z);
            if (distance < 0.05) {
                // Em cima do centro exato nao ha "para fora": escolhe um lado pelo proprio id.
                outward = new Vec3(Math.cos(victim.getId()), 0, Math.sin(victim.getId()));
                distance = 1;
            }
            // Quanto mais fundo dentro do circulo, mais forte o empurrao — quem so encostou na borda
            // e afastado de leve, quem furou ate o centro e jogado de volta.
            double force = 0.35 + 0.55 * (1 - distance / radius);
            AurorionSpell.launch(victim, new Vec3(outward.x / distance * force, 0.28, outward.z / distance * force));
            victim.resetFallDistance();
        }
    }

    /** A corrente ascendente: quem entra sobe, e desce de pena. */
    private void column() {
        for (LivingEntity victim : inside(radius(), height())) {
            if (victim.getDeltaMovement().y < 0.9) {
                Vec3 motion = victim.getDeltaMovement();
                AurorionSpell.launch(victim, new Vec3(motion.x * 0.6, 0.62 * power, motion.z * 0.6));
            }
            victim.resetFallDistance();
            // Queda lenta pelo tempo que sobra da coluna, com um segundo de folga para pousar.
            int left = Math.max(1, duration() - tickCount);
            victim.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, left + 20, 0, false, false, true));
        }
    }

    /** O furacao: anda em linha reta, puxa para o eixo e machuca quem fica dentro. */
    private void storm() {
        if (blockedAhead()) {
            // Bateu em parede: a tempestade se desfaz ali, em vez de atravessar a muralha.
            expire();
            return;
        }
        setPos(follow(position().add(drift)));

        double radius = radius();
        boolean hurts = tickCount % STORM_HURT_INTERVAL == 0;
        for (LivingEntity victim : inside(radius, height())) {
            Vec3 inward = position().subtract(victim.position());
            double distance = Math.sqrt(inward.x * inward.x + inward.z * inward.z);
            double pull = 0.22 + 0.25 * (distance / radius);
            double x = distance < 0.05 ? 0 : inward.x / distance * pull;
            double z = distance < 0.05 ? 0 : inward.z / distance * pull;
            AurorionSpell.launch(victim, victim.getDeltaMovement().scale(0.5).add(x, 0.16, z));
            victim.resetFallDistance();
            if (hurts) bruise(victim);
        }
        if (tickCount % 6 == 0) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_FLAP,
                    SoundSource.PLAYERS, 1.1F, 0.55F);
        }
    }

    /**
     * O golpe do vento. Sai com o tipo {@code aurorion_magia:turbo_ventorum}, que <b>nomeia quem
     * conjurou</b> na mensagem de morte: morrer no turbilhao e morrer pela mao de quem o levantou.
     * Armadura e encantamento continuam valendo — o vento nao ignora nada.
     */
    private void bruise(LivingEntity victim) {
        DamageSources.applyDamage(victim, STORM_DAMAGE * power, new DamageSource(
                level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE_TYPE),
                owner()));
    }

    /**
     * O furacao acompanha o relevo: procura chao de tres blocos acima a tres abaixo da altura atual.
     * Sem isso ele sairia voando sobre um desnivel ou entraria no chao numa subida.
     */
    private Vec3 follow(Vec3 at) {
        BlockPos from = BlockPos.containing(at.x, at.y + 3, at.z);
        for (int step = 0; step <= 6; step++) {
            BlockPos pos = from.below(step);
            if (!level().getBlockState(pos).getCollisionShape(level(), pos).isEmpty()) {
                return new Vec3(at.x, pos.getY() + 1, at.z);
            }
        }
        return at;
    }

    /**
     * Parede dois blocos a frente: e ali que a tempestade morre.
     *
     * <p>Exige solido <b>nas duas alturas</b>, peito e cabeca. Um tronco de arvore, um poste de cerca
     * ou uma quina de muro tem so uma delas, e um furacao que se desfaz no primeiro tronco de uma
     * floresta nao e uma magia ofensiva, e um fogo de artificio. Muralha e parede de predio tem as
     * duas, e e nelas que ele para.
     */
    private boolean blockedAhead() {
        if (drift.lengthSqr() < 1.0E-6) return false;
        Vec3 ahead = position().add(drift.normalize().scale(WALL_LOOKAHEAD));
        return solid(ahead.add(0, 1, 0)) && solid(ahead.add(0, 2.5, 0));
    }

    private boolean solid(Vec3 at) {
        BlockPos pos = BlockPos.containing(at);
        return !level().getBlockState(pos).getCollisionShape(level(), pos).isEmpty();
    }

    /**
     * Quem a zona alcanca agora. Fora sempre: quem conjurou, espectador, criativo, invulneravel (NPC
     * de oficio, manequim) e chefe ({@code imune_deslocamento}) — nenhuma magia de area do mod
     * arrasta chefe.
     */
    private List<LivingEntity> inside(double radius, double height) {
        Vec3 center = position();
        AABB box = new AABB(center.x - radius, center.y - 1, center.z - radius,
                center.x + radius, center.y + height, center.z + radius);
        double radiusSqr = radius * radius;
        List<LivingEntity> found = level().getEntitiesOfClass(LivingEntity.class, box, victim -> {
            if (!victim.isAlive() || victim.isSpectator() || AurorionSpell.untouchable(victim)) return false;
            if (victim instanceof Player player && player.isCreative()) return false;
            if (Displacement.isImmune(victim) || isOwner(victim)) return false;
            double dx = victim.getX() - center.x;
            double dz = victim.getZ() - center.z;
            return dx * dx + dz * dz <= radiusSqr;
        });
        return found.size() <= MAX_AFFECTED ? found : found.subList(0, MAX_AFFECTED);
    }

    @Nullable
    private LivingEntity owner() {
        return ownerId != null && level() instanceof ServerLevel server
                && server.getEntity(ownerId) instanceof LivingEntity living ? living : null;
    }

    // --- Persistencia -------------------------------------------------------------------------
    // O tipo e noSave(): uma zona de cinco segundos nao volta depois de um restart, e um furacao
    // ressuscitado no meio da praça horas depois seria um bug, nao uma feature. Os dois metodos
    // continuam aqui porque o contrato de Entity os exige, e porque e por eles que a zona sobrevive
    // ao unico caso em que o vanilla realmente serializa uma entidade que nao se salva: a troca de
    // dimensao do jogador que a criou levando o chunk junto.

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(DATA_SHAPE, tag.getByte(KEY_SHAPE));
        entityData.set(DATA_RADIUS, tag.getFloat(KEY_RADIUS));
        entityData.set(DATA_HEIGHT, tag.getFloat(KEY_HEIGHT));
        entityData.set(DATA_DURATION, Math.max(1, tag.getInt(KEY_DURATION)));
        tickCount = tag.getInt(KEY_AGE);
        power = tag.contains(KEY_POWER) ? tag.getFloat(KEY_POWER) : 1;
        ownerId = tag.hasUUID(KEY_OWNER) ? tag.getUUID(KEY_OWNER) : null;
        drift = new Vec3(tag.getDouble(KEY_DRIFT_X), 0, tag.getDouble(KEY_DRIFT_Z));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putByte(KEY_SHAPE, entityData.get(DATA_SHAPE));
        tag.putFloat(KEY_RADIUS, radius());
        tag.putFloat(KEY_HEIGHT, height());
        tag.putInt(KEY_DURATION, duration());
        tag.putInt(KEY_AGE, tickCount);
        tag.putFloat(KEY_POWER, power);
        if (ownerId != null) tag.putUUID(KEY_OWNER, ownerId);
        tag.putDouble(KEY_DRIFT_X, drift.x);
        tag.putDouble(KEY_DRIFT_Z, drift.z);
    }
}
