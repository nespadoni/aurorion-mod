package com.aurorion.limbo.registry;

import com.aurorion.limbo.AurorionLimbo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * O som do Limbo.
 *
 * <h2>Por que isto precisa de codigo, sendo som</h2>
 *
 * <p>O campo {@code effects.music} do bioma nao aceita um caminho de arquivo: ele aceita uma
 * <b>referencia ao registro de sound events</b>. Entao o evento tem que existir no registro, e
 * registro e Java. O que fica em {@code assets/} e a outra metade — quais faixas o evento toca —, e
 * essa metade continua sendo dado trocavel por resource pack.
 *
 * <h2>A divisao, e por que ela importa aqui</h2>
 *
 * <ul>
 *   <li><b>Codigo</b>: que o Limbo tem uma trilha propria. Uma linha, e nunca mais muda.</li>
 *   <li><b>{@code sounds.json}</b>: quais faixas sao. Hoje aponta para as vanilla mais vazias; no dia
 *       em que houver musica composta para o servidor, e trocar o arquivo — sem rebuild, e da para
 *       fazer por resource pack sem tocar no jar.</li>
 * </ul>
 *
 * <p>Mesma fronteira da §9.6 aplicada a som em vez de sprite: comportamento e codigo, conteudo nao
 * deveria exigir build.
 */
public final class LimboSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, AurorionLimbo.MOD_ID);

    /**
     * A trilha do Limbo.
     *
     * <p>Um evento so, com varias faixas dentro: o {@code Music} do bioma em 1.21.1 aceita <b>uma</b>
     * referencia, entao a variacao tem que acontecer um nivel abaixo, no {@code sounds.json}, onde o
     * vanilla ja sorteia entre as entradas de um mesmo evento.
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_LIMBO = register("music.limbo");

    /** Ruidos esparsos — o que faz o silencio ficar desconfortavel em vez de so silencioso. */
    public static final DeferredHolder<SoundEvent, SoundEvent> AMBIENT_LIMBO = register("ambient.limbo");

    private LimboSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(AurorionLimbo.MOD_ID, name);
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }
}
