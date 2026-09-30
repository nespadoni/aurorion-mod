package com.aurorion.servicos.mixin;

import com.aurorion.servicos.client.PhoneBridge;
import com.aurorion.servicos.client.ServicosClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Poe o "Servicos" no catalogo de apps do celular. O catalogo e montado de novo a cada chamada
 * ({@code new ArrayList}), e tudo no telefone passa por ele: a grade da tela inicial, a busca e a
 * organizacao das paginas — que coloca sozinha no primeiro espaco livre um app que ainda nao tem
 * lugar. Entrar aqui basta para o icone aparecer.
 *
 * <p>O catalogo e pedido varias vezes por quadro; o registro do app so e recriado quando o numero de
 * pedidos pendentes (o selo no icone) muda.
 */
@Pseudo
@Mixin(targets = "com.mattupolis.phone.client.gui.PhoneHomeAppCatalog", remap = false)
public abstract class PhoneAppCatalogMixin {
    @Unique
    private static Object aurorion_servicos$app;
    @Unique
    private static int aurorion_servicos$badge = -1;

    @Inject(method = "getApps", at = @At("RETURN"), require = 0)
    private static void aurorion_servicos$addApp(CallbackInfoReturnable<List<Object>> cir) {
        int badge = ServicosClient.badge();
        if (aurorion_servicos$app == null || badge != aurorion_servicos$badge) {
            aurorion_servicos$app = PhoneBridge.appInfo(ServicosClient.APP_ID, ServicosClient.SEARCH_TERMS,
                    ServicosClient.APP_NAME, ServicosClient.ICON, ServicosClient.APP_COLOR, badge, true, true);
            aurorion_servicos$badge = badge;
        }
        List<Object> apps = cir.getReturnValue();
        if (aurorion_servicos$app != null && apps != null) apps.add(aurorion_servicos$app);
    }
}
