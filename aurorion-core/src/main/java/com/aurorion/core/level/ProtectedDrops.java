package com.aurorion.core.level;

import com.aurorion.core.AurorionCore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;

/**
 * "Este item no chao ainda tem dono esperando por ele."
 *
 * <p>Um contrato entre dois mods que nao se conhecem: quem marca (o {@code aurorion_limbo}, nos
 * drops de uma morte que o Relicario pode chamar de volta) e quem apagaria (a limpeza periodica do
 * {@code aurorion_essentials}). Nenhum dos dois importa o outro — os dois leem esta chave.
 *
 * <p>A protecao <b>vence sozinha</b>. Um item marcado para sempre viraria lixo que nunca sai do
 * mundo; com prazo, o pior caso e o comportamento vanilla chegando um pouco mais tarde.
 *
 * <p>O prazo e contado em {@code gameTime}, que no servidor e o mesmo relogio para todas as
 * dimensoes e continua correndo com o chunk descarregado. Ja o despawn do vanilla conta a idade da
 * entidade, que so anda com o chunk carregado — por isso as duas metades usam o mesmo numero de
 * ticks, mas cada uma no seu relogio.
 */
public final class ProtectedDrops {
    /** Chave nos dados persistentes da entidade ({@code NeoForgeData}), que sobrevivem ao save. */
    public static final String KEY_UNTIL = AurorionCore.MOD_ID + ":protegido_ate";

    private ProtectedDrops() {
    }

    /**
     * Protege o item por {@code ticks}: nem o despawn do vanilla nem a limpeza periodica o tocam
     * nesse intervalo. Nunca encurta uma protecao ou um tempo de vida que ja fosse maior.
     */
    public static void protect(ItemEntity item, int ticks) {
        if (ticks <= 0) return;

        CompoundTag data = item.getPersistentData();
        long until = item.level().getGameTime() + ticks;
        if (data.getLong(KEY_UNTIL) < until) {
            data.putLong(KEY_UNTIL, until);
        }
        item.lifespan = Math.max(item.lifespan, ticks);
    }

    public static boolean isProtected(ItemEntity item) {
        return item.getPersistentData().getLong(KEY_UNTIL) > item.level().getGameTime();
    }
}
