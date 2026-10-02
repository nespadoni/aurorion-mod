package com.aurorion.trama.progression;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActivityWindowTest {
    @Test void stationarySpamAndTransportWithFixedCameraDoNotQualify() {
        var stationary = new ActivityWindow();
        var transport = new ActivityWindow();
        for (int i=0;i<180;i++) {
            stationary.interact(3);
            assertFalse(stationary.sample(0,64,0,i*30,0));
            assertFalse(transport.sample(i,64,0,0,0));
        }
    }
    @Test void explorationQualifiesOnlyOncePerCompleteMinute() {
        var window = new ActivityWindow();
        for (int i=0;i<59;i++) assertFalse(window.sample(i,64,0,i%2*45,0));
        assertTrue(window.sample(59,64,0,45,0));
        assertFalse(window.sample(60,64,0,0,0));
    }
    @Test void buildingRequiresMovementLookAndVariedInteractions() {
        var window = new ActivityWindow();
        for (int i=0;i<59;i++) {
            window.interact(i%2 == 0 ? 1 : 2);
            assertFalse(window.sample(i%2*3,64,0,i%2*45,0));
        }
        window.interact(2);
        assertTrue(window.sample(3,64,0,45,0));
    }
    @Test void teleportAndResetDiscardIncompleteWindows() {
        var window = new ActivityWindow();
        for (int i=0;i<59;i++) window.sample(i,64,0,i%2*45,0);
        assertFalse(window.sample(10000,64,0,45,0));
        window.clear();
        assertFalse(window.sample(10001,64,0,0,0));
    }
}
