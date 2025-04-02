package org.janelia.colormipsearch.cmd;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CmdUtils {
    private static final Logger LOG = LoggerFactory.getLogger(CmdUtils.class);

    static ExecutorService createCmdExecutor(CommonArgs args) {
        if (args.taskConcurrency > 0) {
            LOG.info("Create a thread pool with {} worker threads ({} available processors for workstealing pool)",
                    args.taskConcurrency, Runtime.getRuntime().availableProcessors());
            return Executors.newFixedThreadPool(
                    args.taskConcurrency,
                    new ThreadFactoryBuilder()
                            .setNameFormat("CMDRUNNER-%d")
                            .setDaemon(true)
                            .build());
        } else {
            LOG.info("Create a workstealing pool with {} worker threads", Runtime.getRuntime().availableProcessors());
            return Executors.newWorkStealingPool(Runtime.getRuntime().availableProcessors() - 1);
        }
    }

    static int getTaskConcurrency(CommonArgs args) {
        if (args.taskConcurrency > 0) {
            return args.taskConcurrency;
        } else {
            return Runtime.getRuntime().availableProcessors() - 1;
        }
    }
}
