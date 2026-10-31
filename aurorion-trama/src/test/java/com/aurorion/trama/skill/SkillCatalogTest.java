package com.aurorion.trama.skill;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SkillCatalogTest {
    @Test void arcaneNodesAreStaffOnlyAndGrantNoRatings() {
        assertEquals(283, SkillCatalog.ids().size());
        assertEquals(List.of("AR1", "AR2", "AR3"), SkillCatalog.staffOnlyIds());
        for (String id : List.of("AR1", "ES_FIRE", "ES_ELDRITCH")) {
            assertTrue(SkillCatalog.contains(id));
            assertTrue(SkillCatalog.ratings(id).isEmpty(), id);
        }
        assertFalse(SkillCatalog.staffOnly("ES_FIRE"));
        assertFalse(SkillCatalog.staffOnly("I00"));
    }
}
