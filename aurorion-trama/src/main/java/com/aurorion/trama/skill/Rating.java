package com.aurorion.trama.skill;

public enum Rating {
    VIT(16,.005), ARM(12,.15), TGH(10,.10), KBR(8,.01), MOV(12,.0035), APS(12,.005),
    MEL(15,.006), RNG(15,.006), MAG(15,.006), MR(10,.0075), FALL(8,.02), BBR(10,.01),
    BREACH(6,.05), EREACH(5,.02), SNEAK(8,.015), WATER(5,.02), BURN(6,.03), OXY(4,.05),
    SAFE(4,.25), EXPKB(5,.02), PSPD(10,.01), SWEEP(6,.03), ENV(8,.01);
    public final double cap, step;
    Rating(double cap, double step) { this.cap = cap; this.step = step; }
    public double clamp(double value) { return Math.max(0, Math.min(cap, value)); }
}
