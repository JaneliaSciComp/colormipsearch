package org.janelia.colormipsearch.cmd;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import com.beust.jcommander.Parameter;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

class CommonArgs implements Serializable {
    @Parameter(names = {"--outputDir", "--output-dir", "-od"}, description = "Output directory")
    String outputDir;

    @Parameter(names = {"--cdsConcurrency", "--task-concurrency", "-tc", "-cdc"},
               description = "Task concurrency - number of tasks to run concurrently")
    int taskConcurrency;

    @Parameter(names = "--no-pretty-print", description = "Do not pretty print the results", arity = 0)
    boolean noPrettyPrint = false;

    @Parameter(names = {"--help", "-h"}, description = "Display the help message", help = true, arity = 0)
    boolean displayHelpMessage = false;

    static Set<String> toLowerCase(Set<String> f) {
        if (CollectionUtils.isEmpty(f)) {
            return Collections.emptySet();
        } else {
            return f.stream().filter(StringUtils::isNotBlank).map(String::toLowerCase).collect(Collectors.toSet());
        }
    }

}
