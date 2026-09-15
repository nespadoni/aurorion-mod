package com.aurorion.aeonita.client;

import com.aurorion.aeonita.item.UniformCapeItem;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

/**
 * Render da capa vestida.
 *
 * <p>Nao sobrescreve nada: o trabalho de casar o modelo com o esqueleto do jogador foi feito no
 * proprio {@code .geo.json}, na conversao do export do CPM. O {@code GeoArmorRenderer} procura oito
 * bones com nome fixo ({@code armorHead}, {@code armorBody}, {@code armorLeftArm}, ...) e copia
 * neles a pose do modelo humanoide vanilla; o CPM tinha exportado esses mesmos bones com os nomes
 * dele ({@code head}, {@code body}, {@code left_arm}, ...), nos pivos certos.
 *
 * <p>Renomear no arquivo, e nao sobrescrever {@code getHeadBone} e companhia aqui, foi de proposito:
 * o metodo devolve {@code null} quando o bone nao existe e o render segue adiante com ele, entao um
 * nome errado vira {@code NullPointerException} no meio de um frame em vez de erro no carregamento.
 * Com os oito nomes escritos no geo — inclusive as duas botas, vazias, que esta peca nao tem — nao
 * ha nome para errar em lugar nenhum.
 */
public class UniformCapeRenderer extends GeoArmorRenderer<UniformCapeItem> {
    public UniformCapeRenderer() {
        super(new UniformCapeModel());
    }
}
