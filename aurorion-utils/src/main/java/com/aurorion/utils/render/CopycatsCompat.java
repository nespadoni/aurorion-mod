package com.aurorion.utils.render;

import com.aurorion.utils.AurorionUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * O que os mixins do Copycats+ precisam do mod sem compilar contra as interfaces dele, que herdam do
 * Create: os tipos sao resolvidos por nome, uma vez, na primeira chamada.
 *
 * <p>So e carregada quando o Copycats esta no pack (os mixins que a usam ficam numa config com
 * {@code requiredMods = ["copycats"]}). Se o nome de alguma classe mudar numa versao futura, os
 * metodos daqui respondem "nao sei" e o mixin cai no caminho de reserva, sem derrubar o jogo.
 */
public final class CopycatsCompat {
    private static final String MULTI_STATE_BLOCK = "com.copycatsplus.copycats.foundation.copycat.multistate.IMultiStateCopycatBlock";
    private static final String COPYCAT_BLOCK_ENTITY = "com.copycatsplus.copycats.foundation.copycat.ICopycatBlockEntity";

    @Nullable private static final Class<?> MULTI_STATE_TYPE = load(MULTI_STATE_BLOCK);
    @Nullable private static final Class<?> BLOCK_ENTITY_TYPE = load(COPYCAT_BLOCK_ENTITY);
    /** {@code String defaultProperty()} do bloco multiestado, como {@code (Object) -> String}. */
    @Nullable private static final MethodHandle DEFAULT_PROPERTY = defaultProperty();

    @Nullable private static volatile BlockState copycatBase;

    private CopycatsCompat() {
    }

    /** O BE e de copycat (qualquer variante)? */
    public static boolean isCopycatBlockEntity(Object blockEntity) {
        return BLOCK_ENTITY_TYPE != null && BLOCK_ENTITY_TYPE.isInstance(blockEntity);
    }

    /** A parte padrao de um bloco multiestado, ou {@code null} se o bloco nao e multiestado. */
    @Nullable
    public static String defaultProperty(Block block) {
        if (MULTI_STATE_TYPE == null || DEFAULT_PROPERTY == null || !MULTI_STATE_TYPE.isInstance(block)) return null;
        try {
            return (String) DEFAULT_PROPERTY.invokeExact((Object) block);
        } catch (Throwable failure) {
            return null;
        }
    }

    /** O material "sem material" do Copycats: a base de copycat do Create, como o proprio mod usa. */
    public static BlockState copycatBase() {
        BlockState base = copycatBase;
        if (base == null) {
            Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "copycat_base"));
            base = (block == Blocks.AIR ? Blocks.STONE : block).defaultBlockState();
            copycatBase = base;
        }
        return base;
    }

    @Nullable
    private static Class<?> load(String name) {
        try {
            return Class.forName(name, false, CopycatsCompat.class.getClassLoader());
        } catch (ReflectiveOperationException | LinkageError failure) {
            AurorionUtils.LOGGER.warn("Copycats: classe {} nao encontrada; a protecao fica no modo de reserva.", name);
            return null;
        }
    }

    @Nullable
    private static MethodHandle defaultProperty() {
        if (MULTI_STATE_TYPE == null) return null;
        try {
            return MethodHandles.publicLookup()
                    .findVirtual(MULTI_STATE_TYPE, "defaultProperty", MethodType.methodType(String.class))
                    .asType(MethodType.methodType(String.class, Object.class));
        } catch (ReflectiveOperationException failure) {
            AurorionUtils.LOGGER.warn("Copycats: defaultProperty() nao encontrado ({}).", failure.toString());
            return null;
        }
    }
}
