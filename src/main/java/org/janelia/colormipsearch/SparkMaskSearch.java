package org.janelia.colormipsearch;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import ij.ImagePlus;
import ij.io.Opener;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.input.PortableDataStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Tuple2;

import java.io.DataInputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * Perform color depth mask search on a Spark cluster.
 *
 * @author <a href="mailto:rokickik@janelia.hhmi.org">Konrad Rokicki</a>
 */
public class SparkMaskSearch implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(SparkMaskSearch.class);

    private transient final int numPartitions;
    private transient final JavaSparkContext context;
    private transient JavaPairRDD<String, ImagePlus> imagePlusRDD;

    public SparkMaskSearch(int numPartitions) {
        this.numPartitions = numPartitions;
        SparkConf conf = new SparkConf().setAppName(SparkMaskSearch.class.getName());
        this.context = new JavaSparkContext(conf);
    }

    //    private static BufferedImage readTiff(PortableDataStream stream) throws IOException {
//        ByteArraySeekableStream seekStream = new ByteArraySeekableStream(stream.toArray());
//        TIFFDecodeParam decodeParam = new TIFFDecodeParam();
//        decodeParam.setDecodePaletteAsShorts(true);
//        ParameterBlock params = new ParameterBlock();
//        params.add(seekStream);
//        RenderedOp image1 = JAI.create("tiff", params);
//        return image1.getAsBufferedImage();
//    }

    private ImagePlus readTiffToImagePlus(PortableDataStream stream) throws Exception {

        // Attempt using BioFormats importer failed to give a 32 bit composite image:
//        IRandomAccess ira = new ByteArrayHandle(stream.toArray());
//        String id = UUID.randomUUID().toString();
//        Location.mapFile(id, ira);
//        ImporterOptions options = new ImporterOptions();
//        options.setId(id);
//        options.setAutoscale(false);
//        options.setColorMode(ImporterOptions.COLOR_MODE_COMPOSITE);
//        ImagePlus[] imps = BF.openImagePlus(options);
//        ImagePlus mask = imps[0];

        // But using ImageJ works:
        Opener opener = new Opener();
        try (DataInputStream dis = stream.open()) {
            return opener.openTiff(dis, "search");
        }
    }

    private MaskSearchResult search(String filepath, ImagePlus image, byte[] maskBytes) throws Exception {

        if (image==null) {
            log.error("Problem loading search image: {}", filepath);
            return new MaskSearchResult(filepath, 0, 0, false);
        }

        log.info("Searching "+filepath);
        ImagePlus mask = new Opener().deserialize(maskBytes);

        ColorMIPMaskCompare.Parameters params = new ColorMIPMaskCompare.Parameters();
        params.maskImage = mask;
        params.searchImage = image;
        ColorMIPMaskCompare search = new ColorMIPMaskCompare();
        ColorMIPMaskCompare.Output output = search.runSearch(params);

        return new MaskSearchResult(filepath, output.matchingSlices, output.matchingSlicesPct, output.isMatch);
    }

    /**
     * Load an image archive into memory.
     * @param imagesFilepath
     */
    public void loadImages(String imagesFilepath) {

        log.info("Loading image archive at: {}", imagesFilepath);

        JavaPairRDD<String, PortableDataStream> filesRdd = context.binaryFiles(imagesFilepath, numPartitions);
        log.info("binaryFiles.numPartitions: {}", filesRdd.getNumPartitions());

        this.imagePlusRDD = filesRdd.mapToPair(pair -> new Tuple2<>(pair._1, readTiffToImagePlus(pair._2))).cache();
        log.info("imagePlusRDD.numPartitions: {}", imagePlusRDD.getNumPartitions());
        log.info("imagePlusRDD.count: {}", imagePlusRDD.count());
    }

    /**
     * Perform the search.
     * @param maskFilepath
     * @return
     * @throws Exception
     */
    public Collection<MaskSearchResult> search(String maskFilepath) throws Exception {

        log.info("Searching with {}", maskFilepath);
        byte[] maskBytes = Files.readAllBytes(Paths.get(maskFilepath));

        JavaRDD<MaskSearchResult> resultRdd = imagePlusRDD.map(pair -> search(pair._1, pair._2, maskBytes));
        log.info("resultRdd.numPartitions: {}", resultRdd.getNumPartitions());

        JavaRDD<MaskSearchResult> sortedResultRdd = resultRdd.sortBy(result -> result.getMatchingSlices(), false, 1);
        log.info("sortedResultRdd.numPartitions: {}", sortedResultRdd.getNumPartitions());

        List<MaskSearchResult> results = sortedResultRdd.collect();
        log.info("Returning {} results", results.size());
        return results;
    }

    public void close() {
        if (context!=null) context.stop();
    }

    public static class Args {

        @Parameter(names = {"--mask", "-m"}, description = "TIFF file to use as the search mask", required = true)
        private String maskFile;

        @Parameter(names = {"--imageDir", "-i"}, description = "TIFF files to search", required = true)
        private String imageDir;

        @Parameter(names = {"--partitions", "-p"}, description = "Number of partitions to use")
        private Integer numPartitions;

        @Parameter(names = {"--outputFile", "-o"}, description = "Output file for results in CSV format. If this is not specified, the output will be printed to the log.")
        private String outputFile;
    }

    public static void main(String[] argv) throws Exception {

        Args args = new Args();
        JCommander.newBuilder()
                .addObject(args)
                .build()
                .parse(argv);

        int partitions = (args.numPartitions==null) ? 4 : args.numPartitions;
        SparkMaskSearch sparkMaskSearch = new SparkMaskSearch(partitions);

        try {
            sparkMaskSearch.loadImages(args.imageDir);
            Stream<MaskSearchResult> results = sparkMaskSearch.search(args.maskFile).stream().filter(r -> r.isMatch());

            if (args.outputFile != null) {
                log.info("Writing search results to "+args.outputFile);
                try (PrintWriter printWriter = new PrintWriter(args.outputFile)) {
                    results.forEach(r -> {
                        String filepath = r.getFilepath().replaceFirst("^file:","");
                        printWriter.printf("%#.5f\t%s\n", r.getMatchingSlicesPct(), filepath);
                    });
                }
            }
            else {
                log.info("Search results:");
                results.forEach(r -> {
                    log.info("{} - {}", r.getMatchingSlicesPct(), r.getFilepath());
                });
            }
        }
        finally {
            sparkMaskSearch.close();
        }
    }

}
