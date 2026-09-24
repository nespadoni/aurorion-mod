package com.aurorion.magia.effect;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.registry.MagiaEffects;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A rede de seguranca dos efeitos que zeram atributo.
 *
 * <h2>O bug que isto resolve</h2>
 *
 * <p>Efeito de status com modificador de atributo e um par: o vanilla poe o modificador quando o
 * efeito entra e o tira quando o efeito sai. Se o servidor cair (ou for derrubado por uma excecao de
 * outro mod) <b>entre</b> as duas metades, o modificador fica gravado no jogador e o efeito nao — e
 * a pessoa volta com velocidade e pulo zerados <b>para sempre</b>, sem nenhum icone de efeito para
 * explicar o porque. Foi o que aconteceu com a antiga "estase" do Sequestro de Impulso, e pode
 * acontecer com qualquer um dos nossos.
 *
 * <p>A varredura roda no login e no renascimento — os dois momentos em que o jogador volta a existir
 * — e custa uma consulta de atributo por modificador da lista (seis). Para cada um: se o modificador
 * esta preso no jogador mas o efeito que deveria estar segurando ele nao esta, o modificador sai.
 *
 * <p>Os ids de modificador de versoes antigas ficam na lista com {@code effect == null}: nao existe
 * mais efeito nenhum que os justifique, entao eles saem sempre. E o que desprende quem ficou
 * travado antes desta correcao.
 */
public final class EffectCleanup {
    /**
     * @param effect o efeito que justifica o modificador, ou {@code null} para "nunca mais e
     *               justificado" (modificador de magia que saiu do mod)
     */
    private record Bond(Holder<Attribute> attribute, ResourceLocation modifier, @Nullable Holder<MobEffect> effect) {
    }

    private static List<Bond> bonds;

    private EffectCleanup() {
    }

    /** Solta todo modificador nosso que ficou preso sem o efeito correspondente. */
    public static void sweep(LivingEntity entity) {
        for (Bond bond : bonds()) {
            if (bond.effect() != null && entity.hasEffect(bond.effect())) continue;
            AttributeInstance instance = entity.getAttribute(bond.attribute());
            if (instance == null || !instance.hasModifier(bond.modifier())) continue;
            instance.removeModifier(bond.modifier());
            AurorionMagia.LOGGER.info("Magia: soltando {} preso em {} sem o efeito correspondente.",
                    bond.modifier(), entity.getName().getString());
        }
    }

    /** Montada na primeira varredura: antes disso os efeitos ainda podem nao estar registrados. */
    private static List<Bond> bonds() {
        if (bonds == null) {
            bonds = List.of(
                    new Bond(Attributes.MOVEMENT_SPEED, AurorionMagia.id("cruciatus_speed"), MagiaEffects.CRUCIATUS),
                    new Bond(Attributes.JUMP_STRENGTH, AurorionMagia.id("cruciatus_jump"), MagiaEffects.CRUCIATUS),
                    new Bond(Attributes.MOVEMENT_SPEED, AurorionMagia.id("genua_speed"), MagiaEffects.KNEELING),
                    new Bond(Attributes.JUMP_STRENGTH, AurorionMagia.id("genua_jump"), MagiaEffects.KNEELING),
                    new Bond(Attributes.MOVEMENT_SPEED, AurorionMagia.id("cativo_speed"), MagiaEffects.CAPTIVE),
                    new Bond(Attributes.JUMP_STRENGTH, AurorionMagia.id("abatido_jump"), MagiaEffects.GROUNDED),
                    // Sequestro de Impulso, removido do mod: quem ficou preso nele sai aqui.
                    new Bond(Attributes.MOVEMENT_SPEED, AurorionMagia.id("estase_speed"), null),
                    new Bond(Attributes.JUMP_STRENGTH, AurorionMagia.id("estase_jump"), null));
        }
        return bonds;
    }
}
