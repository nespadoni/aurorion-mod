package com.aurorion.profissoes.compat;

/** Implementada somente no BodyPart real do LSO quando o mod esta instalado. */
public interface WoundPart {
    float aurorionHealth();
    float aurorionMaxHealth();
    boolean aurorionCritical();
    void aurorionTreat();
}
