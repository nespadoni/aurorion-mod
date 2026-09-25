package com.aurorion.magia.passive;

import com.aurorion.magia.AurorionMagia;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * Uma <b>passiva</b>: uma marca que fica no personagem, e nao uma magia que ele conjura.
 *
 * <p>A diferenca com a magia e o ponto todo do sistema. Magia se aprende, se grava num livro, se
 * conjura, custa mana e entra em recarga. Passiva se <b>recebe uma vez</b> — o pergaminho e consumido
 * e a marca fica — e a partir dai ela e parte de quem a pessoa e: a medica cura no toque porque e
 * medica, nao porque apertou um botao.
 *
 * <p>Isto <b>nao</b> e um registro do Minecraft. Passiva nao entra em item, em NBT de mundo nem em
 * pacote de sincronizacao de registro; ela e um {@code enum} de codigo, como as {@code Shape} da zona
 * de vento. O que vai para o disco e o id em texto ({@link #id()}), exatamente como o
 * {@code SpellGrants} guarda magia de addon que saiu do pack: tirar uma passiva do codigo nao apaga a
 * de ninguem, e devolve-la ao codigo a faz voltar a funcionar.
 *
 * <p>Cada entrada traz um id curto em snake_case (que vira {@code aurorion_magia:<id>}) e se o dono
 * pode liga-la e desliga-la ({@code /aurorion passivas ligar|desligar}): passiva que muda o mundo em
 * volta precisa de interruptor; passiva que so muda o que acontece quando <i>voce</i> age, nao.
 */
public enum Passive {
    /**
     * <b>Mao que Cura.</b> Bater em alguem, com a mao ou com o que estiver nela, cura em vez de ferir.
     *
     * <p>Sem interruptor de proposito: ela nao faz nada ate a pessoa decidir bater em alguem, e a
     * decisao ja e o interruptor.
     */
    HEALING_TOUCH("manus_medica", false),

    /**
     * <b>Presenca Aterradora.</b> Quem estiver perto sente medo — o mundo escurece, a tela fecha em
     * preto, o coracao dispara, a nevoa negra exala — e se prostra.
     *
     * <p>Com interruptor porque ela vale para <i>todo mundo</i> em volta, o tempo todo, sem ninguem
     * ter escolhido nada. Um vilao entra numa taverna e liga a aura; sai de cena e desliga.
     */
    DREAD("presenca_terrivel", true);

    private final ResourceLocation id;
    private final String key;
    private final boolean toggleable;

    Passive(String key, boolean toggleable) {
        this.id = AurorionMagia.id(key);
        this.key = key;
        this.toggleable = toggleable;
    }

    public ResourceLocation id() {
        return id;
    }

    public String key() {
        return key;
    }

    public boolean isToggleable() {
        return toggleable;
    }

    public MutableComponent displayName() {
        return Component.translatable("passive.aurorion_magia." + key).withStyle(ChatFormatting.GOLD);
    }

    public MutableComponent description() {
        return Component.translatable("passive.aurorion_magia." + key + ".guide").withStyle(ChatFormatting.GRAY);
    }
}
