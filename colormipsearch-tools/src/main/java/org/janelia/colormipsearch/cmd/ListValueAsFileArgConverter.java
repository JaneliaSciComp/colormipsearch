package org.janelia.colormipsearch.cmd;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;

import com.beust.jcommander.IStringConverter;

import org.apache.hadoop.shaded.com.google.common.io.Files;

class ListValueAsFileArgConverter implements IStringConverter<String> {

    @Override
    public String convert(String s) {
        if (s.charAt(0) == '@') {
            try {
                return Files.readLines(new File(s.substring(1)), Charset.defaultCharset())
                        .stream().reduce("", (s1, s2) -> s1.equals("") ? s2 : s1 + "," + s2);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            return s;
        }
    }

}
