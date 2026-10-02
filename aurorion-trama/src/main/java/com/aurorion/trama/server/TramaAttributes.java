package com.aurorion.trama.server;

import com.aurorion.trama.skill.Build;
import com.aurorion.trama.skill.Rating;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.*;
import java.util.*;
import static com.aurorion.trama.skill.Rating.*;

final class TramaAttributes {
    private record Binding(Rating rating, Holder<Attribute> attribute, boolean relative, boolean negative,
                           ResourceLocation id) {}
    private static Binding bind(Rating r, Holder<Attribute> a, boolean relative, boolean negative) {
        return new Binding(r,a,relative,negative,ResourceLocation.fromNamespaceAndPath("aurorion_trama",r.name().toLowerCase(Locale.ROOT)));
    }
    private static final List<Binding> BINDINGS = List.of(
            bind(VIT,Attributes.MAX_HEALTH,true,false), bind(ARM,Attributes.ARMOR,false,false),
            bind(TGH,Attributes.ARMOR_TOUGHNESS,false,false), bind(KBR,Attributes.KNOCKBACK_RESISTANCE,false,false),
            bind(MOV,Attributes.MOVEMENT_SPEED,true,false), bind(APS,Attributes.ATTACK_SPEED,true,false),
            bind(FALL,Attributes.FALL_DAMAGE_MULTIPLIER,true,true), bind(BBR,Attributes.BLOCK_BREAK_SPEED,true,false),
            bind(BREACH,Attributes.BLOCK_INTERACTION_RANGE,false,false), bind(EREACH,Attributes.ENTITY_INTERACTION_RANGE,false,false),
            bind(SNEAK,Attributes.SNEAKING_SPEED,true,false), bind(WATER,Attributes.WATER_MOVEMENT_EFFICIENCY,false,false),
            bind(BURN,Attributes.BURNING_TIME,true,true), bind(OXY,Attributes.OXYGEN_BONUS,false,false),
            bind(SAFE,Attributes.SAFE_FALL_DISTANCE,false,false), bind(EXPKB,Attributes.EXPLOSION_KNOCKBACK_RESISTANCE,false,false),
            bind(SWEEP,Attributes.SWEEPING_DAMAGE_RATIO,false,false));
    static void apply(ServerPlayer player, Build.Effects effects) {
        for (var binding : BINDINGS) {
            var instance = player.getAttribute(binding.attribute);
            if (instance == null) continue;
            double amount = effects == null ? 0 : effects.get(binding.rating)*(binding.negative ? -1 : 1);
            var old = instance.getModifier(binding.id);
            if (old != null && Double.compare(old.amount(),amount) == 0) continue;
            instance.removeModifier(binding.id);
            if (amount != 0) instance.addTransientModifier(new AttributeModifier(binding.id,amount,
                    binding.relative ? AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL : AttributeModifier.Operation.ADD_VALUE));
        }
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }
}
