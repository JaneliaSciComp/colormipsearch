package org.janelia.colormipsearch.cmd;

import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.janelia.colormipsearch.config.Config;
import org.janelia.colormipsearch.config.ConfigProvider;
import org.janelia.colormipsearch.dao.DaosProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class AbstractCmd {
    static final long _1M = 1024 * 1024;

    private static final Logger LOG = LoggerFactory.getLogger(AbstractCmd.class);
    private final static int LOW_MEMORY_PERC_THRESHOLD = 20;

    private final String commandName;
    private Config config;
    final long maxMemory;

    AbstractCmd(String commandName) {
        this.commandName = commandName;
        this.config = null;
        maxMemory = Runtime.getRuntime().maxMemory();
    }

    public String getCommandName() {
        return commandName;
    }

    abstract AbstractCmdArgs getArgs();

    boolean matches(String commandName) {
        return StringUtils.isNotBlank(commandName) && StringUtils.equals(this.commandName, commandName);
    }

    abstract void execute();

    Config getConfig() {
        if (config == null) {
            config = ConfigProvider.getInstance()
                    .fromDefaultResources()
                    .fromFile(getArgs().getConfigFileName())
                    .get();
        }
        return config;
    }

    DaosProvider getDaosProvider(boolean useIDGeneratorLock) {
        return DaosProvider.getInstance(getConfig(), useIDGeneratorLock);
    }

    synchronized void checkMemoryUsage() {
        long freeMemory = Runtime.getRuntime().freeMemory();
        int lowMemoryPercTh = getConfig().getIntegerPropertyValue("Memory.LowPercThreshold", LOW_MEMORY_PERC_THRESHOLD);
        long threshold = (maxMemory / 100) * lowMemoryPercTh;
        if (freeMemory < threshold) {
            LOG.warn("Free memory is below the {}% mark : {} bytes, max memory: {} bytes",
                    lowMemoryPercTh, freeMemory, maxMemory);
            System.gc();
        }
    }

    <T> String getShortenedName(Collection<T> elems, int maxLen, Function<T, String> toStrFunc) {
        return elems.stream().map(toStrFunc).limit(maxLen).collect(Collectors.joining(",", "", elems.size() == maxLen ? "" : ",..."));
    }
}
