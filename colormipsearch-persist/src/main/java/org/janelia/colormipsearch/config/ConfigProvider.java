package org.janelia.colormipsearch.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigProvider {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigProvider.class);
    private static final ConfigProvider INSTANCE = new ConfigProvider(new ConfigImpl());

    public static ConfigProvider getInstance() {
        return INSTANCE;
    }

    private final ConfigImpl config;

    private ConfigProvider(ConfigImpl config) {
        this.config = config;
    }

    public ConfigProvider fromDefaultResources() {
        return fromResource("nbdb.properties")
                .fromProperties(System.getProperties())
                ;
    }

    public ConfigProvider fromResource(String resourceName) {
        if (StringUtils.isBlank(resourceName)) {
            return this;
        }
        try (InputStream configStream = this.getClass().getClassLoader().getResourceAsStream(resourceName)) {
            return fromInputStream(configStream);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public ConfigProvider fromFile(String fileName) {
        if (StringUtils.isBlank(fileName)) {
            return this;
        }
        File file = new File(fileName);
        if (file.exists() && file.isFile()) {
            try (InputStream fileInputStream = Files.newInputStream(file.toPath())) {
                LOG.info("Reading application config from file {}", file);
                return fromInputStream(fileInputStream);
            } catch (IOException e) {
                LOG.error("Error reading configuration file {}", fileName, e);
                throw new UncheckedIOException(e);
            }
        } else {
            LOG.error("Configuration file {} not found", fileName);
            throw new IllegalArgumentException("Database config file was provided but it was not found: " + fileName);
        }
    }

    private ConfigProvider fromProperties(Properties properties) {
        properties.stringPropertyNames().forEach(pn -> config.properties.setProperty(pn, properties.getProperty(pn)));
        return this;
    }

    private ConfigProvider fromInputStream(InputStream stream) {
        try {
            config.properties.load(stream);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return this;
    }

    public Config get() {
        return config;
    }
}
