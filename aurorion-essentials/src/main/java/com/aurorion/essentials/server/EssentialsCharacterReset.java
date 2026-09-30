package com.aurorion.essentials.server;

import com.aurorion.core.character.CharacterNamedEvent;
import com.aurorion.core.character.CharacterResetEvent;
import com.aurorion.essentials.AurorionEssentials;
import com.aurorion.essentials.compat.MattupolisPhoneCompat;
import com.aurorion.essentials.compat.PhoneNumberSaveQueue;
import com.aurorion.essentials.compat.PhoneSocialReset;
import com.aurorion.essentials.voice.ShoutRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * O nome do personagem e o nome que o mundo ve.
 *
 * <p>O {@code aurorion_personagem} decide <b>qual</b> e o nome e garante que ele e unico; o nome
 * falso ja era o mecanismo que troca o que aparece no chat, na tab list e sobre a cabeca. Ligar os
 * dois aqui evita um segundo sistema de nome exibido fazendo a mesma coisa com outro nome.
 *
 * <p>Continua sendo possivel usar {@code /fakename} por cima: e o mesmo campo. O nome do personagem
 * e so quem o escreve primeiro.
 */
@EventBusSubscriber(modid = AurorionEssentials.MOD_ID)
public final class EssentialsCharacterReset {
    private EssentialsCharacterReset() {
    }

    @SubscribeEvent
    public static void onReset(CharacterResetEvent event) {
        ServerPlayer player = event.player();
        // O diretorio de numeros e gravado fora da thread do servidor (PhoneNumberSaveQueue): o reset
        // le e reescreve o arquivo, entao antes espera a fila esvaziar — senao uma gravacao atrasada
        // devolveria o numero apagado — e depois esquece a ultima gravacao, porque o disco mudou.
        PhoneNumberSaveQueue.awaitWrites();
        try {
            MattupolisPhoneCompat.resetCharacterData(event.server(), event.account());
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao limpar dados do telefone do personagem anterior", e);
        } finally {
            PhoneNumberSaveQueue.forgetLastWrite();
        }

        // Perfil, posts, DMs e anuncios do telefone ficam guardados pelo nick, que o personagem novo
        // herda. Melhor esforco: se o telefone mudar e a limpeza falhar, o reset segue e o log avisa.
        PhoneSocialReset.forget(event.server(), event.transaction().accountName());

        // O /gritar e da cena do personagem anterior, nao da conta: o novo comeca com a voz normal.
        ShoutRegistry.clear(event.account());

        // A agenda do telefone fica no PC de cada um e aponta pelo nick: sem isto, o contato do
        // personagem morto passaria a chamar o novo. Ver PhoneRetirements.
        PhoneRetirementSync.retire(event.server(), event.previousCharacterId(), event.transaction().accountName());

        // Offline acontece quando um reset interrompido e retomado antes do dono voltar: sem
        // jogador nao ha pacote para enviar, e apagar o dado guardado ja basta.
        if (player != null) {
            FakeNameManager.clear(player);
        } else if (FakeNameData.get(event.server()).setRaw(event.account(), null)) {
            // Quem esta online deixa de ver o nome do morto na agenda.
            PhoneNameDirectorySync.changed(event.server(), event.account(), null);
        }
    }

    @SubscribeEvent
    public static void onNamed(CharacterNamedEvent event) {
        ServerPlayer player = event.player();
        FakeNameManager.Result result = FakeNameManager.set(player, event.character().fullName());

        if (result != FakeNameManager.Result.OK) {
            // A identidade vale assim mesmo — o que falhou foi so a exibicao. Acontece quando o nome
            // escolhido coincide com o nick real de alguem que esta online neste momento.
            AurorionEssentials.LOGGER.warn("Nome de personagem '{}' nao pode ser exibido para {}: {}",
                    event.character().fullName(), player.getGameProfile().getName(), result);
        }
    }
}
