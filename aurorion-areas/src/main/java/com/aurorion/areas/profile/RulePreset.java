package com.aurorion.areas.profile;

import com.aurorion.areas.data.AreaJson;
import com.aurorion.areas.rules.AreaRules;
import com.mojang.serialization.*;
import net.minecraft.resources.ResourceLocation;

public record RulePreset(ResourceLocation id, AreaRules rules) {
    public static Codec<RulePreset> codec(ResourceLocation id) {
        return Codec.PASSTHROUGH.comapFlatMap(value -> {
            try {
                return DataResult.success(new RulePreset(id,
                        AreaJson.readRules(value.convert(JsonOps.INSTANCE).getValue().getAsJsonObject())));
            } catch (RuntimeException error) {
                return DataResult.error(() -> "Perfil invalido: " + error.getMessage());
            }
        }, value -> new Dynamic<>(JsonOps.INSTANCE, AreaJson.writeRules(value.rules())));
    }
}
