# Distributed Color Depth MIP Search

[![DOI](https://zenodo.org/badge/98349943.svg)](https://zenodo.org/badge/latestdoi/98349943)
[![Java CI with Maven](https://github.com/JaneliaSciComp/colormipsearch/actions/workflows/maven.yml/badge.svg)](https://github.com/JaneliaSciComp/colormipsearch/actions/workflows/maven.yml)

This is a distributed version of the [ColorMIP_Mask_Search](https://github.com/JaneliaSciComp/ColorMIP_Mask_Search) Fiji plugin, running on Apache Spark. 

## Build

```./mvnw install```
or just
```./mvnw package```

This will produce a jar called `target/colormipsearch-<VERSION>-jar-with-dependencies.jar` which can be run 
either with Spark or on the local host or on the cluster by using bsub.

## Release the artifacts to Janelia Nexus Repo:

Before running the release script make sure you have an server entry
for the janelia-repo in your default maven settings.xml, typically located
at ~/.m2/settings.xml

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd">

  <servers>
    <server>
      <id>janelia-releases</id>
      <username>yourusername</username>
      <password>yourpassword</password>
    </server>
  </servers>

</settings>
```

If you don't want the password in clear in settings.xml, maven offers a mechanism to encrypt it
using:
```
mvn --encrypt-master-password <password>
```
to create a master password and then use
```
mvn --encrypt-password <password>
```
which you can enter in place of your password. Check [maven documentation](https://maven.apache.org/guides/mini/guide-encryption.html)
how you can do this.

To release the artifacts simply run:

```./release.sh <version>```

This command will also tag the repository with the `<version>`.


## Run

### Perform color depth search for one mask and one image only
```
java  -Xmx120G -Xms120G -jar target/colormipsearch-2.8.0-jar-with-dependencies.jar \
    searchLocalFiles \
    -i /nrs/jacs/jacsData/filestore/system/ColorDepthMIPs/JRC2018_Unisex_20x_HR/flylight_splitgal4_drivers/GMR_MB242A-20121208_31_H4-20x-Brain-JRC2018_Unisex_20x_HR-1846510864534863970-CH1_CDM.png \
    -m /nrs/jacs/jacsData/filestore/system/ColorDepthMIPs/JRC2018_Unisex_20x_HR/flyem_hemibrain/5901194966_RT_18U.tif \
    --maskThreshold 100 \
    -od /groups/jacs/jacsDev/devstore/goinac/cdtest/test3
```

Here's another example to compare images from a zip archive in which we compare the first 1000
images from `/nrs/jacs/jacsData/filestore/system/flylight_40xMCFO_Segmented_PackBits.zip` with 
all images from `/groups/scicomp/scicompsoft/otsuna/Brain/EM_Hemibrain/Masayoshi_selected.zip`

```
java -Xmx480G -Xms480G -jar target/colormipsearch-2.8.0-jar-with-dependencies.jar \
    searchLocalFiles \
    -i /nrs/jacs/jacsData/filestore/system/flylight_40xMCFO_Segmented_PackBits.zip:0:1000 \
    -m /groups/scicomp/scicompsoft/otsuna/Brain/EM_Hemibrain/Masayoshi_selected.zip \
    -od local/testData/masayoshi-cds \
    -gp /nrs/jacs/jacsData/filestore/system/flylight_40xMCFO_gradient_20px.zip \
    -result masayoshiResult-0-1000 \
    -lps 1000
```

### Calculating the gradient score for a set of existing results
```
java -jar target/colormipsearch-2.8.0-jar-with-dependencies.jar \
    gradientScore \
    -rf local/testData/results/qq.json \
    -gp local/testData/flylight_40xMCFO_gradient_20px.zip \
    -rd local/testData/results.withscore
```

## Pre-computed color depth search data

A more detailed description can be found in [PrecomputedData.md](PrecomputedData.md).

## Generating EM - LM precomputed color depth search

This section shows the steps and the commands used for generating 
the precomputed color depth search data

### Generate the MIPs metadata for LM lines and EM bodies

The MIPs metadata as explained in [PrecomputedData.md](PrecomputedData.md) is 
important for LM line or EM body lookup.
 
#### Generate LM SplitGal4 MIPs:
```bash
java \
    -jar target/colormipsearch-2.8.0-jar-with-dependencies.jar \
    groupMIPsByPublishedName \
    --jacsURL http://goinac-ws1.int.janelia.org:8800/api/rest-v2 \
    --authorization "Bearer tokenvalue" \
    -l flylight_split_gal4_published \
    --segmented-image-handling 0 \
    -od local/testData/mips \
    -lmdir split_gal4_lines
```

#### Generate LM MCFO MIPs:
```bash
java \
    -jar target/colormipsearch-2.8.0-jar-with-dependencies.jar \
    groupMIPsByPublishedName \
    --jacsURL http://goinac-ws1.int.janelia.org:8800/api/rest-v2 \
    --authorization "Bearer tokenvalue" \
    -l flylight_gen1_mcfo_published \
    --segmented-image-handling 0 \
    -od local/testData/mips \
    -lmdir gen1_mcfo_lines
```

#### Generate EM MIPs:
```bash
java \
    -jar target/colormipsearch-2.8.0-jar-with-dependencies.jar \
    groupMIPsByPublishedName \
    --jacsURL http://goinac-ws1.int.janelia.org:8800/api/rest-v2 \
    --authorization "Bearer tokenvalue" \
    -l flyem_hemibrain \
    -od local/testData/mips \
    -emdir em_bodies
```

### Generate EM - LM color depth search results

The steps to generate the precomputed color depth search results are the
following:

* Prepare the input file for the color depth matches 
* Calculate color depth matches between EM MIPs and LM MIPs
    * this step outputs one set of color depth matches grouped by EM MIP ID and 
    one set of matches grouped by the LM MIP ID 
* Calculate gradient based score for the matches from EM MIPs to LM MIPs, considering
only color depth matches grouped by EM MIP ID.
* Update the gradient score for the LM to EM matches using the
gradient score from the corresponding EM to LM match calculated in the previous step
* Merge results - if the color depth search results between EM and various LM libraries
were computed separately, for example EM vs Split-Gal4 matches were calculated 
separately from EM vs MCFO, then this step will merge the EM vs Split-Gal4 and
EM vs MCFO in a single result file so that all EM matches can be ranked appropriately
* Rank final results and cleanup - this basically sorts the final results
based on their gradient score and cleans up data that is not necessary for the release, 
such as image file names.


#### Step 1: Prepare LM and EM JSON input for color depth search

For MCFO and SplitGal4 input we use the corresponding segmented images
as input (see the '--segmented-mips-base-dir' argument).

Prepare MCFO input:
```bash
java \
    -jar target/colormipsearch-2.8.0-jar-with-dependencies.jar \
    createColorDepthSearchJSONInput \
    --jacsURL http://goinac-ws1.int.janelia.org:8800/api/rest-v2 \
    --authorization "Bearer tokenvalue" \
    -l flylight_gen1_mcfo_published \
    --segmented-mips-base-dir /nrs/jacs/jacsData/filestore/system/40x_MCFO_Segmented_PackBits_forPublicRelease.zip \
    --segmented-image-handling 0 \
    -od local/testData/mips 
```
Prepare SplitGal4 input:
```bash
java \
    -jar target/colormipsearch-2.8.0-jar-with-dependencies.jar \
    createColorDepthSearchJSONInput \
    --jacsURL http://goinac-ws1.int.janelia.org:8800/api/rest-v2 \
    --authorization "Bearer tokenvalue" \
    -l flylight_split_gal4_published \
    --segmented-mips-base-dir /nrs/jacs/jacsData/filestore/system/SS_Split/SS_Split_ALL_Segmented_CDM \
    --segmented-image-handling 0 \
    -od local/testData/mips
```
Prepare EM input:
```bash
java \
    -jar target/colormipsearch-2.8.0-jar-with-dependencies.jar \
    createColorDepthSearchJSONInput \
    --jacsURL http://goinac-ws1.int.janelia.org:8800/api/rest-v2 \
    --authorization "Bearer tokenvalue" \
    -l flyem_hemibrain \
    -od local/testData/mips
```

#### JSON input only for new MIPs

If you only need to run the color depth search for some new MIPS
that have been added to the library you can do that using the following command:
```bash
java \
    -jar target/colormipsearch-2.8.0-jar-with-dependencies.jar \
    createColorDepthSearchJSONInput \
    --jacsURL http://goinac-ws1.int.janelia.org:8800/api/rest-v2  \
    --authorization "Bearer tokenvalue" \
    -l flylight_split_gal4_published \
    -od local/testData/mips \
    --output-filename new_splitgal4_published \
    --excluded-mips local/testData/mips/existing_splitgal4_published.json
```
The command assumes that "local/testData/mips/existing_splitgal4_published.json" is 
a JSON file that contains the metadat for all MIPs that you don't need to compute and
it will put the MIPs that require color depth search in the 
"new_splitgal4_published.json" ("--excluded-mips" argument) under 
"local/testData/mips" directory ("-od" argument)

"createColorDepthSearchJSONInput" also supports an "--excluded-libraries"
which checks the case in which a MIP is part of multiple libraries
and if it is part of any library specified in the --excluded-lubraries list
then the MIP will not be included in the output JSON.  

#### Step 1': Replace image URLs

Sometime the image URLs need to be replaced with image URLs that 
reference MIPs generated with a different gamma value that are better 
suited for viewing the matched regions. Image URLs can 
be updated at any time but the sooner we apply this the fewer 
files that need to be updated downstream.  

This step requires a JSON file that contains the new image URLs 
that will replace the ones that are set in the JSON created from 
'flylight_gen1_mcfo_published' library. The new URLs for example could
come from the MIPs generated with a different gamma value. There are 
two sets of JSON files that will have to have the imageURLs update:
 * the MCFO lines will have to be changed to reference the new URLs
 * the EM to MCFO color depth search results reference the image of 
 the MCFO MIP that matches the EM MIP entry, so these will have to be updated
 to reference the new URL.

```bash
java \
    -jar target/colormipsearch-2.8.0-jar-with-dependencies.jar \
    createColorDepthSearchJSONInput \
    --jacsURL http://goinac-ws1.int.janelia.org:8800/api/rest-v2 \
    --authorization "Bearer tokenvalue" \
    -l flylight_gen1_mcfo_case_1_gamma1_4 \
    -od local/testData/mips
```

Update image URLs for the lines:
```bash
java \
    -jar target/colormipsearch-2.8.0-jar-with-dependencies.jar \
    replaceAttributes \
    -attrs local/testData/mips/flylight_gen1_mcfo_case_1_gamma1_4.json \
    --input-dirs local/testData/mips/gen1_mcfo_lines \
    --id-field id \
    --fields-toUpdate imageURL thumbnailURL \
    -od local/testData/mips.gamma1.4/gen1_mcfo_lines
```

Update image URLs for the input MIPs:
```bash
java \
    -jar target/colormipsearch-2.8.0-jar-with-dependencies.jar \
    replaceAttributes \
    -attrs local/testData/mips/flylight_gen1_mcfo_case_1_gamma1_4.json \
    --input-file local/testData/mips/flylight_gen1_mcfo_published.json \
    --id-field id \
    --fields-toUpdate imageURL thumbnailURL \
    -od local/testData/mips.gamma1.4
```

This step can also be applied later to replace the imageURLs 
in the result files. As a reminder the imageURLs in the result
files reference the matched image so the field that we lookup for 
a match is "matchedId" 
```bash
java \
    -jar target/colormipsearch-2.8.0-jar-with-dependencies.jar \
    replaceAttributes \
    -attrs local/testData/cdsresult/flylight_gen1_mcfo_case_1_gamma1_4.json \
    --input-dirs local/testData/cdsresults.merged/flyem_hemibrain-vs-flylight \
    --id-field id  \
    --fields-toUpdate imageURL thumbnailURL \
    -od local/testData/cdsresults/flyem_hemibrain.gamma1_4
```

#### Step 2: Compute color depth matches

Even though this process is run only once it outputs two sets of result files:
one for the EM -> LM results and one for the LM -> EM results, 
indexed by the EM mip IDs or LM mip IDs respectively.

Calculate EM vs SplitGal4 results
```bash
java -Xmx180G -Xms180G \
    -jar target/colormipsearch-2.8.0-jar-with-dependencies.jar \
    searchFromJSON  \
    -m local/testData/mips/flyem_hemibrain.json:0:5000 \
    -i local/testData/mips/flylight_gen1_mcfo_published.json:0:40000 \
    --maskThreshold 20 \
    --dataThreshold 30 \
    --xyShift 2 \
    --pixColorFluctuation 1 \
    --pctPositivePixels 1 \
    --mirrorMask \
    --processingPartitionSize 4000 \
    --perMaskSubdir flyem_hemibrain-vs-gen1_mcfo \
    --perLibrarySubdir flylight_gen1_mcfo_published \
    -od local/testData/cdsresults
```

```bash
java -Xmx180G -Xms180G \
    -jar target/colormipsearch-2.8.0-jar-with-dependencies.jar \
    searchFromJSON  \
    -m local/testData/mips/flyem_hemibrain.json:0:35000 \
    -i local/testData/mips/flylight_split_gal4_published.json:0:7800 \
    --maskThreshold 20 \
    --dataThreshold 30 \
    --xyShift 2 \
    --pixColorFluctuation 1 \
    --pctPositivePixels 1 \
    --mirrorMask \
    --processingPartitionSize 4000 \
    --perMaskSubdir flyem_hemibrain-vs-split_gal4 \
    --perLibrarySubdir flylight_split_gal4_published \
    -od local/testData/cdsresults
```

#### Step 3: Calculate gradient score for the EM to LM color depth matches
```bash
java -Xmx240G -Xms240G \
    -Dlog4j.configuration=file:///groups/scicompsoft/home/goinac/Work/color-depth-spark/local/log4j.properties \
    -jar target/colormipsearch-2.8.0-jar-with-dependencies.jar \
    --cacheSize 5000 --cacheExpirationInSeconds 60 \
    gradientScore \
    --maskThreshold 20 \
    --negativeRadius 20 \
    --mirrorMask \
    --topPublishedNameMatches 500 \
    --topPublishedSampleMatches 0 \
    --processingPartitionSize 5 \
    -gp /nrs/jacs/jacsData/filestore/system/40x_MCFO_Segmented_PackBits_forPublicRelease_gradient.zip \
    -zgp /nrs/jacs/jacsData/filestore/system/40x_MCFO_Segmented_PackBits_forPublicRelease_20pxRGBMAX.zip --zgapSuffix _20pxRGBMAX \
    -rd local/testData/cdsresults/flyem_hemibrain-vs-gen1_mcfo:1400:20 \
    -od local/testData/cdsresults.ga/flyem_hemibrain-vs-gen1_mcfo
```

```bash
java -Xmx240G -Xms240G \
    -Dlog4j.configuration=file:///groups/scicompsoft/home/goinac/Work/color-depth-spark/local/log4j.properties \
    -jar target/colormipsearch-2.8.0-jar-with-dependencies.jar \
    --cacheSize 5000 --cacheExpirationInSeconds 60 \
    gradientScore \
    --maskThreshold 20 \
    --negativeRadius 20 \
    --mirrorMask \
    --topPublishedNameMatches 500 \
    --topPublishedSampleMatches 0 \
    --processingPartitionSize 5 \
    -gp /nrs/jacs/jacsData/filestore/system/SS_Split/SS_Split_ALL_Segmented_gradient \
    -zgp /nrs/jacs/jacsData/filestore/system/SS_Split/SS_Split_ALL_Segmented_20pxRGB --zgapSuffix _20pxRGB \
    -rd local/testData/cdsresults/flyem_hemibrain-vs-split_gal4:1400:100 \
    -od local/testData/cdsresults.ga/flyem_hemibrain-vs-split_gal4
```

In order to simplify the parallelization options for Steps 2 and 3
colormipsearch-tools module has a set of bash scripts that allow the user
to the color depth matches and the gradient scoring jobs into smaller jobs
and run them on a host with enough resources or on the grid.
The commands to run these steps are:
```bash
sh colormipsearch-tools/src/main/scripts/submitCDSBatch.sh
```
and 
```bash
sh colormipsearch-tools/src/main/scripts/submitGABatch.sh
```
respectively. 

Before running this commands make sure to set the appropriate parameters
in `colormipsearch-tools/src/main/scripts/cdsparams.sh` which is imported
in the above submit scripts.


#### Step 4: Update the gradient score for the LM to EM color depths matches

This step actually transfers the gradient score from the 
ones calculated for EM -> LM results to the corresponding LM -> EM results.

```bash
java -Xms480G -Xmx480G \
    -jar target/colormipsearch-2.8.0-jar-with-dependencies.jar \
    gradientScoresFromMatchedResults \
    -rd local/testData/cdsresults/flylight_gen1_mcfo_published \
    -revd local/testData/cdsresults.ga/flyem_hemibrain-vs-gen1_mcfo \
    -od local/testData/cdsresults.ga/flylight_gen1_mcfo_published \
    -ps 500
```

```bash
java -Xms480G -Xmx480G \
    -jar target/colormipsearch-2.8.0-jar-with-dependencies.jar \
    gradientScoresFromMatchedResults \
    -rd local/testData/cdsresults/flylight_split_gal4_published \
    -revd local/testData/cdsresults.ga/flyem_hemibrain-vs-split_gal4 \
    -od local/testData/cdsresults.ga/flylight_split_gal4_published \
    -ps 500
```

#### Step 5: Merge flyem results

When we search EM matches we want to see both Spligal4 matches and MCFO matches,
therefore the flyem-vs-mcfo and flyem-vs-sgal4 will have to be merged in a single 
directory.
```bash
java \
    -jar target/colormipsearch-2.8.0-jar-with-dependencies.jar \
    mergeResults \
    -rd \
    local/testData/cdsresults.ga/flyem_hemibrain-vs-gen1_mcfo \
    local/testData/cdsresults.ga/flyem_hemibrain-vs-split_gal4 \
    -od local/testData/cdsresults.merged/flyem_hemibrain-vs-flylight
```

#### Step 6: Normalize and rank results:
```bash
java \
    -jar target/colormipsearch-2.8.0-jar-with-dependencies.jar \
    normalizeScores \
    -rd local/testData/updateURLS/cdsresults/flyem_hemibrain-vs-flylight \
    --pctPositivePixels 2.0 \
    -cleanup \
    -od local/testData/cdsresults.normalized/flyem_hemibrain-vs-flylight
```

```bash
java \
    -jar target/colormipsearch-2.8.0-jar-with-dependencies.jar \
    normalizeScores \
    -rd local/testData/cdsresults.ga/flylight_split_gal4_published \
    --pctPositivePixels 2.0 \
    -cleanup \
    -od local/testData/cdsresults.normalized/flylight_split_gal4_published
```

```bash
java \
    -jar target/colormipsearch-2.8.0-jar-with-dependencies.jar \
    normalizeScores \
    -rd local/testData/cdsresults.ga/flylight_gen1_mcfo_published \
    --pctPositivePixels 2.0 \
    -cleanup \
    -od local/testData/cdsresults.normalized/flylight_gen1_mcfo_published
```

#### Step 7: Upload to AWS S3

This step requires aws-cli installed on the host from which we upload the
data to S3.

Upload MIPs

```bash
# generate publishedNames.txt
find . local/testData/mips \
    -type f \
    -name "*.json" \
    -printf "%f\n" | sed s/.json// | sort -u > publishedNames.txt
aws s3 cp publishedNames.txt s3://janelia-neuronbridge-data-prod/publishedNames.txt

# create and upload data version file
echo 1.0.0 > local/testData/DATA_VERSION

aws s3 cp \
    local/testData/DATA_VERSION \
    s3://janelia-neuronbridge-data-prod/metadata/cdsresults/DATA_VERSION

# upload MCFO lines
aws s3 cp \
    local/testData/mips.gamma1.4/gen1_mcfo_lines \
    s3://janelia-neuronbridge-data-prod/metadata/by_line --recursive
# upload SplitGal4 lines
aws s3 cp \
    local/testData/mips/split_gal4_lines \
    s3://janelia-neuronbridge-data-prod/metadata/by_line --recursive
# upload EM skeletons
aws s3 cp \
    local/testData/mips/em_bodies \
    s3://janelia-neuronbridge-data-prod/metadata/by_body --recursive
```

Upload Results

```bash
aws s3 cp \
    local/testData/cdsresults.normalized/flyem_hemibrain-vs-flylight \
    s3://janelia-neuronbridge-data-prod/metadata/cdsresults --recursive

aws s3 cp \
    local/testData/cdsresults.normalized/flylight_split_gal4_published \
    s3://janelia-neuronbridge-data-prod/metadata/cdsresults --recursive

aws s3 cp \
    local/testData/cdsresults.normalized/flylight_gen1_mcfo_published \
    s3://janelia-neuronbridge-data-prod/metadata/cdsresults --recursive
```

### Import PPP matches
```bash
PPP_IMPORT_DIR=/nrs/saalfeld/kainmuellerd/flymatch/all_hemibrain_1.2_NB/setup22_nblast_20/results/68
PPP_RESULTS_DIR=/nrs/neuronbridge/v2.2/pppresults/flyem-to-flylight

java \
    -jar target/colormipsearch-2.8.0-jar-with-dependencies.jar \
    convertPPPResults \
    -ps 30 \
     --jacs-read-batch-size 3000 \
    -rd ${PPP_IMPORT_DIR} \
    -od ${PPP_RESULTS_DIR}
```
