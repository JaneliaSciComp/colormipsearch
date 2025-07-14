package org.janelia.colormipsearch.cmd;

import java.util.Collections;
import java.util.List;

import com.beust.jcommander.IStringConverter;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.janelia.colormipsearch.cmd.dataexport.ImageStoreMapping;

class MultiKeyValueArg {

    static class MultiKeyValueArgConverter implements IStringConverter<MultiKeyValueArg> {
        @Override
        public MultiKeyValueArg convert(String value) {
            int separator = value.indexOf(':');
            if (separator == -1) {
                throw new IllegalArgumentException("Invalid MultiKeyValueArg - no key value separator found: " + value);
            }
            String keysString = value.substring(0, separator);
            String argValue = value.substring(separator + 1);
            List<String> argKeys = StringUtils.isBlank(keysString)
                    ? Collections.emptyList()
                    : Splitter.on('^').trimResults().splitToList(keysString);
            if (CollectionUtils.isEmpty(argKeys)) {
                throw new IllegalArgumentException("Invalid MultiKeyValueArg - all keys are empty: " + value);
            }
            return new MultiKeyValueArg(argKeys, argValue);
        }
    }

    final List<String> multiKey;
    final String value;

    private MultiKeyValueArg(List<String> multiKey, String value) {
        this.multiKey = multiKey;
        this.value = value;
    }

    @Override
    public String toString() {
        return String.join(",", multiKey) + ":" + value;
    }

}
