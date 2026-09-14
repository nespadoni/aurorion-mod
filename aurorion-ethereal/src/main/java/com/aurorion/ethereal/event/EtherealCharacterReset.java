package com.aurorion.ethereal.event;

import com.aurorion.core.character.CharacterResetEvent;
import com.aurorion.ethereal.AurorionEthereal;
import com.aurorion.ethereal.ceremony.CeremonyData;
import com.aurorion.ethereal.house.HouseData;
import com.aurorion.ethereal.ranking.RankingData;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.UUID;

/**
 * Casa, veredito e pontos sao os tres lugares onde o Etereo guarda uma historia por jogador.
 *
 * <p>A casa e a mais importante das tres: sem apagar, o personagem novo nasceria ja pertencendo a
 * uma casa que ele nunca escolheu, e a cerimonia do altar nunca aconteceria para ele.
 */
@EventBusSubscriber(modid = AurorionEthereal.MOD_ID)
public final class EtherealCharacterReset {
    private EtherealCharacterReset() {
    }

    @SubscribeEvent
    public static void onReset(CharacterResetEvent event) {
        MinecraftServer server = event.server();
        UUID account = event.account();

        HouseData.get(server).setHouse(account, null);

        CeremonyData ceremonies = CeremonyData.get(server);
        ceremonies.removeVerdict(account);
        ceremonies.takeReveal(account);

        RankingData.get(server).clearPlayer(account);
    }
}
