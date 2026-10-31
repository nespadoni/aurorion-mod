package com.aurorion.magia.mixin;

import com.aurorion.magia.config.MagiaConfig;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.capabilities.magic.SyncedSpellData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Com {@code authoritative} ligado, o login do Iron's Restrictions para de ensinar as
 * {@code DefaultLearntSpells}.
 *
 * <p>O {@code SpellAccess.reconcile} roda logo depois e esquece tudo o que nao foi liberado pelo
 * Aurorion — entao ensinar so servia para desfazer em seguida. E cada magia ensinada saia num
 * {@code learnSpell} com pacote proprio para quem logou e para todos em volta: com a lista padrao
 * cheia, dezenas de pacotes por login, a cada login, e era nesse laco que o Iron's derrubava a plateia
 * (ver {@link SyncPlayerDataPacketMixin}).
 *
 * <p>Com {@code authoritative} desligado, nada muda. Fica numa config de mixin opcional e com
 * {@code @Pseudo}: sem o Restrictions, ou com ele mudado, o servidor sobe com o comportamento original.
 */
@Pseudo
@Mixin(targets = "com.relimer.ironsrestrictions.player.PlayerJoinHandler", remap = false)
public abstract class RestrictionsJoinMixin {
    @Redirect(method = "onPlayerLogin", require = 0, at = @At(value = "INVOKE",
            target = "Lio/redspace/ironsspellbooks/capabilities/magic/SyncedSpellData;learnSpell(Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;)V"))
    private static void aurorion_magia$skipDefaults(SyncedSpellData data, AbstractSpell spell) {
        if (!MagiaConfig.AUTHORITATIVE.get()) data.learnSpell(spell);
    }
}
