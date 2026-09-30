package com.aurorion.essentials.mixin;

import com.aurorion.essentials.compat.PhoneNumberDirectory;
import com.aurorion.essentials.compat.PhoneNumberSaveQueue;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.UUID;

/**
 * Tira o disco da thread do servidor no diretorio de numeros do telefone — a causa do crash do
 * Watchdog. O porque inteiro esta em {@link PhoneNumberDirectory}.
 *
 * <p>Duas pecas:
 * <ul>
 *   <li>{@code ensureNumberFor}: quando a conta ja tem numero e o nome nao mudou, devolve o numero e
 *       para ali. E o caso de quase toda chamada do login (2·N² delas), e o original gravaria o
 *       arquivo inteiro em cada uma sem mudar nada.</li>
 *   <li>{@code save}: vira {@link PhoneNumberSaveQueue#save}, que grava fora da thread do servidor, so
 *       quando o conteudo mudou, e de forma atomica.</li>
 * </ul>
 *
 * <p>Fica no {@code aurorion_essentials.phone.mixins.json}, que nao e obrigatorio, e todo injetor tem
 * {@code require = 0}: se uma atualizacao do telefone mudar estes metodos ou campos, o servidor sobe
 * com o comportamento original em vez de nao subir. O {@code PhoneMixinContractTest} confere os
 * alvos contra o jar instalado.
 */
@Pseudo
@Mixin(targets = "com.mattupolis.phone.server.contacts.PhoneNumberServerStore", remap = false)
public abstract class PhoneNumberStoreMixin {
    @Shadow @Final private static Map<UUID, String> UUID_TO_NUMBER;
    @Shadow @Final private static Map<String, UUID> NUMBER_TO_UUID;
    @Shadow @Final private static Map<UUID, String> UUID_TO_NAME;
    @Shadow private static String loadedWorldKey;

    /**
     * Roda dentro do {@code synchronized} do metodo original, entao ler os mapas e seguro. So vale com
     * o diretorio ja carregado: com {@code loadedWorldKey} vazio (servidor subindo, ou logo depois de um
     * reset de personagem) o original precisa rodar para ler o disco.
     */
    @Inject(method = "ensureNumberFor", at = @At("HEAD"), cancellable = true, require = 0)
    private static void aurorion_essentials$unchanged(ServerPlayer player, CallbackInfoReturnable<String> cir) {
        if (player == null || player.getServer() == null) return;
        if (loadedWorldKey == null || loadedWorldKey.isEmpty()) return;
        String number = PhoneNumberDirectory.unchangedNumber(UUID_TO_NUMBER, NUMBER_TO_UUID, UUID_TO_NAME,
                player.getUUID(), player.getGameProfile().getName());
        if (number != null) cir.setReturnValue(number);
    }

    @Inject(method = "save", at = @At("HEAD"), cancellable = true, require = 0)
    private static void aurorion_essentials$saveOffThread(MinecraftServer server, CallbackInfo ci) {
        if (server == null) return;
        PhoneNumberSaveQueue.save(server, UUID_TO_NUMBER, UUID_TO_NAME);
        ci.cancel();
    }
}
