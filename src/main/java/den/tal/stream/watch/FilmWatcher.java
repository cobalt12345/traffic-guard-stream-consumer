package den.tal.stream.watch;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.kinesisvideo.parser.ebml.InputStreamParserByteSource;
import com.amazonaws.kinesisvideo.parser.examples.KinesisVideoFrameViewer;
import com.amazonaws.kinesisvideo.parser.mkv.MkvElementVisitException;
import com.amazonaws.kinesisvideo.parser.mkv.StreamingMkvReader;
import com.amazonaws.kinesisvideo.parser.mkv.visitors.CompositeMkvElementVisitor;
import com.amazonaws.kinesisvideo.parser.utilities.FrameVisitor;
import com.amazonaws.kinesisvideo.parser.utilities.H264FrameRenderer;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoMedia;
import com.amazonaws.services.kinesisvideo.model.*;
import den.tal.stream.watch.exceptions.FilmWatcherInitException;
import den.tal.stream.watch.processors.FilmFrameProcessor;
import den.tal.stream.watch.visitors.LogFrameProcessor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

@Log4j2
@Component
public class FilmWatcher {

    @Value("${kinesis.video.stream.name}")
    private String videoStreamName;

    @Value("${aws.region}")
    private String region;

    @Value("${film.watcher.save_nth_frame}")
    private int watchAnyNthFrame;

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

    @PostConstruct
    private void initWatcher() throws FilmWatcherInitException {
        lock.lock();
        FilmFrameProcessor frameToS3Persister = new FilmFrameProcessor(watchAnyNthFrame, bucketName, folder,
                Regions.fromName(region), credentialsProvider, lock);

        FrameVisitor frameVisitor = FrameVisitor.create(frameToS3Persister);
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
        try {
            log.debug("Get media from stream {}.", videoStreamName);
            GetMediaResult result =  amazonKinesisVideoMedia.getMedia(new GetMediaRequest()
                    .withStreamName(videoStreamName).withStartSelector(startSelector));

            StreamingMkvReader streamingMkvReader = StreamingMkvReader.createDefault(
                    new InputStreamParserByteSource(result.getPayload()));

            streamingMkvReader.apply(compositeVisitor);
        } catch (MkvElementVisitException meve) {
            log.error("Visitor exception {} for stream {}.", meve, videoStreamName);
        } catch (Throwable t) {
            log.error("Can't get media from stream {}.", videoStreamName);
            log.error("", t);

            throw t;

        } finally {
            log.info("Finishing getting media from stream {}.", videoStreamName);
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
