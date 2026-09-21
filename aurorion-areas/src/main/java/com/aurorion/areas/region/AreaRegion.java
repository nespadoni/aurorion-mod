package com.aurorion.areas.region;

import com.aurorion.areas.geometry.AreaVolume;
import com.aurorion.areas.rules.AreaRules;
import com.aurorion.areas.rules.Decision;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * @param house casa dona desta area, ou {@code null} se ela nao pertence a casa nenhuma.
 *              <p>Fica no cadastro da area, e nao em {@link AreaRules}, de proposito: regra e
 *              conteudo de perfil ({@code /area perfil} <b>copia</b> um preset por cima), e um
 *              preset compartilhado nao pode carregar junto a dona da casa. Dono e identidade
 *              daquela area, como o id e a prioridade.
 */
public record AreaRegion(String id, String name, ResourceLocation dimension, int priority, boolean enabled,
                         AreaVolume volume, AreaRules rules, Map<UUID, Set<String>> exceptions,
                         @Nullable ResourceLocation house) {
    /**
     * Area sem dona. Existe para os chamadores que nunca tiveram o conceito de casa continuarem
     * escritos como estavam — persistencia antiga, testes e todo {@code withX} abaixo.
     */
    public AreaRegion(String id, String name, ResourceLocation dimension, int priority, boolean enabled,
                      AreaVolume volume, AreaRules rules, Map<UUID, Set<String>> exceptions) {
        this(id, name, dimension, priority, enabled, volume, rules, exceptions, null);
    }

    public AreaRegion {
        if (!id.matches("[a-z0-9_-]{1,48}")) throw new IllegalArgumentException("Id: letras minusculas, numeros, _ e -, ate 48 caracteres.");
        if (name.isBlank() || name.length() > 96) throw new IllegalArgumentException("Nome deve ter 1 a 96 caracteres.");
        if (priority < -10000 || priority > 10000) throw new IllegalArgumentException("Prioridade entre -10000 e 10000.");
        if (exceptions.size() > 512) throw new IllegalArgumentException("Limite de 512 excecoes por area.");
        Map<UUID, Set<String>> copy = new HashMap<>();
        exceptions.forEach((uuid, keys) -> {
            if (keys.size() > 64) throw new IllegalArgumentException("Limite de 64 regras por excecao.");
            keys.forEach(AreaRules::validateKey);
            copy.put(uuid, Set.copyOf(keys));
        });
        exceptions = Map.copyOf(copy);
    }

    public Decision decision(String key, @Nullable UUID actor) { return decision(key, exceptionsOf(actor)); }
    /** Buscado uma vez quando varias regras da mesma area sao resolvidas para o mesmo personagem. */
    @Nullable public Set<String> exceptionsOf(@Nullable UUID actor) { return actor == null ? null : exceptions.get(actor); }
    public Decision decision(String key, @Nullable Set<String> allowed) {
        return allowed != null && allowed.contains(key) ? Decision.ALLOW : rules.flag(key);
    }
    public boolean beats(@Nullable AreaRegion other) {
        return other == null || priority > other.priority || priority == other.priority && id.compareTo(other.id) < 0;
    }
    public AreaRegion withRules(AreaRules value) { return new AreaRegion(id, name, dimension, priority, enabled, volume, value, exceptions, house); }
    public AreaRegion withVolume(AreaVolume value) { return new AreaRegion(id, name, dimension, priority, enabled, value, rules, exceptions, house); }
    public AreaRegion withPriority(int value) { return new AreaRegion(id, name, dimension, value, enabled, volume, rules, exceptions, house); }
    public AreaRegion withEnabled(boolean value) { return new AreaRegion(id, name, dimension, priority, value, volume, rules, exceptions, house); }
    public AreaRegion withName(String value) { return new AreaRegion(id, value, dimension, priority, enabled, volume, rules, exceptions, house); }
    public AreaRegion withHouse(@Nullable ResourceLocation value) { return new AreaRegion(id, name, dimension, priority, enabled, volume, rules, exceptions, value); }
    public AreaRegion withException(UUID actor, String key, boolean allow) {
        AreaRules.validateKey(key);
        Map<UUID, Set<String>> changed = new HashMap<>(exceptions);
        Set<String> keys = new HashSet<>(changed.getOrDefault(actor, Set.of()));
        if (allow) keys.add(key); else keys.remove(key);
        if (keys.isEmpty()) changed.remove(actor); else changed.put(actor, keys);
        return new AreaRegion(id, name, dimension, priority, enabled, volume, rules, changed, house);
    }
}
