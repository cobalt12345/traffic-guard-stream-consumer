package den.tal.stream.watch;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.kinesisvideo.parser.utilities.FrameVisitor;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoClientBuilder;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoMedia;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoMediaClientBuilder;
import com.amazonaws.services.kinesisvideo.model.APIName;
import com.amazonaws.services.kinesisvideo.model.GetDataEndpointRequest;
import den.tal.stream.watch.exceptions.FilmWatcherInitException;
import den.tal.stream.watch.processors.FrameProcessor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Log4j2
@Component
public class FilmWatcher {

    @Value("${aws.user.profile}")
    private String profileName;

    @Value("${kinesis.video.stream.name}")
    private String videoStreamName;

    @Value("${aws.region}")
    private Regions region;

    @Value("${film.watcher.save_nth_frame}")
    private int watchAnyNthFrame;

    @Value("${film.watcher.bucket_name}")
    private String bucketName;

    private AmazonKinesisVideoMedia amazonKinesisVideoMedia;

    @PostConstruct
    private void initWatcher() throws FilmWatcherInitException {
        var credentials = new ProfileCredentialsProvider(profileName);
        var videoClientBuilder = AmazonKinesisVideoClientBuilder.standard();
        videoClientBuilder.withCredentials(credentials).withRegion(region);
        var amazonKinesisVideo = videoClientBuilder.build();
        final String endpoint = amazonKinesisVideo.getDataEndpoint(new GetDataEndpointRequest()
                .withAPIName(APIName.GET_MEDIA).withStreamName(videoStreamName)).getDataEndpoint();

        log.debug("Kinesis Video Endpoint: {}", endpoint);
        if (null == endpoint) {

            throw new FilmWatcherInitException("Kinesis Video Endpoint is required!");
        }

        var amazonKinesisVideoMediaClientBuilder = AmazonKinesisVideoMediaClientBuilder.standard();
        amazonKinesisVideoMediaClientBuilder.withEndpointConfiguration(
                new AwsClientBuilder.EndpointConfiguration(endpoint, region.getName())).withCredentials(credentials);

        amazonKinesisVideoMedia = amazonKinesisVideoMediaClientBuilder.build();
//        FrameProcessor frameToS3Persister = new FrameProcessor(watchAnyNthFrame, )
//        FrameVisitor frameVisitor = FrameVisitor.create();
    }


}
