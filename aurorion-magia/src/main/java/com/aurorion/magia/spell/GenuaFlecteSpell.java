package com.aurorion.magia.spell;

import com.aurorion.magia.compat.EmotecraftCompat;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import com.aurorion.magia.registry.MagiaEffects;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;

/**
 * Genua Flecte — "Dobre os joelhos." Prostracao.
 *
 * <p>O alvo e virado de frente para quem conjurou e cai de joelhos. Enquanto durar: quase sem andar,
 * sem pular, sem correr — e, agora, <b>sem usar nada</b>: nenhuma magia sai da boca dele, e nem item,
 * nem arco, nem escudo, nem totem responde na mao. De joelhos, o corpo e da cerimonia, nao dele. So a
 * voz continua livre, de proposito: a magia serve para ouvir um pedido de desculpa, nao para calar.
 *
 * <p><b>Duas conjuracoes</b>: a primeira poe de joelhos, a segunda no mesmo alvo manda levantar. E o
 * professor que libera o aluno, o carrasco que muda de ideia — sem esperar o tempo correr.
 *
 * <p>A pose de joelhos vem do Emotecraft quando instalado (emote embutido no jar, forcado pelo
 * servidor para todos verem); sem ele, o cliente do ajoelhado fica agachado.
 */
public final class GenuaFlecteSpell extends AurorionSpell {
    private static final int RANGE = 14;

    public GenuaFlecteSpell() {
        super("genua_flecte", SchoolRegistry.ELDRITCH_RESOURCE, SpellRarity.RARE, 5, 30, CastType.INSTANT);
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 1;
        this.baseManaCost = 40;
        this.manaCostPerLevel = 8;
        this.castTime = 0;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.EVOKER_CAST_SPELL);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.72f, 0.64f, 0.84f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(Component.translatable("ui.aurorion_magia.duracao", Utils.timeFromTicks(duration(spellLevel), 1)),
                Component.translatable("ui.aurorion_magia.maos_atadas"),
                Component.translatable("ui.aurorion_magia.alternar"),
                Component.translatable("ui.aurorion_magia.alcance", RANGE));
    }

    /** Aliado incluido: mandar levantar tambem e conjurar nele. */
    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        return aim(level, entity, playerMagicData, RANGE, true, target -> !Displacement.isImmune(target));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            LivingEntity target = target(serverLevel, entity, playerMagicData);
            if (target != null) {
                if (target.hasEffect(MagiaEffects.KNEELING)) {
                    rise(target);
                } else {
                    kneel(entity, target, duration(spellLevel));
                }
            }
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    private static void kneel(LivingEntity caster, LivingEntity target, int duration) {
        target.addEffect(new MobEffectInstance(MagiaEffects.KNEELING, duration, 0, false, false, true), caster);
        faceTowards(target, caster);
        if (target instanceof ServerPlayer player) {
            player.setSprinting(false);
            // De joelhos nao se termina o que ja estava comecando: conjuracao e item em uso caem.
            player.stopUsingItem();
            if (MagicData.getPlayerMagicData(player).isCasting()) Utils.serverSideCancelCast(player);
            player.displayClientMessage(Component.translatable("aurorion_magia.de_joelhos"), true);
            EmotecraftCompat.kneel(player);
        } else if (target instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        sound(target, SoundEvents.ANVIL_LAND, 0.35f, 0.45f);
        sound(target, SoundEvents.SOUL_ESCAPE.value(), 1.0f, 0.7f);
        MagiaNetwork.sendVisual(caster, target, SpellVisualPayload.Kind.GENUA_FLECTE, duration);
    }

    /** Segunda conjuracao no mesmo alvo: pode levantar. O emote para pelo fim do efeito. */
    private static void rise(LivingEntity target) {
        target.removeEffect(MagiaEffects.KNEELING);
        if (target instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("aurorion_magia.levante"), true);
        }
        sound(target, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.8f, 1.2f);
    }

    /** "Cai diante do conjurador": vira o corpo e a cabeca para ele, olhando um pouco para cima. */
    private static void faceTowards(LivingEntity target, LivingEntity caster) {
        double dx = caster.getX() - target.getX();
        double dz = caster.getZ() - target.getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        float pitch = -15f;
        if (target instanceof ServerPlayer player) {
            player.teleportTo(player.serverLevel(), player.getX(), player.getY(), player.getZ(), yaw, pitch);
        } else {
            target.setYRot(yaw);
            target.setYHeadRot(yaw);
            target.yBodyRot = yaw;
            target.setXRot(pitch);
        }
    }

    /** 5 s no nivel 1, +1,5 s por nivel. */
    private static int duration(int spellLevel) {
        return 100 + 30 * (spellLevel - 1);
    }
}
