package com.aurorion.magia.effect;

import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.compat.EmotecraftCompat;
import com.aurorion.magia.config.MagiaConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * O corpo cedendo ao chao diante de quem carrega a Presenca Aterradora.
 *
 * <p>Nao e o {@code ajoelhado} da magia Prostracao, e a diferenca esta nas maos: este efeito <b>nao</b>
 * entra no {@code handsBound} do {@code MagiaServerEvents}, entao quem esta no chao por medo continua
 * conseguindo comer, beber, erguer o escudo e conjurar. A aura fica ligada por tempo indeterminado e
 * vale para todo mundo que passar perto; se ela tambem tirasse o item da mao, atravessar a rua onde o
 * vilao esta deixaria de ser assustador e viraria impossibilidade de jogar.
 *
 * <p>A pose vem do Emotecraft e e escolhida por {@code dreadProstrates}: prostracao, os dois joelhos
 * no chao ({@code kneel_down}, padrao), ou um joelho so ({@code kneel_one_knee}). O nome do efeito
 * ({@code genuflexo}) e o dos modificadores ficaram como estavam de proposito — id de registro e de
 * modificador de atributo em mundo que ja rodou nao se renomeia so por causa de rotulo.
 *
 * <p>A duracao e sempre curta ({@code DreadAura} a renova de segundo em segundo). Sair do raio e sair
 * do chao: o efeito expira sozinho em pouco mais de um segundo, sem ninguem precisar varrer lista.
 */
public final class GenuflectedEffect extends MobEffect {
    private static final int INTERVAL = 20;

    public GenuflectedEffect() {
        super(MobEffectCategory.HARMFUL, 0x3A2A58);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, AurorionMagia.id("genuflexo_speed"),
                -0.9, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.JUMP_STRENGTH, AurorionMagia.id("genuflexo_jump"),
                -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % INTERVAL == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        // Relogou, trocou de dimensao ou o emote acabou: a pose volta. So em jogador, e so no servidor.
        if (entity instanceof ServerPlayer player) {
            EmotecraftCompat.ensureProstrating(player, MagiaConfig.DREAD_PROSTRATES.get());
        }
        return true;
    }
}
