package com.aurorion.ethereal.block.entity;

import com.aurorion.ethereal.ranking.BoardLine;
import com.aurorion.ethereal.ranking.BoardMode;
import com.aurorion.ethereal.ranking.BoardService;
import com.aurorion.ethereal.registry.EtherealBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * O Projetor Aeonico. Guarda o que projetar, o tamanho do painel e as linhas ja renderizadas.
 *
 * <p>Guardar o <b>texto pronto</b> em vez do ranking cru e o que mantem o cliente sem trabalho: ele
 * nao conhece casas, nao conhece datapack e nao ordena nada — recebe linhas e desenha. O custo disso
 * e o texto viajar de novo a cada mudanca, o que so acontece em evento (SDD §4.2).
 *
 * <p>Cinco linhas, fixo, como no mod de referencia: o painel foi desenhado para caber isso, e um
 * numero configuravel de linhas so criaria projetor com texto saindo da moldura.
 */
public final class AeonicProjectorBlockEntity extends BlockEntity {
    /** Largura do painel holografico, em blocos. */
    public static final float WIDTH_MIN = 1.0F;
    public static final float WIDTH_MAX = 2.6F;
    public static final float WIDTH_DEFAULT = 1.8F;

    /** Altura do painel holografico, em blocos. */
    public static final float HEIGHT_MIN = 1.2F;
    public static final float HEIGHT_MAX = 3.0F;
    public static final float HEIGHT_DEFAULT = 1.85F;

    /** Passo dos botoes de tamanho na tela de configuracao. */
    public static final float STEP = 0.1F;

    /** Quantas posicoes o painel mostra. */
    public static final int MAX_LINES = 5;

    private static final String KEY_MODE = "Mode";
    private static final String KEY_WIDTH = "HoloW";
    private static final String KEY_HEIGHT = "HoloH";
    private static final String KEY_LINES = "Lines";
    private static final String KEY_TEXT = "Text";
    private static final String KEY_COLOR = "Color";

    private BoardMode mode = BoardMode.TOP_HOUSES;
    private float holoWidth = WIDTH_DEFAULT;
    private float holoHeight = HEIGHT_DEFAULT;
    private List<BoardLine> renderedLines = List.of();

    public AeonicProjectorBlockEntity(BlockPos pos, BlockState state) {
        super(EtherealBlockEntities.AEONIC_PROJECTOR.get(), pos, state);
    }

    public BoardMode mode() {
        return mode;
    }

    public float holoWidth() {
        return holoWidth;
    }

    public float holoHeight() {
        return holoHeight;
    }

    public List<BoardLine> renderedLines() {
        return renderedLines;
    }

    /** Aplica o que veio da tela. Os limites sao reaplicados aqui: a tela nunca e autoridade. */
    public void applyConfiguration(BoardMode newMode, float newWidth, float newHeight) {
        mode = newMode;
        holoWidth = snap(newWidth, WIDTH_MIN, WIDTH_MAX);
        holoHeight = snap(newHeight, HEIGHT_MIN, HEIGHT_MAX);
        changedAndSync();
    }

    public void setRenderedLines(List<BoardLine> lines) {
        renderedLines = List.copyOf(lines.size() > MAX_LINES ? lines.subList(0, MAX_LINES) : lines);
        changedAndSync();
    }

    /** Arredonda ao passo de 0.1 antes de limitar — o mesmo que a tela faz nas setas. */
    private static float snap(float value, float min, float max) {
        return Mth.clamp(Math.round(value * 10.0F) / 10.0F, min, max);
    }

    private void changedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) BoardService.loaded(this);
    }

    @Override
    public void setRemoved() {
        BoardService.unloaded(this);
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(KEY_MODE, mode.id());
        tag.putFloat(KEY_WIDTH, holoWidth);
        tag.putFloat(KEY_HEIGHT, holoHeight);

        ListTag lines = new ListTag();
        for (BoardLine line : renderedLines) {
            CompoundTag entry = new CompoundTag();
            entry.putString(KEY_TEXT, Component.Serializer.toJson(line.text(), registries));
            entry.putInt(KEY_COLOR, line.color());
            lines.add(entry);
        }
        tag.put(KEY_LINES, lines);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mode = BoardMode.byId(tag.getString(KEY_MODE));
        holoWidth = snap(tag.getFloat(KEY_WIDTH), WIDTH_MIN, WIDTH_MAX);
        holoHeight = snap(tag.getFloat(KEY_HEIGHT), HEIGHT_MIN, HEIGHT_MAX);

        ListTag lines = tag.getList(KEY_LINES, Tag.TAG_COMPOUND);
        List<BoardLine> loaded = new ArrayList<>(Math.min(lines.size(), MAX_LINES));
        for (int i = 0; i < lines.size() && i < MAX_LINES; i++) {
            CompoundTag entry = lines.getCompound(i);
            // Texto que nao parseia mais (formato antigo, arquivo mexido a mao) vira linha vazia em
            // vez de derrubar a leitura do bloco inteiro — o proximo refresh reescreve tudo.
            MutableComponent text = Component.Serializer.fromJson(entry.getString(KEY_TEXT), registries);
            loaded.add(new BoardLine(text == null ? Component.empty() : text, entry.getInt(KEY_COLOR)));
        }
        renderedLines = List.copyOf(loaded);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider registries) {
        CompoundTag tag = packet.getTag();
        if (tag != null) loadAdditional(tag, registries);
    }
}
