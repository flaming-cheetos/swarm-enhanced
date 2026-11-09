package com.rdh.swarm.web;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Web v2 assets live in the jar, not in a Java text block. The files are the
 * design surface; this class only loads them and substitutes the one template
 * value the page needs (the session token).
 */
public final class SwarmWebPanelV2 {
    private static final String HTML = load("index.html");
    private static final String CSS = load("app.css");
    private static final String JS = load("app.js");

    private SwarmWebPanelV2() {}

    public static boolean isAvailable() {
        return !HTML.isEmpty() && !CSS.isEmpty() && !JS.isEmpty();
    }

    public static String html(String token) {
        return HTML.replace("__SWARM_TOKEN__", escapeHtml(token == null ? "" : token));
    }

    public static String css() {
        return CSS;
    }

    public static String js() {
        return JS;
    }

    private static String load(String name) {
        try (InputStream in = SwarmWebPanelV2.class.getResourceAsStream("/web/v2/" + name)) {
            return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static String escapeHtml(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }
}
