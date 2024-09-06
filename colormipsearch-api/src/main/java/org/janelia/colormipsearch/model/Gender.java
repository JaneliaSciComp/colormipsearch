package org.janelia.colormipsearch.model;

import org.apache.commons.lang3.StringUtils;

public enum Gender {
    f("female"),
    m("male");

    private String val;

    Gender(String val) {
        this.val = val;
    }

    public static Gender fromVal(String s) {
        for (Gender gender : values()) {
            if (StringUtils.equalsIgnoreCase(gender.name(), s)
                    || StringUtils.equalsIgnoreCase(gender.val, s)) {
                return gender;
            }
        }
        return null;
    }
}
