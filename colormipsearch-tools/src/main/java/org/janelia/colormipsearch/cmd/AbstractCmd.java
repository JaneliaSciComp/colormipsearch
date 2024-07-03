package org.janelia.colormipsearch.cmd;

import org.apache.commons.lang3.StringUtils;
import org.janelia.colormipsearch.config.Config;
import org.janelia.colormipsearch.config.ConfigProvider;
import org.janelia.colormipsearch.dao.DaosProvider;

abstract class AbstractCmd {
    static final long _1M = 1024 * 1024;

    private final String commandName;
    private Config config;

    AbstractCmd(String commandName) {
        this.commandName = commandName;
        this.config = null;
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

    DaosProvider getDaosProvider() {
        return DaosProvider.getInstance(getConfig());
    }
}
