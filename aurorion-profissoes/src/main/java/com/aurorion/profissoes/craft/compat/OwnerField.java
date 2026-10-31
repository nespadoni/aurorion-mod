package com.aurorion.profissoes.craft.compat;

import com.aurorion.profissoes.AurorionProfissoes;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

/**
 * Lê o campo {@code UUID ownerUuid} que algumas máquinas de mod guardam (o Let's Do grava quem abriu a
 * panela por último). Reflexão com o {@link Field} guardado por classe: o custo de achar o campo sai uma
 * vez; o resto é um {@code get}, e só quando uma receita termina.
 */
public final class OwnerField {
    private static final ClassValue<Optional<Field>> FIELDS = new ClassValue<>() {
        @Override
        protected Optional<Field> computeValue(Class<?> type) {
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                try {
                    Field field = c.getDeclaredField("ownerUuid");
                    if (field.getType() != UUID.class) return Optional.empty();
                    field.setAccessible(true);
                    return Optional.of(field);
                } catch (NoSuchFieldException ignored) {
                    // sobe para a superclasse
                } catch (RuntimeException e) {
                    AurorionProfissoes.LOGGER.warn("Ofício: não deu para ler o dono de {}", type.getName(), e);
                    return Optional.empty();
                }
            }
            return Optional.empty();
        }
    };

    private OwnerField() {
    }

    @Nullable
    public static UUID of(Object block) {
        Optional<Field> field = FIELDS.get(block.getClass());
        if (field.isEmpty()) return null;
        try {
            return (UUID) field.get().get(block);
        } catch (IllegalAccessException e) {
            return null;
        }
    }
}
