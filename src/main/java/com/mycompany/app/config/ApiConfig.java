package com.mycompany.app.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration class for managing API keys and URLs
 */
public class ApiConfig {
    private static final String CONFIG_FILE = "/application.properties";
    private static Properties properties;

    static {
        loadProperties();
    }

    private static void loadProperties() {
        properties = new Properties();
        try (InputStream input = ApiConfig.class.getResourceAsStream(CONFIG_FILE)) {
            if (input != null) {
                properties.load(input);
            } else {
                System.err.println("Unable to find " + CONFIG_FILE);
            }
        } catch (IOException e) {
            System.err.println("Error loading configuration: " + e.getMessage());
        }
    }

    public static String getCoinGeckoApiKey() {
        return properties.getProperty("coingecko.api.key", "");
    }

    public static String getCoinGeckoApiUrl() {
        return properties.getProperty("coingecko.api.url", "https://api.coingecko.com/api/v3");
    }

    public static String getSerpApiKey() {
        return properties.getProperty("serp.api.key", "");
    }

    public static String getSerpApiUrl() {
        return properties.getProperty("serp.api.url", "https://serpapi.com/search");
    }
}
