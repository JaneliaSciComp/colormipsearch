package org.janelia.colormipsearch.cmd;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.beust.jcommander.IStringConverter;

import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.janelia.colormipsearch.dataio.DataSourceParam;

class ListArg {

    static class ListArgConverter implements IStringConverter<ListArg> {
        @Override
        public ListArg convert(String value) {
            List<String> argComponents = Arrays.stream(StringUtils.split(value, ':'))
                    .map(String::trim)
                    .collect(Collectors.toList());
            ListArg arg = new ListArg();
            if (argComponents.size() > 0) {
                arg.input = argComponents.get(0);
            }
            if (argComponents.size() > 1 && StringUtils.isNotBlank(argComponents.get(1))) {
                arg.setOffset(Integer.parseInt(argComponents.get(1)));
            }
            if (argComponents.size() > 2 && StringUtils.isNotBlank(argComponents.get(2))) {
                arg.setLength(Integer.parseInt(argComponents.get(2)));
            }
            return arg;
        }
    }

    String input;
    int offset = 0;
    int length = -1;

    private void setOffset(int offset) {
        if (offset > 0) {
            this.offset = offset;
        } else {
            this.offset = 0;
        }
    }

    private void setLength(int length) {
        this.length = length > 0 ? length : -1;
    }

    Path getInputPath() {
        return Paths.get(input);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        ListArg listArg = (ListArg) o;

        return new EqualsBuilder()
                .append(input, listArg.input)
                .append(offset, listArg.offset)
                .append(length, listArg.length).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(input)
                .append(offset)
                .append(length).toHashCode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(input);
        if (offset > 0 || length > 0) {
            sb.append(':');
        }
        if (offset > 0) {
            sb.append(offset);
        }
        if (length > 0) {
            sb.append(':').append(length);
        }
        return sb.toString();
    }

    String listArgName() {
        String fn = RegExUtils.removePattern(new File(input).getName(), "\\..*");
        if (length > 0) {
            return fn + "_" + Math.max(offset, 0) + "_" + length;
        } else {
            return fn;
        }
    }
}
