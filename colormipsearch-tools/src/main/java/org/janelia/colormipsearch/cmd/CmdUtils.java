package org.janelia.colormipsearch.cmd;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.annotation.Nullable;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class CmdUtils {
    private static final Logger LOG = LoggerFactory.getLogger(CmdUtils.class);

    static Executor createCDSExecutor(CommonArgs args) {
        if (args.cdsConcurrency > 0) {
            LOG.info("Create a thread pool with {} worker threads ({} available processors for workstealing pool)",
                    args.cdsConcurrency, Runtime.getRuntime().availableProcessors());
            return Executors.newFixedThreadPool(
                    args.cdsConcurrency,
                    new ThreadFactoryBuilder()
                            .setNameFormat("CDSRUNNER-%d")
                            .setDaemon(true)
                            .build());
        } else {
            LOG.info("Create a workstealing pool with {} worker threads", Runtime.getRuntime().availableProcessors());
            return Executors.newWorkStealingPool(Runtime.getRuntime().availableProcessors() - 1);
        }
    }

    static void createOutputDirs(Path... outputDirs) {
        for (Path outputDir : outputDirs) {
            if (outputDir != null) {
                try {
                    // create output directory
                    Files.createDirectories(outputDir);
                } catch (IOException e) {
                    LOG.error("Error creating output directory: {}", outputDir, e);
                    System.exit(1);
                }
            }
        }
    }

    @Nullable
    static File getOutputFile(Path outputDir, File inputFile) {
        if (outputDir == null) {
            return null;
        } else {
            return outputDir.resolve(inputFile.getName()).toFile();
        }
    }

}
