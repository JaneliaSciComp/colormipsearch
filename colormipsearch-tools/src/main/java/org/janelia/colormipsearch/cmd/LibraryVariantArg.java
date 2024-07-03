package org.janelia.colormipsearch.cmd;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.IValueValidator;
import com.beust.jcommander.ParameterException;
import com.google.common.base.Splitter;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

class LibraryVariantArg {

    public static class LibraryVariantArgValidator implements IValueValidator<LibraryVariantArg> {
        @Override
        public void validate(String name, LibraryVariantArg value) throws ParameterException {
            if (StringUtils.isBlank(value.libraryName)) {
                throw new ParameterException("Library name is not specified");
            }
            if (value.variantType == null) {
                throw new ParameterException("Library variant type is not specified for " + value);
            }
            if (CollectionUtils.isEmpty(value.variantPaths)) {
                throw new ParameterException("Library variant path is not specified for " + value);
            }
        }
    }

    public static class ListLibraryVariantArgValidator implements IValueValidator<List<LibraryVariantArg>> {
        private final LibraryVariantArgValidator singleValueValidator = new LibraryVariantArgValidator();

        @Override
        public void validate(String name, List<LibraryVariantArg> listValue) throws ParameterException {
            for (LibraryVariantArg value: listValue) {
                singleValueValidator.validate(name, value);
            }
        }
    }

    static class LibraryVariantArgConverter implements IStringConverter<LibraryVariantArg> {
        @Override
        public LibraryVariantArg convert(String value) {
            List<String> argComponents = StringUtils.isBlank(value)
                ? Collections.emptyList()
                : Splitter.on(':')
                    .trimResults()
                    .splitToList(value);
            LibraryVariantArg arg = new LibraryVariantArg();
            if (argComponents.size() > 0) {
                arg.libraryName = argComponents.get(0);
            }
            if (argComponents.size() > 1 && StringUtils.isNotBlank(argComponents.get(1))) {
                arg.variantType = argComponents.get(1);
            }
            if (argComponents.size() > 2 && StringUtils.isNotBlank(argComponents.get(2))) {
                arg.variantPaths = Arrays.stream(StringUtils.split(argComponents.get(2), '^'))
                        .filter(StringUtils::isNotBlank)
                        .collect(Collectors.toSet());
            }
            if (argComponents.size() > 3 && StringUtils.isNotBlank(argComponents.get(3))) {
                arg.variantIgnoredPattern = argComponents.get(3);
            }
            if (argComponents.size() > 4 && StringUtils.isNotBlank(argComponents.get(4))) {
                arg.variantTypeSuffix = argComponents.get(4);
            }
            if (argComponents.size() > 5 && StringUtils.isNotBlank(argComponents.get(5))) {
                arg.variantNameSuffix = argComponents.get(5);
            }
            return arg;
        }
    }

    String libraryName;
    // variant type: CDM, grad, zgap, 3d-vol
    String variantType;
    // there can be more than one location for a specific variant
    // very often there is a location with significant variants and one with "junk" variants
    // and we may still want to keep these separate
    Collection<String> variantPaths;
    String variantIgnoredPattern;
    String variantTypeSuffix; // this is typically the suffix appended to a foldername such as _gradient or _RGB20x
    String variantNameSuffix; // this is a suffix that ends the filename itself such as _FL

    @Override
    public String toString() {
        return libraryName
            + ':' + variantType
            + ':' + StringUtils.join(variantPaths, '^')
            ;
    }

}
