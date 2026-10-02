package com.aurorion.trama.skill;

import java.util.*;
import static com.aurorion.trama.skill.Rating.*;

/** One aggregate for all nodes. Conditional snapshots share the same final caps as static effects. */
public final class Build {
    public final Set<String> nodes;
    public final EnumMap<Rating, Double> ratings;
    public record Context(double health, boolean sprint, boolean sneak, boolean settled,
                          boolean vigilant, boolean momentum, boolean evasion,
                          boolean magicStep, boolean sprintOpening, boolean recovering) {}
    public static final class Effects {
        private final EnumMap<Rating, Double> values = new EnumMap<>(Rating.class);
        public double physicalReduction, projectileReduction;
        public double get(Rating rating) { return values.getOrDefault(rating, 0.0); }
        void add(Rating r, double amount) { values.merge(r, amount, Double::sum); }
        void scale(Rating r, double factor) { values.put(r, get(r)*factor); }
        void cap(Rating r, double low, double high) { values.put(r, clamp(get(r), low, high)); }
    }
    public Build(Set<String> nodes) { this.nodes = Set.copyOf(nodes); ratings = SkillCatalog.sum(nodes); }
    public boolean has(String id) { return nodes.contains(id); }
    public double rating(Rating r) { return ratings.getOrDefault(r, 0.0); }
    public Effects effects(Context c) {
        var r = new EnumMap<>(ratings);
        // Read original capped ratings so conversions never feed each other in a loop.
        convert(r, "C11", ARM, VIT, .25, 2); convert(r, "C12", VIT, ARM, .25, 2);
        convert(r, "C13", MOV, APS, .25, 3); convert(r, "C14", MR, MAG, .25, 3);
        convert(r, "C15", FALL, MOV, .25, 2);
        if (has("AT9")) r.merge(MAG, rating(VIT)*.30, Double::sum);
        else convert(r, "AT4", VIT, MAG, .20, 2);
        var e = new Effects(); r.forEach((key,value) -> e.add(key, value*key.step));
        if (has("AT9")) e.scale(VIT, .7);
        if (has("C16")) { scale(e,1.25,VIT,ARM,TGH,KBR,MR); scale(e,.8,MEL,RNG,MAG,APS); }
        if (has("C17")) { scale(e,1.2,MEL,RNG,MAG,APS); scale(e,.75,VIT,ARM,TGH); }
        if (has("IE9")) {
            if (c.health < .35) scale(e,1.25,VIT,ARM,TGH); else if (c.health > .35) e.add(MEL,-.03);
        }
        if (has("NV9")) { e.add(MR,.10); e.add(MAG,-.06); }
        if (has("VP9")) e.add(MEL,-.04);
        if (has("IO9")) {
            if (c.health > .5) e.physicalReduction += .04; else if (c.health < .5) e.add(MOV,-.02);
        }
        if (has("IB7") && c.settled) { e.add(ARM,.60); e.add(KBR,.02); }
        if (has("SR4") && c.settled) { e.add(ARM,.40); e.add(KBR,.04); }
        if (has("ID4") && !c.sprint) e.add(APS,.02);
        if (has("IE4") && c.health > .7) e.add(MEL,.02);
        if (has("IE7") && c.health < .35) { e.add(MEL,.03); e.add(MOV,-.02); }
        if (has("IO4") && c.health > .75) e.projectileReduction += .03;
        if (has("NW4") && c.vigilant) e.add(MAG,.03);
        if (has("SB4") && spread() <= 3) scaleAdd(e,.015,MEL,RNG,MAG);
        if (has("SB9") && Math.abs(rating(VIT)-rating(strongest())) <= 4) {
            e.add(strongest(),.02); e.add(VIT,.01);
        }
        if (has("NC9")) {
            if (c.sneak) scaleAdd(e,.04,RNG,MAG);
            if (c.sprint) scaleAdd(e,-.04,RNG,MAG);
        }
        if (has("VF4") && c.momentum) e.add(MOV,.015);
        if (has("VT4") && c.momentum) e.add(APS,.02);
        if (c.sneak) {
            if (has("VP4")) e.add(RNG,.04);
            if (has("VP9") && !c.sprint) e.add(RNG,.08);
        }
        if (c.evasion) {
            if (has("VA9")) { e.add(MOV,.03); scaleAdd(e,-.03,MEL,RNG,MAG); }
            else if (has("VA4")) e.add(MOV,.02);
        }
        if (has("BNA7") && c.magicStep) e.add(MOV,.015);
        if (has("BAV7") && c.sprintOpening) scaleAdd(e,.02,RNG,MAG);
        if (has("AL4") && c.health >= .4 && c.health <= .7) scaleAdd(e,.02,MEL,MAG);
        if (has("AL9")) {
            if (c.health >= .35 && c.health <= .6) { scaleAdd(e,.04,MEL,RNG,MAG); e.add(MOV,.015); }
            else if (c.health > .6) scaleAdd(e,-.02,MEL,RNG,MAG);
            else e.add(MOV,-.02);
        }
        if (has("AD4")) { e.add(strongest(),.01); e.add(weakest(),-.01); }
        if (has("AD9")) { e.add(strongest(),.02); e.add(weakest(),-.02); }
        if (has("BIS7") && c.health < .4) { e.physicalReduction += .02; e.add(MOV,-.01); }
        if (has("BSN7") && !c.sprint) { e.add(MAG,.015); e.add(BBR,.02); }
        if (has("SG9") && c.recovering) scaleAdd(e,-.04,MEL,RNG,MAG);
        if (has("C19")) { e.scale(ARM,.5); e.scale(KBR,.5); }
        if (has("C20")) { scale(e,1.5,ARM,TGH,KBR); e.values.put(MOV,0.0); }
        finish(e);
        return e;
    }
    private void convert(EnumMap<Rating,Double> out, String node, Rating from, Rating to, double ratio, double limit) {
        if (has(node)) out.merge(to, Math.min(limit,rating(from)*ratio), Double::sum);
    }
    public Rating strongest() {
        Rating result = MEL;
        for (Rating r : List.of(RNG,MAG)) if (rating(r) > rating(result)) result = r;
        return result;
    }
    public Rating weakest() {
        Rating result = MAG;
        for (Rating r : List.of(RNG,MEL)) if (rating(r) < rating(result)) result = r;
        return result;
    }
    private double spread() { return rating(strongest())-rating(weakest()); }
    public double damage(Effects e, Rating family, double eventBonus) {
        double result = e.get(family)+eventBonus;
        if (has("C18")) result = Math.max(result, .7*Math.max(e.get(MEL),Math.max(e.get(RNG),e.get(MAG))));
        return clamp(result,-.20,has("C18") ? .06 : family == MAG && has("AV9") ? .16 : .13);
    }
    private void finish(Effects e) {
        // Static caps first; final caps permit documented mastery and conditional additions.
        e.cap(VIT,0,has("AV9") ? .03 : .10); e.cap(ARM,0,has("C20") ? 3 : 1.80);
        e.cap(TGH,0,1.5); e.cap(KBR,0,.12); e.cap(MOV,-.10,.07); e.cap(APS,0,.08);
        e.cap(MR,0,.12); e.cap(FALL,0,has("C19") ? .20 : .25);
        if (has("C19")) e.values.put(FALL,Math.min(.20,Math.max(e.get(FALL),rating(FALL)*.025)));
        for (Rating r : List.of(BBR,BREACH,EREACH,SNEAK,WATER,BURN,OXY,SAFE,EXPKB,PSPD,SWEEP,ENV))
            e.cap(r,0,r.cap*r.step);
        for (Rating r : List.of(MEL,RNG,MAG)) e.cap(r,-.20,r == MAG && has("AV9") ? .16 : .13);
    }
    private static void scale(Effects e,double factor,Rating... rs) { for (Rating r:rs) e.scale(r,factor); }
    private static void scaleAdd(Effects e,double amount,Rating... rs) { for (Rating r:rs) e.add(r,amount); }
    public static double clamp(double v,double low,double high) { return Math.max(low,Math.min(high,v)); }
}
