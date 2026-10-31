package com.aurorion.magia.mixin;

import com.aurorion.magia.AurorionMagia;
import io.netty.buffer.Unpooled;
import io.redspace.ironsspellbooks.capabilities.magic.SyncedSpellData;
import io.redspace.ironsspellbooks.network.casting.SyncPlayerDataPacket;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * O pacote de sincronizacao do Iron's leva uma <b>copia</b> dos dados, tirada na hora em que nasce.
 *
 * <p>No original ele guarda o {@link SyncedSpellData} vivo e so o serializa depois, na thread do Netty.
 * Qualquer mudanca nesse meio-tempo — o Iron's Restrictions ensinando as magias padrao uma por uma no
 * login, cada uma com o seu pacote; um manuscrito; o comando de esquecer — derrubava com
 * {@code ConcurrentModificationException} todo mundo que estava perto de quem logou. Em 25/10/2026
 * foram 64 quedas numa tarde.
 *
 * <p>A copia sai do proprio formato de rede do Iron's ({@code write} e {@code read}), na thread do
 * servidor, onde ninguem mexe nos dados ao mesmo tempo. Custo: serializar duas vezes um pacote que so
 * sai quando algo mudou.
 */
@Mixin(value = SyncPlayerDataPacket.class, remap = false)
public abstract class SyncPlayerDataPacketMixin {
    @Shadow
    SyncedSpellData syncedSpellData;

    @Inject(method = "<init>(Lio/redspace/ironsspellbooks/capabilities/magic/SyncedSpellData;)V",
            at = @At("RETURN"), require = 1)
    private void aurorion_magia$snapshot(SyncedSpellData live, CallbackInfo ci) {
        if (live == null) return;
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            SyncedSpellData.write(buf, live);
            syncedSpellData = SyncedSpellData.read(buf);
        } catch (RuntimeException failure) {
            // Sem copia o pacote segue como no original: pior caso e o comportamento de antes.
            AurorionMagia.LOGGER.warn("Nao foi possivel copiar os dados de magia para sincronizar.", failure);
        } finally {
            buf.release();
        }
    }
}
