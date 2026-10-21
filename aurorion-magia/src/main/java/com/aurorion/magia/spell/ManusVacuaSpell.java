package com.aurorion.magia.spell;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.network.MagiaNetwork;
import com.aurorion.magia.network.SpellVisualPayload;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Manus Vacua — Mao Vazia. Arranca o item da mao do alvo e o joga no chao, entre voce e ele.
 *
 * <p>Mao principal por padrao; <b>agachado</b>, a mao secundaria (escudo, totem, foco). O item cai com
 * {@value #PICKUP_DELAY} ticks sem poder ser pego — a janela para voce, ou um aliado, chegar antes.
 *
 * <h2>Quem pode ser desarmado</h2>
 *
 * <p>Mesma regra de quem leva golpe ({@link Hits#hittable}): criativo, espectador, inalvejavel e
 * jogador em lugar sem PvP ficam de fora — conferido na mira e de novo no instante do desarme. Na zona
 * segura nao ha desarme nenhum (e nao "desarme que devolve o item"): arrancar o item e devolver ao
 * inventario ainda tirava o escudo da mao no meio de uma conversa, e a zona segura e para nada
 * acontecer. Chefe ({@code imune_deslocamento}) tambem nao solta a arma.
 *
 * <h2>Criatura</h2>
 *
 * <p>Jogador perde o item para o chao, inteiro. Criatura perde o item da mao do mesmo jeito, mas ele so
 * cai com a chance de drop que o proprio mob tem para aquela mao — a mesma da morte. Sem isso, a magia
 * virava fazenda: o esqueleto de arco com 8,5% de chance, ou o mob de evento com equipamento de 0%,
 * entregava o item em 100% das conjuracoes. Quando a sorte nao cai, o item se desfaz na mao dele.
 */
public final class ManusVacuaSpell extends AurorionSpell {
    private static final int RANGE = 14;
    private static final int PICKUP_DELAY = 40;

    /**
     * {@code Mob#getEquipmentDropChance} e protegido no vanilla. Lido por reflexao, uma vez: o NeoForge
     * 1.21 roda com os nomes da Mojang, entao o nome e o mesmo em dev e no servidor. Sem ele, criatura
     * nao e desarmada (falha fechada: nenhum item nasce do nada).
     */
    @Nullable
    private static Method dropChance;
    private static boolean dropChanceResolved;

    public ManusVacuaSpell() {
        super("manus_vacua", SchoolRegistry.EVOCATION_RESOURCE, SpellRarity.UNCOMMON, 3, 20, CastType.INSTANT);
        this.baseSpellPower = 1;
        this.spellPowerPerLevel = 1;
        this.baseManaCost = 35;
        this.manaCostPerLevel = 5;
        this.castTime = 0;
    }

    @Override
    public Vector3f getTargetingColor() {
        return new Vector3f(0.88f, 0.9f, 0.96f);
    }

    @Override
    public List<MutableComponent> getUniqueInfo(int spellLevel, @Nullable LivingEntity caster) {
        return List.of(Component.translatable("ui.aurorion_magia.desarme"),
                Component.translatable("ui.aurorion_magia.alcance", RANGE));
    }

    @Override
    public boolean checkPreCastConditions(Level level, int spellLevel, LivingEntity entity, MagicData playerMagicData) {
        InteractionHand hand = handFor(entity);
        return aim(level, entity, playerMagicData, RANGE, false,
                target -> !target.getItemInHand(hand).isEmpty() && disarmable(entity, target));
    }

    @Override
    public void onCast(Level level, int spellLevel, LivingEntity entity, CastSource castSource, MagicData playerMagicData) {
        if (level instanceof ServerLevel serverLevel) {
            LivingEntity target = target(serverLevel, entity, playerMagicData);
            if (target != null && disarmable(entity, target)) disarm(serverLevel, entity, target, handFor(entity));
        }
        super.onCast(level, spellLevel, entity, castSource, playerMagicData);
    }

    /** Pode levar golpe de quem conjura e nao e chefe. */
    private static boolean disarmable(LivingEntity caster, LivingEntity target) {
        return Hits.hittable(caster, target) && !Displacement.isImmune(target);
    }

    private static void disarm(ServerLevel level, LivingEntity caster, LivingEntity target, InteractionHand hand) {
        ItemStack stack = target.getItemInHand(hand);
        if (stack.isEmpty()) return;
        boolean drops = true;
        if (target instanceof Mob mob) {
            Float chance = dropChanceOf(mob, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
            // Reflexao indisponivel: nao mexe na criatura.
            if (chance == null) return;
            // Acima de 1 e "item garantido" (o que o mob pegou do chao, ou setGuaranteedDrop).
            drops = chance > 1.0f || (chance > 0 && level.random.nextFloat() < chance);
        }

        if (target.isUsingItem() && target.getUsedItemHand() == hand) target.stopUsingItem();
        target.setItemInHand(hand, ItemStack.EMPTY);

        Vec3 from = target.position().add(0, target.getBbHeight() * 0.6, 0);
        if (drops) {
            Vec3 toward = caster.position().subtract(target.position());
            Vec3 fling = toward.lengthSqr() < 1.0E-4 ? Vec3.ZERO : toward.normalize().scale(0.22);
            ItemEntity drop = new ItemEntity(level, from.x, from.y, from.z, stack, fling.x, 0.32, fling.z);
            drop.setPickUpDelay(PICKUP_DELAY);
            level.addFreshEntity(drop);
        } else {
            sound(target, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.8f, 0.9f);
        }

        if (target instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("aurorion_magia.desarmado"), true);
        }
        sound(target, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 1.4f);
        sound(target, SoundEvents.ARMOR_EQUIP_CHAIN.value(), 1.0f, 0.6f);
        MagiaNetwork.sendVisual(caster, target, SpellVisualPayload.Kind.MANUS_VACUA, 20, from, hand.ordinal());
    }

    /** A chance de drop do mob naquela mao, ou {@code null} se nao deu para ler. */
    @Nullable
    private static Float dropChanceOf(Mob mob, EquipmentSlot slot) {
        if (!dropChanceResolved) {
            dropChanceResolved = true;
            try {
                Method method = Mob.class.getDeclaredMethod("getEquipmentDropChance", EquipmentSlot.class);
                method.setAccessible(true);
                dropChance = method;
            } catch (ReflectiveOperationException | RuntimeException exception) {
                AurorionMagia.LOGGER.warn("Magia: Mob#getEquipmentDropChance indisponivel — Manus Vacua nao desarma criaturas.");
            }
        }
        if (dropChance == null) return null;
        try {
            return (Float) dropChance.invoke(mob, slot);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return null;
        }
    }

    private static InteractionHand handFor(LivingEntity caster) {
        return caster.isShiftKeyDown() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }
}
