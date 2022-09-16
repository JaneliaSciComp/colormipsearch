package org.janelia.colormipsearch.model;

import org.janelia.colormipsearch.dto.AbstractNeuronMetadata;
import org.janelia.colormipsearch.dto.PPPMatchedTarget;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PPPMatchEntityTest {

    @Test
    public void extractSampleNameAndObjective() {
        PPPMatchEntity<TestEMNeuronEntity, TestLMNeuronEntity> testPPPM =
                createTestPPPMatchEntity("577720000--RT_18U", "BJD_128D10_AE_01-20171208_61_E3_REG_UNISEX_40x");
        PPPMatchedTarget<? extends AbstractNeuronMetadata> testPPPMetadata = testPPPM.metadata();
        assertEquals("BJD_128D10_AE_01-20171208_61_E3", testPPPMetadata.getSourceLmName());
        assertEquals("40x", testPPPMetadata.getSourceObjective());
    }

    private PPPMatchEntity<TestEMNeuronEntity, TestLMNeuronEntity> createTestPPPMatchEntity(
            String sourceEmName,
            String sourceLmName
    ) {
        PPPMatchEntity<TestEMNeuronEntity, TestLMNeuronEntity> testPPPM = new PPPMatchEntity<>();
        testPPPM.addTag("2.3.0");
        testPPPM.setMaskImageRefId(Long.valueOf("3104924552016343171"));
        testPPPM.setMirrored(true);
        testPPPM.setSourceEmName(sourceEmName);
        testPPPM.setSourceEmLibrary("flyem_hemibrain_1_2_1");
        testPPPM.setSourceLmName(sourceLmName);
        testPPPM.setSourceLmLibrary("flylight_gen1_mcfo_published");
        testPPPM.setCoverageScore(-83.89210580042597);
        testPPPM.setAggregateCoverage(96.32401522934352);
        testPPPM.setRank(19.5);
        testPPPM.addSourceImageFile("/nrs/saalfeld/maisl/flymatch/all_hemibrain_1.2_NB/setup22_nblast_20/results/00/577720000--RT_18U/lm_cable_length_20_v4_iter_2_tanh/screenshots/577720000--RT_18U_hr_1_hscore_0_cr_32_cscore_ 83_BJD_128D10_AE_01-20171208_61_E3_REG_UNISEX_40x_1_raw.png");
        testPPPM.addSourceImageFile("/nrs/saalfeld/maisl/flymatch/all_hemibrain_1.2_NB/setup22_nblast_20/results/00/577720000--RT_18U/lm_cable_length_20_v4_iter_2_tanh/screenshots/577720000--RT_18U_hr_1_hscore_0_cr_32_cscore_ 83_BJD_128D10_AE_01-20171208_61_E3_REG_UNISEX_40x_6_ch_skel.png");
        testPPPM.addSourceImageFile("/nrs/saalfeld/maisl/flymatch/all_hemibrain_1.2_NB/setup22_nblast_20/results/00/577720000--RT_18U/lm_cable_length_20_v4_iter_2_tanh/screenshots/577720000--RT_18U_hr_1_hscore_0_cr_32_cscore_ 83_BJD_128D10_AE_01-20171208_61_E3_REG_UNISEX_40x_3_skel.png");
        testPPPM.addSourceImageFile("/nrs/saalfeld/maisl/flymatch/all_hemibrain_1.2_NB/setup22_nblast_20/results/00/577720000--RT_18U/lm_cable_length_20_v4_iter_2_tanh/screenshots/577720000--RT_18U_hr_1_hscore_0_cr_32_cscore_ 83_BJD_128D10_AE_01-20171208_61_E3_REG_UNISEX_40x_2_masked_raw.png");
        testPPPM.addSourceImageFile("/nrs/saalfeld/maisl/flymatch/all_hemibrain_1.2_NB/setup22_nblast_20/results/00/577720000--RT_18U/lm_cable_length_20_v4_iter_2_tanh/screenshots/577720000--RT_18U_hr_1_hscore_0_cr_32_cscore_ 83_BJD_128D10_AE_01-20171208_61_E3_REG_UNISEX_40x_5_ch.png");
        return testPPPM;
    }
}
