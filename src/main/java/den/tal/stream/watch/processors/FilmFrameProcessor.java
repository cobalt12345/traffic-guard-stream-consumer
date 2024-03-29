package den.tal.stream.watch.processors;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.kinesisvideo.parser.mkv.Frame;
import com.amazonaws.kinesisvideo.parser.mkv.FrameProcessException;
import com.amazonaws.kinesisvideo.parser.utilities.*;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import den.tal.stream.watch.exceptions.FilmWatcherInitException;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class FilmFrameProcessor implements FrameVisitor.FrameProcessor {

    private int processNthFrame;
    private int frameCounter;
    private String bucketName;
    private String folder;
    private AmazonS3 s3;
    private H264FrameDecoder frameDecoder = new H264FrameDecoder();
    private ReentrantLock lock;

    public FilmFrameProcessor(int analyzeEachNFrame, String bucketName, String folder, Regions region,
                              AWSCredentialsProvider credentialsProvider, ReentrantLock lock,
                              AwsClientBuilder.EndpointConfiguration s3EndpointConfiguration)
                                    throws FilmWatcherInitException {

        processNthFrame = analyzeEachNFrame;
        this.bucketName = bucketName;
        this.folder = "source-images-" + new SimpleDateFormat("ddMM").format(new Date());
        this.lock = lock;

        try {
            var s3ClientBuilder = AmazonS3ClientBuilder.standard();

            if (null != s3EndpointConfiguration) {
                log.info("Use pre-defined S3 endpoint: {}", s3EndpointConfiguration.getServiceEndpoint());
                s3ClientBuilder = s3ClientBuilder.withEndpointConfiguration(s3EndpointConfiguration);
            } else {
                log.info("Use default S3 endpoint.");
                s3ClientBuilder = s3ClientBuilder.withRegion(region).withCredentials(credentialsProvider);
            }

            s3 = s3ClientBuilder.build();

        } catch (Exception ex) {
            log.error("Processor can not be created!", ex);

            throw new FilmWatcherInitException(ex);
        }
    }

    @Override
    public void process(Frame frame, MkvTrackMetadata trackMetadata, Optional<FragmentMetadata> fragmentMetadata,
                        Optional<FragmentMetadataVisitor.MkvTagProcessor> tagProcessor,
                        Optional<BigInteger> timescale, Optional<BigInteger> fragmentTimecode)
            throws FrameProcessException {

        lock.lock();
        try {
            log.debug("Process frame #{}", frameCounter);
            if (frameCounter % processNthFrame == 0) {
                log.debug("Save frame #{} to bucket {}", frameCounter, bucketName);
                try (var os = new ByteArrayOutputStream()) {
                    BufferedImage image = frameDecoder.decodeH264Frame(frame, trackMetadata);
                    Optional<MkvTag> locationTag = Optional.empty();
                    if (tagProcessor.isPresent()) {
                        final FragmentMetadataVisitor.BasicMkvTagProcessor processor =
                            (FragmentMetadataVisitor.BasicMkvTagProcessor) tagProcessor.get();

                        locationTag = processor.getTags().stream().filter(
                                mkvTag -> "LOCATION".equals(mkvTag.getTagName())).findFirst();

                        log.debug("Location tag: {}", locationTag);
                    } else {
                        log.debug("No MkvTag processor present.");
                    }
                    ImageIO.write(image, "jpeg", os);
                    byte[] imageBytes = os.toByteArray();
                    var is = new ByteArrayInputStream(imageBytes);
                    var objectMetadata = new ObjectMetadata();
                    objectMetadata.setContentType("image/jpeg");
                    objectMetadata.setContentLength(imageBytes.length);
                    if (locationTag.isPresent()) {
                        objectMetadata.addUserMetadata("location", locationTag.get().getTagValue());
                    }
                    s3.putObject(bucketName, folder + "/" + UUID.randomUUID() + ".jpg", is, objectMetadata);
                } catch (Exception ex) {
                    final var msg = "Could not save frame to S3!";
                    log.error(msg, ex);

                    throw new FrameProcessException(msg, ex);
                }
            }

            ++frameCounter;
        } finally {
            lock.unlock();
        }
    }
}
