package com.aurorion.magia.spell;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.entity.ShadowEntity;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.ICastDataSerializable;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.MultiTargetEntityCastData;
import io.redspace.ironsspellbooks.capabilities.magic.RecastInstance;
import io.redspace.ironsspellbooks.capabilities.magic.RecastResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Umbra Viva — Sombra Viva. O W do Zed: a sombra de quem conjura avanca {@value #DASH} blocos na
 * direcao da mira e fica la por alguns segundos. Conjurar de novo enquanto ela existe <b>troca de
 * lugar</b> com ela.
 *
 * <p>As outras magias do kit (Shuriken Laminado, Corte Sombrio) saem tambem de cada sombra viva — ate
 * {@value ShadowEntity#MAX_PER_OWNER} ao mesmo tempo, contando a que a Marca Fatal deixa para tras.
 *
 * <h2>Energia</h2>
 *
 * <p>A passiva do W: quando quem conjura e uma sombra acertam o <b>mesmo</b> inimigo com a <b>mesma</b>
 * conjuracao, volta mana ({@value #ENERGY_MANA}), uma vez por conjuracao. Ver {@link #energy}.
 *
 * <p>A segunda conjuracao usa o sistema de reconjuracao do Iron's: a recarga so comeca quando a sombra
 * e usada ou some.
 */
public final class UmbraVivaSpell extends AurorionSpell {
    private static final double DASH = 8;
    private static final int DASH_TICKS = 5;
    private static final float ENERGY_MANA = 20;
    private static final String HIT_KEY = AurorionMagia.MOD_ID + ":zed_golpe";
    private static final String ENERGY_KEY = AurorionMagia.MOD_ID + ":zed_energia";

    public UmbraVivaSpell() {
        super("umbra_viva", SchoolRegistry.ENDER_RESOURCE, SpellRarity.EPIC, 3, 18, CastType.INSTANT);
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 0;
        this.baseManaCost = 40;
        this.manaCostPerLevel = 5;
        this.castTime = 0;
    }

    @Override
    public Optional<SoundEvent> getCastFinishSound() {
        return Optional.of(SoundEvents.ENDERMAN_AMBIENT);
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.6f, 0.05f, 0.1f);
    }

    @Override
    public ICastDataSerializable getEmptyCastData() {
        // RecastInstance usa esta fabrica para ler tanto o pacote do cliente quanto o NBT.
        return new MultiTargetEntityCastData();
    }

    @Override
    public int getRecastCount(int spellLevel, @Nullable LivingEntity entity) {
        return 2;
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(
                Component.translatable("ui.aurorion_magia.investida", (int) DASH),
                Component.translatable("ui.aurorion_magia.duracao", Utils.timeFromTicks(life(spellLevel), 1)),
                Component.translatable("ui.aurorion_magia.reconjurar_troca"),
                Component.translatable("ui.aurorion_magia.energia_zed", (int) ENERGY_MANA));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel
                && (playerMagicData == null || !playerMagicData.getPlayerRecasts().hasRecastForSpell(getSpellId()))) {
            ShadowEntity shadow = cast(serverLevel, entity, life(spellLevel));
            if (playerMagicData != null && entity instanceof ServerPlayer) {
                playerMagicData.getPlayerRecasts().addRecast(new RecastInstance(getSpellId(), spellLevel, 2,
                        life(spellLevel), castSource, new MultiTargetEntityCastData(shadow)), playerMagicData);
            }
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /** A segunda conjuracao (ou o fim do prazo): troca de lugar com a sombra, se ela ainda existir. */
    @Override
    public void onRecastFinished(ServerPlayer player, RecastInstance recastInstance, RecastResult recastResult,
                                 ICastDataSerializable castData) {
        super.onRecastFinished(player, recastInstance, recastResult, castData);
        if (recastResult != RecastResult.USED_ALL_RECASTS || !(castData instanceof MultiTargetEntityCastData targets)
                || targets.getTargets().isEmpty()) return;
        UUID id = targets.getTargets().getFirst();
        if (player.serverLevel().getEntity(id) instanceof ShadowEntity shadow && shadow.isAlive()
                && shadow.isOwnedBy(player)) swap(player, shadow);
    }

    private ShadowEntity cast(ServerLevel level, LivingEntity caster, int life) {
        Vec3 look = caster.getLookAngle().multiply(1, 0, 1);
        Vec3 direction = look.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, caster.getYRot()) : look.normalize();
        Vec3 from = caster.position();
        Vec3 chest = from.add(0, 1, 0);
        BlockHitResult wall = level.clip(new ClipContext(chest, chest.add(direction.scale(DASH)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        Vec3 reach = wall.getType() == HitResult.Type.MISS ? chest.add(direction.scale(DASH))
                : wall.getLocation().subtract(direction.scale(0.6));
        Vec3 destination = Utils.moveToRelativeGroundLevel(level, reach.subtract(0, 1, 0), 3);
        sound(caster, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.2f, 0.7f);
        return ShadowEntity.spawn(level, caster, from, destination, DASH_TICKS, life);
    }

    private void swap(ServerPlayer player, ShadowEntity shadow) {
        Vec3 here = player.position();
        Vec3 there = shadow.position();
        if (!Utils.handleSpellTeleport(this, player, there)) return;
        shadow.setPos(here.x, here.y, here.z);
        player.resetFallDistance();
        sound(player, SoundEvents.ENDERMAN_TELEPORT, 1.0f, 0.6f);
        MagiaNetwork.sendVisualAt(player.serverLevel(), player, SpellVisualPayload.Kind.UMBRA_SWAP, 14, here, 0);
        MagiaNetwork.sendVisualAt(player.serverLevel(), player, SpellVisualPayload.Kind.UMBRA_SWAP, 14, there, 0);
    }

    /**
     * Um golpe do kit do Zed acertou {@code victim}. Se outro golpe da mesma conjuracao (o mesmo
     * {@code stamp}) ja o tinha acertado — quem conjura e a sombra, ou duas sombras —, volta mana, uma
     * vez por conjuracao.
     */
    public static void energy(LivingEntity owner, LivingEntity victim, long stamp) {
        CompoundTag hit = victim.getPersistentData();
        if (hit.getLong(HIT_KEY) != stamp) {
            hit.putLong(HIT_KEY, stamp);
            return;
        }
        CompoundTag mine = owner.getPersistentData();
        if (mine.getLong(ENERGY_KEY) == stamp) return;
        mine.putLong(ENERGY_KEY, stamp);
        if (owner instanceof ServerPlayer player) {
            MagicData.getPlayerMagicData(player).addMana(ENERGY_MANA);
            sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.8f);
        }
    }

    /** Base: 5 s no nivel 1, +1 s por nivel. SpellBalance dobra este tempo. */
    private static int life(int spellLevel) {
        return SpellBalance.duration(100 + 20 * (spellLevel - 1));
    }
}
