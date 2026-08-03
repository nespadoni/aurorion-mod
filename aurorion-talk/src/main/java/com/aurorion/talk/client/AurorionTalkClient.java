package com.aurorion.talk.client;

import com.aurorion.talk.AurorionTalk;
import com.aurorion.talk.client.gui.BalloonCustomizationScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Entrypoint client-side. O botao "Config" da lista de mods abre direto a personalizacao do balao
 * — e o que a maioria dos jogadores vem procurar; preferencias ficam um botao adiante, dentro dela.
 */
@Mod(value = AurorionTalk.MOD_ID, dist = Dist.CLIENT)
public class AurorionTalkClient {

    public AurorionTalkClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (modContainer, parent) -> new BalloonCustomizationScreen());
    }
}
