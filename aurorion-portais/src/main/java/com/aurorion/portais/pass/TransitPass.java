package com.aurorion.portais.pass;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Autorizacao individual para atravessar uma dimensao fora do horario da linha.
 *
 * <p>Vale nos dois sentidos, de proposito: o passe que tira alguem do Nether e o mesmo que poe. E o
 * unico jeito de o passe funcionar como <em>resgate</em> — que e o caso de uso que importa quando
 * alguem perdeu o trem e quer sair.
 *
 * <p>Este record e o encaixe deliberado para o item de "abrir portal" que ainda vai existir: o item
 * so precisa chamar {@link PassData#grant}, sem saber nada sobre horario, linha ou relogio.
 */
public record TransitPass(ResourceKey<Level> dimension, long expiresAt, int uses) {
    /** {@code expiresAt = 0}: nao expira por tempo. */
    public static final long NO_EXPIRY = 0L;

    /** {@code uses < 0}: nao gasta por uso — vale enquanto nao expirar. */
    public static final int UNLIMITED_USES = -1;

    private static final String KEY_DIMENSION = "Dimension";
    private static final String KEY_EXPIRES = "ExpiresAt";
    private static final String KEY_USES = "Uses";

    public boolean isValidAt(long nowMillis) {
        if (uses == 0) {
            return false;
        }
        return expiresAt == NO_EXPIRY || nowMillis < expiresAt;
    }

    public boolean expiresByTime() {
        return expiresAt != NO_EXPIRY;
    }

    public boolean expiresByUse() {
        return uses >= 0;
    }

    /** @return o passe com um uso a menos, ou {@code null} quando esse era o ultimo. */
    @Nullable
    public TransitPass consumed() {
        if (!expiresByUse()) {
            return this;
        }
        int left = uses - 1;
        return left <= 0 ? null : new TransitPass(dimension, expiresAt, left);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(KEY_DIMENSION, dimension.location().toString());
        tag.putLong(KEY_EXPIRES, expiresAt);
        tag.putInt(KEY_USES, uses);
        return tag;
    }

    /** @return {@code null} se o id da dimensao gravado nao for mais parseavel. */
    @Nullable
    public static TransitPass load(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(KEY_DIMENSION));
        if (id == null) {
            return null;
        }
        // A dimensao nao precisa existir agora: um passe para uma dimensao de mod desinstalado fica
        // guardado inerte e volta a valer se o mod voltar, em vez de sumir do inventario de alguem.
        return new TransitPass(ResourceKey.create(Registries.DIMENSION, id), tag.getLong(KEY_EXPIRES), tag.getInt(KEY_USES));
    }
}
