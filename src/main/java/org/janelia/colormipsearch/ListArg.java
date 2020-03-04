package org.janelia.colormipsearch;

import java.util.List;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.SubParameter;
import com.google.common.base.Splitter;

import org.apache.commons.lang3.StringUtils;

class ListArg {

    static class ListArgConverter implements IStringConverter<ListArg> {
        @Override
        public ListArg convert(String value) {
            List<String> argComponents = Splitter.on(":").trimResults().splitToList(value);
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

    @SubParameter(order = 0)
    String input;
    @SubParameter(order = 1)
    int offset = 0;
    @SubParameter(order = 2)
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
}