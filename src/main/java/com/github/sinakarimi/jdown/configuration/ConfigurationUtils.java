package com.github.sinakarimi.jdown.configuration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public class ConfigurationUtils {

    private static final String CONFIG_PATH = "src/main/resources/configuration.json";
    private static Map<String, Object> CONFIGS = null;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static boolean hasConfigs() {
        return CONFIGS != null && !CONFIGS.isEmpty();
    }

    public static void populateConfigs(boolean reload) throws IOException {
        if (CONFIGS == null || reload) {
            CONFIGS = MAPPER.readValue(Path.of(CONFIG_PATH).toFile(), new TypeReference<>() {});
        }
    }

    public static Object getConfig(String configName) {
        if (CONFIGS == null) {
            try {
                populateConfigs(false);
            } catch (IOException e) {
                throw new RuntimeException("failed to load configuration into the application", e);
            }
        }

        return CONFIGS.get(configName);
    }

    public static <T> T getConfig(String configName, Class<T> returnType) {
        if (returnType == null) {
            throw new IllegalArgumentException("The return type class cannot be null in getConfig input!!!!");
        }

        if (CONFIGS == null) {
            try {
                populateConfigs(false);
            } catch (IOException e) {
                throw new RuntimeException("failed to load configuration into the application", e);
            }
        }

        Object configVal = CONFIGS.get(configName);
        return returnType.cast(configVal);
    }

    public static class ConfigurationConstants {

        public static final String NUMBER_OF_THREADS = "numOfThreads";

    }

}
