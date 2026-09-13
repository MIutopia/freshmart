package com.freshmart.notification;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class HolidayCardContentPolicyTest {
    @Test
    void renderRemovesMarkupAndKeepsSvgStructure() {
        String svg = HolidayCardContentPolicy.render("中秋<script>", "团圆 & 开心 <img>");

        assertTrue(svg.startsWith("<svg "));
        assertFalse(svg.contains("<script"));
        assertFalse(svg.contains("<img"));
        assertTrue(svg.contains("中秋script"));
    }
}
