package com.rdh.swarm;

import java.io.InputStream;
import java.util.Properties;

/** The Gradle-generated build stamp. Loaded once from the jar resource. */
public final class BuildInfo {
    private static final String UNKNOWN = "unknown";
    private static final Properties PROPS = load();

    private static Properties load() {
        Properties props = new Properties();
        try (InputStream in = BuildInfo.class.getResourceAsStream("/build-info.properties")) {
            if (in != null) props.load(in);
        } catch (Exception ignored) {
            // A source-only build still has to compile and run.
        }
        return props;
    }

    private BuildInfo() {}

    public static String stamp() {
        return PROPS.getProperty("version", UNKNOWN);
    }

    public static String built() {
        return PROPS.getProperty("built", UNKNOWN);
    }

    public static String commit() {
        return PROPS.getProperty("commit", UNKNOWN);
    }

    /** Existing lifecycle code only asks for a string here; return the stamp. */
    static String z() {
        return stamp();
    }
}
