package org.janelia.colormipsearch.cmd;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

class AbstractCmdArgs implements Serializable {
    List<String> validate() {
        return Collections.emptyList();
    }
}
