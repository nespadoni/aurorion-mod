package com.aurorion.limbo.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Credencial do webhook: STARTUP nunca e sincronizado com jogadores. */
public final class AuditWebhookConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<String> WEBHOOK_URL;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("auditoria");
        WEBHOOK_URL = builder
                .comment("Webhook da auditoria do Limbo. Vazio desliga o envio.",
                        "Contem uma credencial: configure somente neste arquivo STARTUP, nunca no SERVER.",
                        "Alterar exige reiniciar o servidor. O envio nao bloqueia o tick.")
                .define("webhookUrl", "");
        builder.pop();
        SPEC = builder.build();
    }

    private AuditWebhookConfig() {
    }
}
