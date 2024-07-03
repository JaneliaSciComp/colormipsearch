package org.janelia.colormipsearch.cmd;

import java.util.Arrays;

import com.beust.jcommander.JCommander;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CalculateGradientScoresCmdTest {

    @Test
    public void parseCmdArgs() {
        CalculateGradientScoresCmd cmd = new CalculateGradientScoresCmd(
                "gradScpore",
                new CommonArgs(),
                () -> 10L);
        JCommander jc = JCommander.newBuilder()
                .addCommand(cmd.getCommandName(), cmd.getArgs())
                .build();

        jc.parse("gradScpore",
                "--masks-libraries", "flyem_1", "flyem_2",
                "--processing-tag", "gradscore-123",
                "--masks-tags",
                "@src/test/resources/org/janelia/colormipsearch/cmd/farg1.txt", // handled automatically by JCommander
                "--masks-datasets",
                "\"@src/test/resources/org/janelia/colormipsearch/cmd/farg1.txt\"", // handled by ListValueAsFileArgConverter
                "--targets-datasets",
                "@src/test/resources/org/janelia/colormipsearch/cmd/farg1.txt", // handled by JCommander
                "'@src/test/resources/org/janelia/colormipsearch/cmd/farg2.txt',ds5", // handled by ListValueAsFileArgConverter
                "-as", "brain",
                "--match-tags", "3.2.0,3.2.1", "3.2.2,3.2.3",
                "--targets-tags", "1", "2", "3,4"
        );
        assertEquals(Arrays.asList(createListArg("flyem_1"), createListArg("flyem_2")), cmd.getArgs().masksLibraries);
        assertEquals("gradscore-123", cmd.getArgs().processingTag);
        assertEquals(Arrays.asList("3.2.0", "3.2.1", "3.2.2", "3.2.3"), cmd.getArgs().matchTags);
        assertEquals(Arrays.asList("1", "2", "3", "4"), cmd.getArgs().targetTags);
        assertEquals(Arrays.asList("ds1", "ds2"), cmd.getArgs().maskTags);
        assertEquals(Arrays.asList("ds1", "ds2"), cmd.getArgs().maskDatasets);
        assertEquals(Arrays.asList("ds1", "ds2", "ds3", "ds4", "ds5"), cmd.getArgs().targetDatasets);
    }

    private ListArg createListArg(String lname) {
        ListArg arg = new ListArg();
        arg.input = lname;
        return arg;
    }
}
