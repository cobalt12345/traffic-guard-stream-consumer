package den.tal.stream.watch.processors;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.kinesisvideo.parser.mkv.Frame;
import com.amazonaws.kinesisvideo.parser.mkv.FrameProcessException;
import com.amazonaws.kinesisvideo.parser.utilities.*;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import den.tal.stream.watch.exceptions.FilmWatcherInitException;
import lombok.extern.log4j.Log4j2;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Log4j2
public class FrameProcessor implements FrameVisitor.FrameProcessor {

    private int processNthFrame;
    private int frameCounter;
    private String bucketName;
    private String folder;
    private AmazonS3 s3;
    private H264FrameDecoder frameDecoder = new H264FrameDecoder();

    public FrameProcessor(int analyzeEachNFrame, String bucketName, String folder, Regions region, String profileName) throws FilmWatcherInitException {
        processNthFrame = analyzeEachNFrame;
        this.bucketName = bucketName;
        this.folder = folder + "/" + new SimpleDateFormat("ddMM").format(new Date());

        try {
            s3 = AmazonS3ClientBuilder.standard().withRegion(region)
                    .withCredentials(new ProfileCredentialsProvider(profileName)).build();

        } catch (Exception ex) {
            log.error("Processor can not be created!", ex);

            throw new FilmWatcherInitException(ex);
        }
    }

    @Override
    public void process(Frame frame, MkvTrackMetadata trackMetadata, Optional<FragmentMetadata> fragmentMetadata) throws FrameProcessException {
        log.debug("Process frame #{}", frameCounter);
        if (frameCounter % processNthFrame == 0) {
            log.debug("Save frame #{} to bucket {}", frameCounter, bucketName);
            try {
                BufferedImage image = frameDecoder.decodeH264Frame(frame, trackMetadata);
                var os = new ByteArrayOutputStream();
                ImageIO.write(image, "jpeg", os);
                byte[] imageBytes = os.toByteArray();
                var is = new ByteArrayInputStream(imageBytes);
                var objectMetadata = new ObjectMetadata();
                objectMetadata.setContentType("image/jpeg");
                objectMetadata.setContentLength(imageBytes.length);
                s3.putObject(bucketName, UUID.randomUUID() + ".jpg", is, objectMetadata);
            } catch (Exception ex) {
                log.error("Could not save frame to S3!", ex);
            }
        }
        ++frameCounter;
    }
}
