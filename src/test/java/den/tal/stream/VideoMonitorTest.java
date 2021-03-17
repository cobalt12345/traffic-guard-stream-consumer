package den.tal.stream;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.kinesisvideo.parser.ebml.InputStreamParserByteSource;
import com.amazonaws.kinesisvideo.parser.ebml.MkvTypeInfos;
import com.amazonaws.kinesisvideo.parser.examples.KinesisVideoFrameViewer;
import com.amazonaws.kinesisvideo.parser.mkv.*;
import com.amazonaws.kinesisvideo.parser.mkv.visitors.CompositeMkvElementVisitor;
import com.amazonaws.kinesisvideo.parser.utilities.*;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideo;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoClientBuilder;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoMedia;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoMediaClientBuilder;
import com.amazonaws.services.kinesisvideo.model.*;
import lombok.extern.slf4j.Slf4j;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.amazonaws.kinesisvideo.parser.utilities.BufferedImageUtil.addTextToImage;

@Slf4j
//@RunWith(SpringRunner.class)
//@SpringJUnitConfig(StreamConsumerConfig.class)
public class VideoMonitorTest {

    public static void main(String[] args) throws Exception {
        new VideoMonitorTest().testVideoStream();
    }

    private String profileName = "default";
    private String kinesisVideoStreamName = "traffic-guard";
    private ProfileCredentialsProvider credentials = new ProfileCredentialsProvider(profileName);
    private Regions region = Regions.EU_CENTRAL_1;
    private int width = 640;
    private int height = 480;
    private AmazonKinesisVideoMedia amazonKinesisVideoMedia;
    private StartSelector startSelector;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private CompositeMkvElementVisitor frameRendererVisitor;

//    @Test
    public void testVideoStream() throws IOException {
        final AmazonKinesisVideoClientBuilder videoClientBuilder = AmazonKinesisVideoClientBuilder.standard();
        videoClientBuilder.withCredentials(credentials).withRegion(region);
        AmazonKinesisVideo amazonKinesisVideo = videoClientBuilder.build();
        final String endpoint = amazonKinesisVideo.getDataEndpoint(new GetDataEndpointRequest()
                .withAPIName(APIName.GET_MEDIA).withStreamName(kinesisVideoStreamName)).getDataEndpoint();

        AmazonKinesisVideoMediaClientBuilder builder = AmazonKinesisVideoMediaClientBuilder.standard();
        builder.withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint, region.getName()))
                .withCredentials(credentials);

        amazonKinesisVideoMedia = builder.build();
        H264FrameRenderer frameRenderer = H264FrameRenderer.create(new KinesisVideoFrameViewer(width, height));
//        TollerantFrameRenderer frameRenderer = new TollerantFrameRenderer(new KinesisVideoFrameViewer(width, height));
        FragmentMetadataVisitor fragmentMetadataVisitor = FragmentMetadataVisitor.create();
        //A visitor used to log as the GetMedia stream is processed.
        LogVisitor logVisitor = new LogVisitor(fragmentMetadataVisitor);

        frameRendererVisitor = new CompositeMkvElementVisitor(logVisitor, FrameVisitor.create(frameRenderer));
        startSelector = new StartSelector().withStartSelectorType(StartSelectorType.NOW);

        executorService.submit(this::run);
    }

    private void run() {
        try {
            log.debug("Get media from stream {}.", kinesisVideoStreamName);
            GetMediaResult result =  amazonKinesisVideoMedia.getMedia(new GetMediaRequest().withStreamName(kinesisVideoStreamName)
                    .withStartSelector(startSelector));

            StreamingMkvReader streamingMkvReader = StreamingMkvReader.createDefault(
                    new InputStreamParserByteSource(result.getPayload()));

            streamingMkvReader.apply(frameRendererVisitor);
        } catch (MkvElementVisitException meve) {
            log.error("Visitor exception {} for stream {}.", meve, kinesisVideoStreamName);
        } catch (Throwable t) {
            log.error("Can't get media from stream {}.", kinesisVideoStreamName);
            log.error("", t);

            throw t;

        } finally {
            log.info("Finishing getting media from stream {}.", kinesisVideoStreamName);
        }
    }

    private static class LogVisitor extends MkvElementVisitor {
        private final FragmentMetadataVisitor fragmentMetadataVisitor;
        private long fragmentCount = 0;

        @Override
        public void visit(MkvStartMasterElement startMasterElement) throws MkvElementVisitException {
            if (MkvTypeInfos.EBML.equals(startMasterElement.getElementMetaData().getTypeInfo())) {
                fragmentCount++;
                log.info("Start of segment  {} ", fragmentCount);
            }
        }

        @Override
        public void visit(MkvEndMasterElement endMasterElement) throws MkvElementVisitException {
            if (MkvTypeInfos.SEGMENT.equals(endMasterElement.getElementMetaData().getTypeInfo())) {
                log.info("End of segment  {} fragment # {} millisBehindNow {} ", fragmentCount,
                        fragmentMetadataVisitor.getCurrentFragmentMetadata(), fragmentMetadataVisitor.getMillisBehindNow());
            }
        }

        @Override
        public void visit(MkvDataElement dataElement) throws MkvElementVisitException {
        }

        @SuppressWarnings("all")
        public LogVisitor(final FragmentMetadataVisitor fragmentMetadataVisitor) {
            this.fragmentMetadataVisitor = fragmentMetadataVisitor;
        }

        @SuppressWarnings("all")
        public long getFragmentCount() {
            return this.fragmentCount;
        }
    }
}

@Slf4j
class TollerantFrameRenderer extends H264FrameRenderer {
    private static final int PIXEL_TO_LEFT = 10;
    private static final int PIXEL_TO_TOP_LINE_1 = 20;
    private static final int PIXEL_TO_TOP_LINE_2 = 40;

    private final KinesisVideoFrameViewer kinesisVideoFrameViewer;

    TollerantFrameRenderer(final KinesisVideoFrameViewer kinesisVideoFrameViewer) {
        super(kinesisVideoFrameViewer);
        this.kinesisVideoFrameViewer = kinesisVideoFrameViewer;
    }

    @Override
    public void process(Frame frame, MkvTrackMetadata trackMetadata, Optional<FragmentMetadata> fragmentMetadata, Optional<FragmentMetadataVisitor.MkvTagProcessor> tagProcessor) throws FrameProcessException {
        try {
            final BufferedImage bufferedImage = decodeH264Frame(frame, trackMetadata);
            if (tagProcessor.isPresent()) {
                final FragmentMetadataVisitor.BasicMkvTagProcessor processor = (FragmentMetadataVisitor.BasicMkvTagProcessor) tagProcessor.get();
                if (fragmentMetadata.isPresent()) {
                    addTextToImage(bufferedImage, String.format("Fragment Number: %s", fragmentMetadata.get().getFragmentNumberString()), PIXEL_TO_LEFT, PIXEL_TO_TOP_LINE_1);
                }
                if (processor.getTags().size() > 0) {
                    addTextToImage(bufferedImage, "Fragment Metadata: " + processor.getTags().toString(), PIXEL_TO_LEFT, PIXEL_TO_TOP_LINE_2);
                } else {
                    addTextToImage(bufferedImage, "Fragment Metadata: No Metadata Available", PIXEL_TO_LEFT, PIXEL_TO_TOP_LINE_2);
                }
            }
            kinesisVideoFrameViewer.update(bufferedImage);
        } catch (Exception ex) {
            log.error("Cannot render", ex);
        }
    }
}