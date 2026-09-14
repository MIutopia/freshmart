package com.freshmart.notification;

import java.util.regex.Pattern;

public final class HolidayCardContentPolicy {
    private static final Pattern SAFE_TEXT = Pattern.compile("[^\\p{L}\\p{N}，。！？、：；（）()《》“”‘’ .,!?:;\\-]");

    private HolidayCardContentPolicy() {
    }

    public static String sanitize(String value, int maxLength) {
        String result = value == null ? "" : SAFE_TEXT.matcher(value.trim()).replaceAll("");
        return result.substring(0, Math.min(result.length(), maxLength));
    }

    public static String render(String holidayKey, String greeting) {
        String safeHoliday = sanitize(holidayKey, 32);
        String safeGreeting = sanitize(greeting, 120);
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"640\" height=\"360\" viewBox=\"0 0 640 360\">"
                + "<rect width=\"640\" height=\"360\" fill=\"#f5f2e8\"/><path d=\"M0 280 Q160 220 320 280 T640 280 V360 H0Z\" fill=\"#d9ead3\"/>"
                + "<text x=\"320\" y=\"125\" text-anchor=\"middle\" font-size=\"34\" fill=\"#2f5d50\" font-family=\"sans-serif\">" + escapeXml(safeHoliday) + "</text>"
                + "<text x=\"320\" y=\"195\" text-anchor=\"middle\" font-size=\"22\" fill=\"#34495e\" font-family=\"sans-serif\">" + escapeXml(safeGreeting) + "</text>"
                + "<text x=\"320\" y=\"315\" text-anchor=\"middle\" font-size=\"18\" fill=\"#2f5d50\" font-family=\"sans-serif\">FreshMart - 新鲜每一天</text></svg>";
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
