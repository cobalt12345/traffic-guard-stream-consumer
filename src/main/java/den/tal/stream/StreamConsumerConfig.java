package den.tal.stream;

import com.amazonaws.auth.profile.internal.securitytoken.RoleInfo;
import com.amazonaws.auth.profile.internal.securitytoken.STSProfileCredentialsServiceProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideo;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoClientBuilder;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoMedia;
import com.amazonaws.services.kinesisvideo.AmazonKinesisVideoMediaClientBuilder;
import com.amazonaws.services.kinesisvideo.model.APIName;
import com.amazonaws.services.kinesisvideo.model.GetDataEndpointRequest;
import den.tal.stream.watch.exceptions.FilmWatcherInitException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;


@Slf4j
@Configuration
@ComponentScan()
public class StreamConsumerConfig {

    @Autowired
    private AppConfig appConfig;

    @Bean
    public STSProfileCredentialsServiceProvider stsCredentialsProvider() {
        RoleInfo roleInfo =
                new RoleInfo().withRoleArn(appConfig.getTrafficGuardRoleArn())
                        .withRoleSessionName("traffic-guard-session");

        return new STSProfileCredentialsServiceProvider(roleInfo);
    }

    @Bean
    public AmazonKinesisVideo kinesisVideo(STSProfileCredentialsServiceProvider stsCredentialsProvider) {
        AmazonKinesisVideoClientBuilder videoClientBuilder = AmazonKinesisVideoClientBuilder.standard();
        AmazonKinesisVideo amazonKinesisVideo = videoClientBuilder.withRegion(appConfig.getRegion())
                .withCredentials(stsCredentialsProvider).build();

        return amazonKinesisVideo;
    }

    @Bean
    public AmazonKinesisVideoMedia kinesisVideoMedia(STSProfileCredentialsServiceProvider stsCredentialsProvider,
                                                     AmazonKinesisVideo kinesisVideo) throws FilmWatcherInitException {

        String endpoint = kinesisVideo.getDataEndpoint(new GetDataEndpointRequest()
                .withAPIName(APIName.GET_MEDIA).withStreamName(appConfig.getVideoStreamName()))
                    .getDataEndpoint();

        log.debug("Kinesis Video Endpoint: {}", endpoint);
        if (null == endpoint) {

            throw new FilmWatcherInitException("Kinesis Video Endpoint is required!");
        }

        var amazonKinesisVideoMediaClientBuilder =
                AmazonKinesisVideoMediaClientBuilder.standard();

        amazonKinesisVideoMediaClientBuilder.withEndpointConfiguration(
                new AwsClientBuilder.EndpointConfiguration(endpoint, appConfig.getRegion()))
                    .withCredentials(stsCredentialsProvider);

        return amazonKinesisVideoMediaClientBuilder.build();
    }
}
