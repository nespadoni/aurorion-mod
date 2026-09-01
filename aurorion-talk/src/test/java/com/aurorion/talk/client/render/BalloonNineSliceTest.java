package com.aurorion.talk.client.render;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BalloonNineSliceTest {
    @Test
    void emitsNineSlicesAndArrowForNewestBalloon() {
        List<int[]> boxes = new ArrayList<>();

        BalloonNineSlice.emit((x, y, w, h, u, v, uw, vh) ->
                boxes.add(new int[]{x, y, w, h, u, v, uw, vh}), 21, 2, 4, true);

        assertEquals(10, boxes.size());
        assertEquals(-14, BalloonNineSlice.top(2, 4));
        assertArrayEquals(new int[]{-3, 9, 7, 4, 18, 6, 7, 4}, boxes.get(9));
    }

    @Test
    void olderBalloonHasNoArrow() {
        int[] count = {0};

        BalloonNineSlice.emit((x, y, w, h, u, v, uw, vh) -> count[0]++, 21, 1, 0, false);

        assertEquals(9, count[0]);
    }
}
