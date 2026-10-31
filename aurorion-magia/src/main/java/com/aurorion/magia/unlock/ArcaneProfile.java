package com.aurorion.magia.unlock;

import com.aurorion.core.character.SkillTreeGate;
import com.aurorion.magia.AurorionMagia;
import com.aurorion.magia.config.MagiaConfig;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import io.redspace.ironsspellbooks.api.spells.SpellRarity;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * O que a Trama diz da magia de alguém: até que nível do Despertar Arcano o professor liberou, e qual
 * escola a pessoa escolheu como especialidade.
 *
 * <p>Lido pelo {@link SkillTreeGate} do core (a magia não importa a Trama) e guardado aqui, porque o
 * gate de conjuração roda a cada tentativa: recalcula só no login, no {@code SkillTreeChangedEvent} e no
 * reset de personagem. A conjuração lê um {@code HashMap}.
 *
 * <p>A especialidade também vira um modificador no atributo de poder da escola do Iron's
 * ({@code <escola>_spell_power}) — dano, cura e o que escala com poder sobem sem código por magia.
 */
public final class ArcaneProfile {
    public record Profile(int tier, @Nullable SchoolType specialty) {
        static final Profile NONE = new Profile(0, null);
    }

    private static final ResourceLocation SPECIALIST_MODIFIER = AurorionMagia.id("especialista");
    private static final Map<UUID, Profile> PROFILES = new HashMap<>();

    private ArcaneProfile() {
    }

    /** O modo do Despertar vale? Sem a Trama no pack, a magia volta sozinha à liberação por aula. */
    public static boolean nodeMode() {
        return "no".equals(MagiaConfig.UNLOCK_MODE.get()) && SkillTreeGate.installed();
    }

    public static Profile of(ServerPlayer player) {
        Profile profile = PROFILES.get(player.getUUID());
        if (profile == null) {
            profile = compute(player);
            PROFILES.put(player.getUUID(), profile);
        }
        return profile;
    }

    /** Nós mudaram (ou login, ou personagem novo): recalcula, reaplica o bônus de escola e o espelho do Iron's. */
    public static void refresh(ServerPlayer player) {
        Profile profile = compute(player);
        PROFILES.put(player.getUUID(), profile);
        applySpecialty(player, profile.specialty());
        SpellAccess.reconcile(player);
    }

    public static void forget(UUID player) {
        PROFILES.remove(player);
    }

    public static void clear() {
        PROFILES.clear();
    }

    /** Nível do Despertar que a magia, neste nível de conjuração, exige: 1, 2 ou 3 (proibida). */
    public static int requiredTier(AbstractSpell spell, int level) {
        if (SpellAccess.isForbidden(spell)) return 3;
        SpellRarity rarity = spell.getRarity(Math.max(level, spell.getMinLevel()));
        return rarity.compareTo(tier2Rarity()) >= 0 ? 2 : 1;
    }

    public static boolean isSpecialty(ServerPlayer player, @Nullable SchoolType school) {
        return school != null && school == of(player).specialty();
    }

    private static Profile compute(ServerPlayer player) {
        if (!SkillTreeGate.installed()) return Profile.NONE;
        int tier = SkillTreeGate.has(player, MagiaConfig.NODE_TIER3.get()) ? 3
                : SkillTreeGate.has(player, MagiaConfig.NODE_TIER2.get()) ? 2
                : SkillTreeGate.has(player, MagiaConfig.NODE_TIER1.get()) ? 1 : 0;
        SchoolType specialty = null;
        // Especialista sem o Despertar não existe: o nó fica comprado, mas não vale até o professor liberar.
        if (tier >= 1) {
            for (String entry : MagiaConfig.SPECIALTY_NODES.get()) {
                int split = entry.indexOf('=');
                if (split < 0 || !SkillTreeGate.has(player, entry.substring(split + 1))) continue;
                ResourceLocation school = ResourceLocation.tryParse(entry.substring(0, split));
                specialty = school == null ? null : SchoolRegistry.getSchool(school);
                break;
            }
        }
        return tier == 0 && specialty == null ? Profile.NONE : new Profile(tier, specialty);
    }

    private static SpellRarity tier2Rarity() {
        try {
            return SpellRarity.valueOf(MagiaConfig.TIER2_RARITY.get());
        } catch (IllegalArgumentException e) {
            return SpellRarity.RARE;
        }
    }

    /**
     * Um modificador só, no poder da escola da especialidade; o das outras escolas sai. Transitório: não
     * vai para o save do jogador, e o login o recoloca.
     */
    private static void applySpecialty(ServerPlayer player, @Nullable SchoolType specialty) {
        double amount = MagiaConfig.SPECIALIST_POWER.get();
        for (SchoolType school : SchoolRegistry.REGISTRY) {
            Optional<Holder.Reference<Attribute>> attribute = powerAttribute(school);
            if (attribute.isEmpty()) continue;
            AttributeInstance instance = player.getAttribute(attribute.get());
            if (instance == null) continue;
            instance.removeModifier(SPECIALIST_MODIFIER);
            if (school == specialty && amount > 0) {
                instance.addTransientModifier(new AttributeModifier(SPECIALIST_MODIFIER, amount,
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
        }
    }

    /** O Iron's nomeia o atributo pela escola: {@code irons_spellbooks:fire} → {@code irons_spellbooks:fire_spell_power}. */
    private static Optional<Holder.Reference<Attribute>> powerAttribute(SchoolType school) {
        ResourceLocation id = school.getId();
        return BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), id.getPath() + "_spell_power"));
    }
}
