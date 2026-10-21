package com.aurorion.magia.spell;

import com.aurorion.magia.AurorionMagia;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * A paralisia conferida pelo servidor, para jogador.
 *
 * <p>Os efeitos que "nao deixam andar" (estase, cruciatus, joelhos, cativo...) zeram velocidade e pulo
 * por modificador de atributo. Para criatura isso basta: quem a move e o servidor. Jogador nao — quem
 * move o corpo e o cliente dele, e um cliente modificado simplesmente ignora o atributo e anda. Aqui o
 * servidor guarda onde o corpo estava quando o efeito comecou e, se ele se afastou alem de uma folga
 * pequena, o devolve (o mesmo teleporte de conexao do {@link WaterCage#hold}).
 *
 * <p>A folga existe para nao brigar com o que e legitimo: o empurrao do proprio servidor, o arredondar
 * da posicao, a suspensao do Tormento Coletivo (por isso o Cruciatus segura so no plano — a altura e
 * do Tormento). Um cliente honesto nunca chega perto dela; nenhum pacote sai enquanto ele esta dentro.
 *
 * <p>Aplicado so a estase e ao cruciatus, as duas paralisias que tambem protegem ou executam algo
 * (estase segura dano, cruciatus segura alguem sob tortura). Joelhos, Genuflexao, Cativo, Chao e o
 * peso do afogamento continuam so com atributo: sao cena, e o cliente modificado que anda com eles
 * "ganha" so o passo — nenhuma regra de dano depende de ele estar parado.
 *
 * <p>Custo: uma distancia por tick, so em jogador com um desses efeitos. A ancora mora no
 * {@code persistentData} e sai no fim do efeito ({@link #clear}).
 */
public final class PositionHold {
    public static final String STASIS = AurorionMagia.MOD_ID + ":ancora_estase";
    public static final String CRUCIATUS = AurorionMagia.MOD_ID + ":ancora_cruciatus";

    private static final String KEY_X = "X";
    private static final String KEY_Y = "Y";
    private static final String KEY_Z = "Z";
    private static final String KEY_DIM = "Dim";
    private static final String KEY_AT = "At";
    private static final int STALE_TICKS = 5;

    private PositionHold() {
    }

    /**
     * Um tick do efeito.
     *
     * @param key        a ancora deste efeito ({@link #STASIS}, {@link #CRUCIATUS})
     * @param planeOnly  {@code true} confere so x/z (a altura fica livre)
     * @param tolerance  quantos blocos o corpo pode se afastar antes de voltar
     */
    public static void hold(LivingEntity entity, String key, boolean planeOnly, double tolerance) {
        if (!(entity instanceof ServerPlayer player) || player.isSpectator() || player.isCreative()) return;
        String dim = player.level().dimension().location().toString();
        long now = player.level().getGameTime();
        CompoundTag data = player.getPersistentData();
        // Ancora nova quando nao ha, quando e de outra dimensao, ou quando ficou parada mais que uns
        // ticks (sobra de um efeito cujo fim nao foi visto, relog): nunca devolver alguem a um ponto
        // velho.
        if (!data.contains(key, Tag.TAG_COMPOUND) || !data.getCompound(key).getString(KEY_DIM).equals(dim)
                || now - data.getCompound(key).getLong(KEY_AT) > STALE_TICKS) {
            CompoundTag tag = new CompoundTag();
            tag.putDouble(KEY_X, player.getX());
            tag.putDouble(KEY_Y, player.getY());
            tag.putDouble(KEY_Z, player.getZ());
            tag.putString(KEY_DIM, dim);
            tag.putLong(KEY_AT, now);
            data.put(key, tag);
            return;
        }
        CompoundTag tag = data.getCompound(key);
        tag.putLong(KEY_AT, now);
        double x = tag.getDouble(KEY_X);
        double z = tag.getDouble(KEY_Z);
        double y = planeOnly ? player.getY() : tag.getDouble(KEY_Y);
        double dx = player.getX() - x, dy = player.getY() - y, dz = player.getZ() - z;
        if (dx * dx + dy * dy + dz * dz <= tolerance * tolerance) return;
        player.connection.teleport(x, y, z, player.getYRot(), player.getXRot());
        AurorionSpell.launch(player, planeOnly ? new Vec3(0, player.getDeltaMovement().y, 0) : Vec3.ZERO);
    }

    public static void clear(LivingEntity entity, String key) {
        entity.getPersistentData().remove(key);
    }
}
