package com.aurorion.ethereal.client;

import com.aurorion.ethereal.ceremony.BindingRite;
import com.aurorion.ethereal.network.RitePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * O que o cliente sabe sobre os ritos acontecendo por perto.
 *
 * <p>O servidor manda <b>dois</b> pacotes por rito — comeca e termina. Tudo entre um e outro e
 * contado aqui: a animacao inteira sai de um inteiro por rito que anda um por tick. Um pacote por
 * quadro daria a mesma imagem e custaria treze segundos de trafego por espectador.
 *
 * <p>O mapa e por id de entidade, e nao por UUID, porque e assim que o render de jogador e o nivel
 * do cliente falam. Ele fica vazio na esmagadora maioria do tempo.
 */
public final class RiteClient {
    /** Um rito visto daqui. O {@code tick} anda sozinho; ninguem o sincroniza depois do inicio. */
    public static final class Rite {
        private final Component houseName;
        private final Component motto;
        private final int color;
        private final ItemStack symbol;
        private int tick;

        private Rite(RitePayload payload) {
            this.houseName = payload.houseName();
            this.motto = payload.motto();
            this.color = payload.color();
            this.symbol = payload.icon()
                    .flatMap(BuiltInRegistries.ITEM::getOptional)
                    .map(ItemStack::new)
                    .orElseGet(() -> new ItemStack(Items.NETHER_STAR));
        }

        public Component houseName() { return houseName; }
        public Component motto() { return motto; }
        public int color() { return color; }
        public ItemStack symbol() { return symbol; }
        public int tick() { return tick; }

        /** Ticks desde o estouro. Negativo antes dele — a casa ainda e segredo. */
        public int sinceReveal() { return tick - BindingRite.REVEAL_TICK; }

        public boolean revealed() { return tick >= BindingRite.REVEAL_TICK; }
    }

    private static final Map<Integer, Rite> ACTIVE = new HashMap<>();

    private RiteClient() {
    }

    public static void accept(RitePayload payload) {
        if (payload.active()) {
            ACTIVE.put(payload.entityId(), new Rite(payload));
        } else {
            ACTIVE.remove(payload.entityId());
        }
    }

    public static void tick() {
        if (ACTIVE.isEmpty()) return;
        ACTIVE.values().removeIf(rite -> ++rite.tick > BindingRite.TOTAL_TICKS);
    }

    @Nullable
    public static Rite of(int entityId) {
        return ACTIVE.isEmpty() ? null : ACTIVE.get(entityId);
    }

    /** O rito de quem esta olhando, que e o unico que ganha texto na tela. */
    @Nullable
    public static Rite ofSelf() {
        var player = Minecraft.getInstance().player;
        return player == null ? null : of(player.getId());
    }

    /** Sair do servidor esquece tudo: ids de entidade nao valem no proximo mundo. */
    public static void clear() {
        ACTIVE.clear();
    }
}
