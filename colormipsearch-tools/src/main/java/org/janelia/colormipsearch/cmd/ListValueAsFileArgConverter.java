package org.janelia.colormipsearch.cmd;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.beust.jcommander.IStringConverter;

import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.shaded.com.google.common.io.Files;

class ListValueAsFileArgConverter implements IStringConverter<List<String>> {

    @Override
    public List<String> convert(String s) {
        return Arrays.stream(StringUtils.split(s, ','))
                .map(String::trim)
                .map(arg -> StringUtils.strip(arg, "'\""))
                .flatMap(arg -> arg.charAt(0) == '@' ? handleFileArg(arg) : Stream.of(arg))
                .collect(Collectors.toList());
    }

    private Stream<String> handleFileArg(String fnArg) {
        try {
            return Files.readLines(new File(fnArg.substring(1)), Charset.defaultCharset()).stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
