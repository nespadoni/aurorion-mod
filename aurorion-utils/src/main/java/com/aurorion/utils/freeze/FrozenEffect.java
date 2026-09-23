package com.aurorion.utils.freeze;

import com.aurorion.utils.AurorionUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.common.EffectCure;

import java.util.Set;

/**
 * O estado "congelado". O que <b>prende no lugar</b> continua sendo a montaria (a
 * {@code FreezeAnchorEntity}); o efeito e o que diz a todo o resto que a pessoa esta congelada:
 *
 * <ul>
 *   <li>o servidor recusa bater, usar, quebrar, colocar, jogar item fora e desmontar;</li>
 *   <li>o cliente do congelado zera o teclado — nao anda, nao pula, nao agacha, nao troca de slot,
 *       nao abre inventario — e so deixa olhar em volta;</li>
 *   <li>outros mods (o Tempus Sistere do {@code aurorion-magia}) congelam aplicando este mesmo
 *       efeito pelo id {@value #ID_STRING}, sem importar nenhuma classe daqui.</li>
 * </ul>
 *
 * <p><b>Leite e totem nao curam</b> ({@link #fillEffectCures} vazio). A decisao antiga de "montaria,
 * nao efeito" existia porque um balde de leite desfaria o freeze; aqui a montaria continua, e o
 * efeito simplesmente nao tem cura — alem de que congelado nem consegue beber. So sai por
 * {@code /unfreeze}, pelo fim do tempo ou por {@code /effect clear} da staff.
 *
 * <p>O tick, uma vez por segundo e so em quem esta congelado, recoloca a montaria se ela sumiu
 * (relog, {@code /tp} da staff, troca de dimensao).
 */
public final class FrozenEffect extends MobEffect {
    public static final String ID_STRING = "aurorion_utils:congelado";
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(AurorionUtils.MOD_ID, "congelado");
    private static final int INTERVAL = 20;

    public FrozenEffect() {
        super(MobEffectCategory.HARMFUL, 0xBFE8FF);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, ResourceLocation.fromNamespaceAndPath(AurorionUtils.MOD_ID, "congelado_speed"),
                -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.JUMP_STRENGTH, ResourceLocation.fromNamespaceAndPath(AurorionUtils.MOD_ID, "congelado_jump"),
                -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public void fillEffectCures(Set<EffectCure> cures, MobEffectInstance instance) {
        // Nenhuma cura: nem leite, nem totem, nem mel.
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % INTERVAL == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!entity.level().isClientSide) FreezeManager.pin(entity);
        return true;
    }
}
