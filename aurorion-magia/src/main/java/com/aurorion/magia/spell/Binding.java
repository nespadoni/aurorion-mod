package com.aurorion.magia.spell;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import com.aurorion.magia.registry.MagiaEffects;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A corrente do Vinculum Carnificis.
 *
 * <p>Quem esta preso anda, bate, conjura e se defende a vontade — dentro do raio. Ao cruzar a borda,
 * leva um puxao na direcao da ancora <b>e sangra</b>: a corrente nao e um muro macio, e ferro
 * fechado na carne. Cada puxao seguido aumenta a "tensao", e o proximo vem mais forte e dai mais
 * caro. Ficar dentro do raio alivia a tensao aos poucos.
 *
 * <p>O dano tem um intervalo proprio de {@value #HURT_INTERVAL} ticks, independente do tick da
 * corrente (5x por segundo): quem insiste na borda leva <b>um</b> golpe por segundo, nao cinco. Sai
 * com o tipo {@code aurorion_magia:vinculum_carnificis}, que ignora armadura e escudo e nomeia quem
 * prendeu na mensagem de morte — morrer na corrente e morrer pela mao de quem a lancou.
 *
 * <p>Teleporte nao salva: perola, chorus e teleportes de magia sao cancelados pelo
 * {@code MagiaServerEvents}; e se alguma coisa colocar o preso muito longe mesmo assim (um mod de
 * teleporte sem evento), a corrente o arranca de volta <b>para a propria ancora</b> — o unico ponto
 * que a magia sabe ser chao firme, porque foi de la que o alvo foi preso.
 *
 * <p>A ancora mora no {@code persistentData} do preso, ao lado do efeito que tambem e salvo:
 * deslogar preso e voltar continua preso, no mesmo lugar.
 *
 * <p><b>Prazo.</b> A corrente fica ate alguem conjurar o Vinculum de novo no preso ({@link #unbind}),
 * ate ele morrer ou ate {@value #MAX_DURATION} ticks (5 min). Leite e totem nao a abrem. Antes ela nao
 * tinha prazo nenhum: prender nao abre cooldown ({@link ToggleCooldown}), e um conjurador prendia
 * meia praça e ia embora. Como o visual no cliente tem prazo, a propria corrente o renova a cada
 * {@value #VISUAL_REFRESH} ticks — e assim tambem quem chega perto depois passa a ver a ancora.
 *
 * <h2>Limites</h2>
 *
 * <ul>
 *   <li><b>{@value #MAX_PER_CASTER} correntes por conjurador</b> ao mesmo tempo. A contagem e so em
 *       memoria, refeita pelo proprio tick de cada preso ({@link #enforce}) — depois de um restart ela
 *       se reconstroi em um segundo, sem varredura.</li>
 *   <li><b>{@value #REBIND_COOLDOWN} ticks</b> sem poder ser preso de novo depois de solto: soltar e
 *       prender o mesmo alvo em sequencia nao vira prisao perpetua.</li>
 *   <li><b>Zona segura e refugio</b>: o preso num lugar onde PvP nao vale sai da corrente. Puxar e
 *       sangrar dentro da zona segura e justamente o que ela proibe.</li>
 *   <li><b>Sem dono, sem corrente</b>: quem prendeu saiu do jogo ou morreu, e a corrente se abre. O
 *       sangramento sai em nome do dono; sem ele, sairia sem causador e passaria pela regra de PvP.</li>
 *   <li>O sangramento respeita armadura (saiu de {@code bypasses_armor}); so o escudo nao segura, a
 *       corrente nao vem de frente.</li>
 * </ul>
 */
public final class Binding {
    public static final ResourceKey<DamageType> DAMAGE_TYPE =
            ResourceKey.create(Registries.DAMAGE_TYPE, AurorionMagia.id("vinculum_carnificis"));

    private static final String KEY = AurorionMagia.MOD_ID + ":vinculo";
    private static final String KEY_X = "X";
    private static final String KEY_Y = "Y";
    private static final String KEY_Z = "Z";
    private static final String KEY_DIM = "Dim";
    private static final String KEY_RADIUS = "Radius";
    private static final String KEY_STRAIN = "Strain";
    private static final String KEY_OWNER = "Owner";
    private static final String KEY_HURT_AT = "HurtAt";
    private static final String KEY_VISUAL_AT = "VisualAt";

    /** Prazo do visual no cliente; renovado na metade, entao um pacote perdido nao apaga nada. */
    private static final int VISUAL_TICKS = 200;
    private static final int VISUAL_REFRESH = 100;

    /** Alem do raio mais isto, nao e fuga a pe: foi teleporte. Volta para a ancora na hora. */
    private static final double SNAP_BACK = 8;
    private static final int MAX_STRAIN = 8;
    /** Um golpe por segundo, no maximo, por mais que o preso force a borda. */
    private static final int HURT_INTERVAL = 20;
    /** 3 de dano no primeiro puxao, +1 por ponto de tensao: 11 (cinco coracoes e meio) no limite. */
    private static final float HURT_BASE = 3;
    private static final float HURT_PER_STRAIN = 1;

    /** 5 minutos de corrente, no maximo. */
    public static final int MAX_DURATION = 20 * 60 * 5;
    /** Correntes simultaneas por conjurador. */
    public static final int MAX_PER_CASTER = 3;
    /** 30 s sem poder ser preso de novo depois de solto. */
    public static final int REBIND_COOLDOWN = 20 * 30;
    /** Fora da corrente ate este tick do jogo. Fora do tag da ancora, que sai junto com a corrente. */
    private static final String KEY_FREE_UNTIL = AurorionMagia.MOD_ID + ":vinculo_livre_ate";
    /** Preso que nao ticou por este tempo (deslogou, descarregou) deixa de contar no limite. */
    private static final int SEEN_TIMEOUT = 100;
    /** Dono e zona segura conferidos uma vez por segundo, nao a cada tick da corrente. */
    private static final int CHECK_INTERVAL = 20;

    /** Dono -> (preso -> ultimo tick visto). So a thread do servidor mexe aqui. */
    private static final Map<UUID, Map<UUID, Long>> BY_OWNER = new HashMap<>();

    private Binding() {
    }

    /**
     * Por que {@code caster} nao pode prender {@code target} agora, ou {@code null} se pode. A chave
     * de traducao vai para a barra de acao de quem conjurou.
     */
    @Nullable
    public static String refusal(LivingEntity caster, LivingEntity target) {
        long now = target.level().getGameTime();
        if (target.getPersistentData().getLong(KEY_FREE_UNTIL) > now) return "aurorion_magia.corrente_recente";
        if (active(caster.getUUID(), now) >= MAX_PER_CASTER) return "aurorion_magia.corrente_limite";
        return null;
    }

    public static void bind(LivingEntity target, LivingEntity caster, float radius) {
        Vec3 anchor = target.position();
        CompoundTag tag = new CompoundTag();
        tag.putDouble(KEY_X, anchor.x);
        tag.putDouble(KEY_Y, anchor.y);
        tag.putDouble(KEY_Z, anchor.z);
        tag.putString(KEY_DIM, target.level().dimension().location().toString());
        tag.putFloat(KEY_RADIUS, radius);
        tag.putUUID(KEY_OWNER, caster.getUUID());
        tag.putLong(KEY_VISUAL_AT, target.level().getGameTime());
        target.getPersistentData().put(KEY, tag);

        target.addEffect(new MobEffectInstance(MagiaEffects.BOUND, MAX_DURATION, 0,
                false, false, true), caster);
        seen(caster.getUUID(), target.getUUID(), target.level().getGameTime());
        MagiaNetwork.sendVisual(caster, target, SpellVisualPayload.Kind.VINCULUM, VISUAL_TICKS, anchor, radius);
    }

    /**
     * A segunda conjuracao: a corrente se abre. Tirar o efeito dispara o {@code MobEffectEvent.Remove},
     * que chama {@link #release} — o mesmo caminho da morte e do {@code /effect clear}.
     */
    public static void unbind(LivingEntity target) {
        target.removeEffect(MagiaEffects.BOUND);
        AurorionSpell.sound(target, SoundEvents.CHAIN_BREAK, 1.0f, 1.2f);
        if (target instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("aurorion_magia.corrente_solta"), true);
        }
    }

    /**
     * Chamado pelo tick do efeito, 5 vezes por segundo, so em quem esta preso.
     *
     * @return {@code false} quando a corrente deve se abrir (dono fora, preso na zona segura). O efeito
     *         sai pelo retorno do tick — {@code removeEffect} aqui dentro mexeria na lista de efeitos
     *         que o vanilla esta percorrendo.
     */
    public static boolean enforce(LivingEntity entity) {
        CompoundTag tag = tagOf(entity);
        if (tag == null || !(entity.level() instanceof ServerLevel level)) return true;
        if (tag.hasUUID(KEY_OWNER)) seen(tag.getUUID(KEY_OWNER), entity.getUUID(), level.getGameTime());
        if (entity.tickCount % CHECK_INTERVAL == 0) {
            Entity owner = tag.hasUUID(KEY_OWNER) ? Owners.find(level, tag.getUUID(KEY_OWNER)) : null;
            if (owner == null || !Hits.pvpAllowedFrom(owner, entity)) {
                if (entity instanceof ServerPlayer player) {
                    player.displayClientMessage(Component.translatable("aurorion_magia.corrente_solta"), true);
                }
                AurorionSpell.sound(entity, SoundEvents.CHAIN_BREAK, 1.0f, 1.2f);
                return false;
            }
        }
        if (!tag.getString(KEY_DIM).equals(level.dimension().location().toString())) return true;

        Vec3 anchor = new Vec3(tag.getDouble(KEY_X), tag.getDouble(KEY_Y), tag.getDouble(KEY_Z));
        float radius = tag.getFloat(KEY_RADIUS);
        refreshVisual(level, entity, tag, anchor, radius);
        Vec3 offset = entity.position().subtract(anchor);
        double distance = offset.length();
        int strain = tag.getInt(KEY_STRAIN);

        if (distance <= radius) {
            if (strain > 0) tag.putInt(KEY_STRAIN, strain - 1);
            return true;
        }

        strain = Math.min(MAX_STRAIN, strain + 1);
        tag.putInt(KEY_STRAIN, strain);
        Vec3 inward = offset.scale(-1 / distance);

        if (distance > radius + SNAP_BACK) {
            // Longe demais para ter sido a pe. Volta para a ancora, e nao para a borda: a ancora e o
            // unico ponto que a magia sabe ser chao firme (foi onde o alvo estava quando foi preso).
            // Cair de um ponto qualquer da borda e o que fazia gente despencar do nada.
            moveTo(level, entity, anchor);
        } else {
            // Quanto mais passou da borda e quanto mais vezes ja tentou, mais violento.
            double force = Math.min(2.6, 0.6 + 0.2 * strain + (distance - radius) * 0.3);
            AurorionSpell.launch(entity, inward.scale(force).add(0, 0.2 + 0.03 * strain, 0));
        }
        entity.resetFallDistance();
        bleed(level, entity, tag, strain);
        snap(entity, anchor, strain);
        return true;
    }

    /** Perola ou teleporte negado: mesmo estalo do puxao, sem mover ninguem. */
    public static void refuseTeleport(LivingEntity entity) {
        CompoundTag tag = tagOf(entity);
        if (tag == null) return;
        Vec3 anchor = new Vec3(tag.getDouble(KEY_X), tag.getDouble(KEY_Y), tag.getDouble(KEY_Z));
        snap(entity, anchor, MAX_STRAIN);
    }

    public static boolean isBound(Entity entity) {
        return entity instanceof LivingEntity living && living.hasEffect(MagiaEffects.BOUND) && tagOf(living) != null;
    }

    /** A corrente acabou (segunda conjuracao, morte, {@code /effect clear}): o visual some junto. */
    public static void release(LivingEntity entity) {
        CompoundTag tag = tagOf(entity);
        if (tag == null) return;
        entity.getPersistentData().remove(KEY);
        entity.getPersistentData().putLong(KEY_FREE_UNTIL, entity.level().getGameTime() + REBIND_COOLDOWN);
        if (tag.hasUUID(KEY_OWNER)) {
            Map<UUID, Long> held = BY_OWNER.get(tag.getUUID(KEY_OWNER));
            if (held != null) {
                held.remove(entity.getUUID());
                if (held.isEmpty()) BY_OWNER.remove(tag.getUUID(KEY_OWNER));
            }
        }
        Vec3 anchor = new Vec3(tag.getDouble(KEY_X), tag.getDouble(KEY_Y), tag.getDouble(KEY_Z));
        // ttl zero: "este visual acabou" (ver ClientSpellVisuals.accept).
        MagiaNetwork.sendVisual(null, entity, SpellVisualPayload.Kind.VINCULUM, 0, anchor, tag.getFloat(KEY_RADIUS));
    }

    /**
     * Renova o visual da ancora antes de ele vencer no cliente. Sai sem conjurador (-1): quem prendeu
     * pode estar longe ou offline, e o cliente estica o visual que ja tem em vez de abrir outro.
     */
    private static void refreshVisual(ServerLevel level, LivingEntity entity, CompoundTag tag, Vec3 anchor,
                                      float radius) {
        long now = level.getGameTime();
        if (now - tag.getLong(KEY_VISUAL_AT) < VISUAL_REFRESH) return;
        tag.putLong(KEY_VISUAL_AT, now);
        MagiaNetwork.sendVisual(null, entity, SpellVisualPayload.Kind.VINCULUM, VISUAL_TICKS, anchor, radius);
    }

    /** O ferro na carne. No maximo uma vez por segundo, por mais que o preso force a borda. */
    private static void bleed(ServerLevel level, LivingEntity entity, CompoundTag tag, int strain) {
        long now = level.getGameTime();
        if (now - tag.getLong(KEY_HURT_AT) < HURT_INTERVAL) return;
        tag.putLong(KEY_HURT_AT, now);

        // Sem dono nao ha sangue: o dano sairia sem causador e passaria pela regra de PvP. A conferencia
        // de zona e refeita aqui, no instante do golpe, e nao so na de uma vez por segundo.
        Entity owner = tag.hasUUID(KEY_OWNER) ? Owners.find(level, tag.getUUID(KEY_OWNER)) : null;
        if (owner == null || !Hits.pvpAllowedFrom(owner, entity)) return;
        DamageSource source = new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE_TYPE), owner);
        entity.hurt(FriendlyFire.source(source, entity), HURT_BASE + HURT_PER_STRAIN * strain);
        AurorionSpell.sound(entity, SoundEvents.PLAYER_HURT, 1.0f, 0.6f);
    }

    /** Jogador pelo teleporte de conexao; o resto direto. Teleportar jogador "na mao" desincroniza. */
    private static void moveTo(ServerLevel level, LivingEntity entity, Vec3 to) {
        if (entity instanceof ServerPlayer player) {
            player.teleportTo(level, to.x, to.y, to.z, player.getYRot(), player.getXRot());
        } else {
            entity.teleportTo(to.x, to.y, to.z);
        }
        AurorionSpell.launch(entity, Vec3.ZERO);
    }

    private static void snap(LivingEntity entity, Vec3 anchor, int strain) {
        AurorionSpell.sound(entity, SoundEvents.CHAIN_BREAK, 1.0f, 0.55f + strain * 0.04f);
        AurorionSpell.sound(entity, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.8f, 0.6f);
        MagiaNetwork.sendVisual(null, entity, SpellVisualPayload.Kind.VINCULUM_SNAP, 8, anchor, strain);
        if (entity instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("aurorion_magia.corrente"), true);
        }
    }

    private static void seen(UUID owner, UUID victim, long now) {
        BY_OWNER.computeIfAbsent(owner, key -> new HashMap<>()).put(victim, now);
    }

    /** Correntes de {@code owner} vistas ha pouco. Limpa as que pararam de ticar. */
    private static int active(UUID owner, long now) {
        Map<UUID, Long> held = BY_OWNER.get(owner);
        if (held == null) return 0;
        held.values().removeIf(at -> now - at > SEEN_TIMEOUT);
        if (held.isEmpty()) BY_OWNER.remove(owner);
        return held.size();
    }

    /** Estado so em memoria: nao passa de um mundo para outro. */
    public static void clear() {
        BY_OWNER.clear();
    }

    @Nullable
    private static CompoundTag tagOf(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        return data.contains(KEY, Tag.TAG_COMPOUND) ? data.getCompound(KEY) : null;
    }
}
