# Color Depth MIP Search Tools

[![DOI](https://zenodo.org/badge/98349943.svg)](https://zenodo.org/badge/latestdoi/98349943)
[![Java CI with Maven](https://github.com/JaneliaSciComp/colormipsearch/actions/workflows/maven.yml/badge.svg)](https://github.com/JaneliaSciComp/colormipsearch/actions/workflows/maven.yml)

This is a set of tools for precomputing color depth searches using the same algorithms as [ColorMIP_Mask_Search](https://github.com/JaneliaSciComp/ColorMIP_Mask_Search) Fiji plugin. 
The precomputed results are persisted in a Mongo database and can be exported to JSON in order to upload them to AWS for NeuronBridge. 
 
## Build

```bash
./mvnw install
```
or just
```bash
./mvnw package
```

### Build the docker container
```bash
docker buildx build -t ghcr.io/janeliascicomp/colormipsearch-tools:<VERSION> . --push
```


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
```bash
mvn --encrypt-master-password <password>
```
to create a master password and then use
```bash
mvn --encrypt-password <password>
```
which you can enter in place of your password. Check [maven documentation](https://maven.apache.org/guides/mini/guide-encryption.html)
how you can do this.

To release the artifacts simply run:

```./release.sh <version>```

This command will also tag the repository with the `<version>`.


## Run


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

### Generate EM - LM color depth search results

The steps to generate the precomputed color depth search results are the
following:

* Run `createColorDepthSearchDataInput` to import color depth MIPs from JACS into the Mongo database 
* Run `colorDepthSearch` to calculate color depth matches between a mask library and a target library 
* Run `gradientScores` to calculate gradient based score for the matches from a selected mask library 
* If matching scores still need to be normalized after calculating gradient scores, run the `normalizeGradientScores` command
* If Patch Per Pixel matches are available, those can be imported using `importPPPResults`
* Run `exportData` command to export the results in order to be uploaded to NeuronBridge

These steps are typically handled by a set a [Nextflow](https://www.nextflow.io/) workflows from [Precompute github repository](https://github.com/JaneliaSciComp/neuronbridge-precompute)
