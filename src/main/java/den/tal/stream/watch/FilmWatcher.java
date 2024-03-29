package den.tal.stream.watch;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.kinesisvideo.parser.ebml.InputStreamParserByteSource;
import com.amazonaws.kinesisvideo.parser.examples.KinesisVideoFrameViewer;
import com.amazonaws.kinesisvideo.parser.mkv.MkvElementVisitException;
import com.amazonaws.kinesisvideo.parser.mkv.StreamingMkvReader;
import com.amazonaws.kinesisvideo.parser.mkv.visitors.CompositeMkvElementVisitor;
import com.amazonaws.kinesisvideo.parser.utilities.FragmentMetadataVisitor;
import com.amazonaws.kinesisvideo.parser.utilities.FrameVisitor;
import com.amazonaws.kinesisvideo.parser.utilities.H264FrameRenderer;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoMedia;
import com.amazonaws.services.kinesisvideo.model.GetMediaRequest;
import com.amazonaws.services.kinesisvideo.model.GetMediaResult;
import com.amazonaws.services.kinesisvideo.model.StartSelector;
import com.amazonaws.services.kinesisvideo.model.StartSelectorType;
import den.tal.stream.watch.exceptions.FilmWatcherInitException;
import den.tal.stream.watch.processors.FilmFrameProcessor;
import den.tal.stream.watch.visitors.LogFrameProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
public class FilmWatcher {

    @Value("${kinesis.video.stream.name}")
    private String videoStreamName;

    @Value("${aws.region}")
    private String region;

    @Value("${film.watcher.save_nth_frame}")
    private int watchAnyNthFrame;

    @Value("${film.watcher.wait.retry.read}")
    private int waitAndRetryInSeconds;

    @Value("${film.watcher.bucket_name}")
    private String bucketName;

    @Value("${film.watcher.folder}")
    private String folder;

    @Value("${film.watcher.monitor.render}")
    private boolean renderStream;

    @Value("${film.watcher.monitor.width}")
    private int monitorWidth;

    @Value("${film.watcher.monitor.height}")
    private int monitorHeight;

    @Autowired
    private AWSCredentialsProvider credentialsProvider;

    final private ExecutorService executorService = Executors.newSingleThreadExecutor();

    private CompositeMkvElementVisitor compositeVisitor;

    @Autowired
    private AmazonKinesisVideoMedia amazonKinesisVideoMedia;

    private StartSelector startSelector;

    final private ReentrantLock lock = new ReentrantLock();

    @Qualifier("s3EndpointConfiguration")
    @Autowired(required = false)
    private AwsClientBuilder.EndpointConfiguration s3EndpointConfiguration;


    @PostConstruct
    private void initWatcher() throws FilmWatcherInitException {
        lock.lock();
        FilmFrameProcessor frameToS3Persister = new FilmFrameProcessor(watchAnyNthFrame, bucketName, folder,
                Regions.fromName(region), credentialsProvider, lock, s3EndpointConfiguration);

        FrameVisitor frameVisitor = FrameVisitor.create(frameToS3Persister, Optional.of(
                new FragmentMetadataVisitor.BasicMkvTagProcessor()));

        var logFrame = new LogFrameProcessor();
        if (renderStream) {
            log.debug("Render stream...");
            final var frameRenderer = H264FrameRenderer.create(new KinesisVideoFrameViewer(monitorWidth,
                    monitorHeight));

            compositeVisitor = new CompositeMkvElementVisitor(FrameVisitor.create(logFrame), frameVisitor,
                    FrameVisitor.create(frameRenderer));

        } else {
            compositeVisitor = new CompositeMkvElementVisitor(FrameVisitor.create(logFrame), frameVisitor);
        }

        startSelector = new StartSelector().withStartSelectorType(StartSelectorType.NOW);
        executorService.submit(this::run);
    }

    @PreDestroy
    private void destroyWatcher() {
        endWatchFilm();
        executorService.shutdown();
    }

    private void run() {
        int status = 0;
        try {
            final long millis = TimeUnit.SECONDS.toMillis(waitAndRetryInSeconds);
            while (true) {
                log.debug("Get media from stream {}.", videoStreamName);
                GetMediaResult result = amazonKinesisVideoMedia.getMedia(new GetMediaRequest()
                        .withStreamName(videoStreamName).withStartSelector(startSelector));

                StreamingMkvReader streamingMkvReader = StreamingMkvReader.createDefault(
                        new InputStreamParserByteSource(result.getPayload()));

                if (streamingMkvReader.mightHaveNext()) {
                    streamingMkvReader.apply(compositeVisitor);
                } else {
                    try {
                        log.debug("Nothing to read from stream {}. Sleep for {} seconds.", videoStreamName,
                                waitAndRetryInSeconds);

                        Thread.currentThread().sleep(millis);
                    } catch (InterruptedException iex) {
                        log.warn("", iex);
                    }
                }
            }
        } catch (MkvElementVisitException meve) {
            status = 1;
            log.error("Visitor exception {} for stream {}.", meve, videoStreamName);
        } catch (RuntimeException ret) {
            log.error("Can't get media from stream {}.", videoStreamName);
            log.error("", ret);

            throw ret;

        } finally {
            log.info("Finishing getting media from stream {}.", videoStreamName);
            System.exit(status);
        }
    }

    public void beginWatchFilm() {
        log.trace("Begin watching film...");
        lock.unlock();
    }

    public void endWatchFilm() {
        log.trace("End watching film...");
        lock.lock();
    }
}
