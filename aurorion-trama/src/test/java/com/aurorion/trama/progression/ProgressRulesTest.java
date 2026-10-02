package com.aurorion.trama.progression;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProgressRulesTest {
    private int earned(long xp, long seconds) { return ProgressRules.earned(xp,seconds,80,20,15,4); }
    @Test void discoveriesDoNotBypassTimeAndTimeDoesNotBypassExperience() {
        assertEquals(5,earned(100000,0));
        assertEquals(5,earned(0,10000000));
        assertEquals(5,earned(80,899));
        assertEquals(6,earned(80,900));
        assertEquals(6,earned(179,2040));
        assertEquals(7,earned(180,2040));
    }
    @Test void cumulativeCurvesReach45WithoutExceedingIt() {
        assertEquals(18800,ProgressRules.experienceRequired(45,80,20));
        assertEquals(62*3600,ProgressRules.secondsRequired(45,15,4));
        assertEquals(44,earned(18799,62*3600));
        assertEquals(44,earned(18800,62*3600-1));
        assertEquals(45,earned(Long.MAX_VALUE,Long.MAX_VALUE));
    }
    @Test void everyPointHasStrictlyIncreasingCumulativeRequirements() {
        for (int p=6;p<=45;p++) {
            assertTrue(ProgressRules.experienceRequired(p,80,20)>ProgressRules.experienceRequired(p-1,80,20));
            assertTrue(ProgressRules.secondsRequired(p,15,4)>ProgressRules.secondsRequired(p-1,15,4));
        }
    }
}
