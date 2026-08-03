package com.aurorion.talk.style;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Aparencia do balao de um jogador, montada por composicao.
 *
 * <p>Nao existe registry de estilos prontos: o jogador combina balao + enfeite + duas cores na GUI.
 * Isso e o que permite adicionar arte nova so soltando um PNG na pasta, sem tocar em codigo.</p>
 *
 * @param skin       folha 32x32 nine-slice do balao
 * @param decoration enfeite desenhado no canto superior esquerdo, ou vazio
 * @param color      cor multiplicada na textura do balao, 0xRRGGBB
 * @param textColor  cor do texto dentro do balao, 0xRRGGBB
 */
public record BalloonStyle(
        ResourceLocation skin,
        Optional<ResourceLocation> decoration,
        int color,
        int textColor
) {
    public static final BalloonStyle DEFAULT =
            new BalloonStyle(BalloonTextures.DEFAULT_SKIN, Optional.empty(), 0xFFFFFF, 0x141414);

    public static final StreamCodec<ByteBuf, BalloonStyle> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, BalloonStyle::skin,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), BalloonStyle::decoration,
            ByteBufCodecs.INT, BalloonStyle::color,
            ByteBufCodecs.INT, BalloonStyle::textColor,
            BalloonStyle::new
    );

    private static final String KEY_SKIN = "Skin";
    private static final String KEY_DECORATION = "Decoration";
    private static final String KEY_COLOR = "Color";
    private static final String KEY_TEXT_COLOR = "TextColor";

    public boolean hasDecoration() {
        return decoration.isPresent();
    }

    public BalloonStyle withSkin(ResourceLocation newSkin) {
        return new BalloonStyle(newSkin, decoration, color, textColor);
    }

    public BalloonStyle withDecoration(Optional<ResourceLocation> newDecoration) {
        return new BalloonStyle(skin, newDecoration, color, textColor);
    }

    public BalloonStyle withColor(int newColor) {
        return new BalloonStyle(skin, decoration, newColor, textColor);
    }

    public BalloonStyle withTextColor(int newTextColor) {
        return new BalloonStyle(skin, decoration, color, newTextColor);
    }

    /**
     * Verifica se este estilo pode ter vindo da GUI. Roda no servidor, em cima do que o cliente
     * mandou — sem isso um cliente modificado escolheria qualquer caminho de textura e qualquer cor.
     */
    public boolean isWellFormed() {
        if (!BalloonTextures.isSkin(skin)) return false;
        if (decoration.isPresent() && !BalloonTextures.isDecoration(decoration.get())) return false;

        return BalloonPalette.isBalloonColor(color) && BalloonPalette.isTextColor(textColor);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY_SKIN, skin.toString());
        decoration.ifPresent(id -> tag.putString(KEY_DECORATION, id.toString()));
        tag.putInt(KEY_COLOR, color);
        tag.putInt(KEY_TEXT_COLOR, textColor);
        return tag;
    }

    /** @return vazio se o NBT estiver corrompido ou de uma versao incompativel. */
    public static Optional<BalloonStyle> load(CompoundTag tag) {
        ResourceLocation skin = ResourceLocation.tryParse(tag.getString(KEY_SKIN));
        if (skin == null) return Optional.empty();

        Optional<ResourceLocation> decoration = tag.contains(KEY_DECORATION)
                ? Optional.ofNullable(ResourceLocation.tryParse(tag.getString(KEY_DECORATION)))
                : Optional.empty();

        return Optional.of(new BalloonStyle(skin, decoration, tag.getInt(KEY_COLOR), tag.getInt(KEY_TEXT_COLOR)));
    }
}
