package com.aurorion.areas.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;

/**
 * Contorno de uma area ou selecao, enviado sob demanda a um unico membro da staff.
 *
 * <p>So sai de {@code /area visualizar}: nao acompanha o tick de ninguem e nenhuma outra area do
 * cadastro viaja junto. As coordenadas vao em {@code double} porque o cliente precisa subtrair a
 * posicao da camera antes de perder precisao — em X/Z de milhoes, um {@code float} ja erraria o
 * contorno por blocos inteiros.
 */
public record AreaOutlinePayload(ResourceLocation dimension, int ticks, List<Shape> shapes) implements CustomPacketPayload {
    /** Limites do dominio ({@code AreaVolume.MAX_PARTS} e {@code AreaShape.MAX_VERTICES}) repetidos aqui como teto de leitura. */
    public static final int MAX_SHAPES = 32, MAX_VERTICES = 128;
    public static final Type<AreaOutlinePayload> TYPE = new Type<>(ResourceLocation.parse("aurorion_areas:outline"));

    /** Circulo quando {@code radius > 0} (um unico ponto, o centro); caso contrario, poligono. */
    public record Shape(boolean hole, double minY, double maxY, double radius, double[] xs, double[] zs) {}

    public static final StreamCodec<RegistryFriendlyByteBuf, AreaOutlinePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeResourceLocation(payload.dimension);
                buf.writeVarInt(payload.ticks);
                buf.writeVarInt(payload.shapes.size());
                for (Shape shape : payload.shapes) {
                    buf.writeBoolean(shape.hole);
                    buf.writeDouble(shape.minY); buf.writeDouble(shape.maxY); buf.writeDouble(shape.radius);
                    buf.writeVarInt(shape.xs.length);
                    for (int i = 0; i < shape.xs.length; i++) { buf.writeDouble(shape.xs[i]); buf.writeDouble(shape.zs[i]); }
                }
            },
            buf -> {
                ResourceLocation dimension = buf.readResourceLocation();
                int ticks = buf.readVarInt();
                int count = buf.readVarInt();
                if (count < 0 || count > MAX_SHAPES) throw new IllegalArgumentException("Formas demais no contorno.");
                List<Shape> shapes = new ArrayList<>(count);
                for (int s = 0; s < count; s++) {
                    boolean hole = buf.readBoolean();
                    double minY = buf.readDouble(), maxY = buf.readDouble(), radius = buf.readDouble();
                    int vertices = buf.readVarInt();
                    if (vertices < 1 || vertices > MAX_VERTICES) throw new IllegalArgumentException("Vertices demais no contorno.");
                    double[] xs = new double[vertices], zs = new double[vertices];
                    for (int i = 0; i < vertices; i++) { xs[i] = buf.readDouble(); zs[i] = buf.readDouble(); }
                    shapes.add(new Shape(hole, minY, maxY, radius, xs, zs));
                }
                return new AreaOutlinePayload(dimension, ticks, shapes);
            });

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
