package den.tal.stream;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.EC2ContainerCredentialsProviderWrapper;
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
import org.springframework.context.annotation.Profile;


@Slf4j
@Configuration
@ComponentScan()
public class StreamConsumerConfig {

    @Autowired
    private AppConfig appConfig;

    @Profile("deploy_locally")
    @Bean
    public AWSCredentialsProvider stsCredentialsProvider() {
        RoleInfo roleInfo =
                new RoleInfo().withRoleArn(appConfig.getTrafficGuardRoleArn())
                        .withRoleSessionName("traffic-guard-session");

        return new STSProfileCredentialsServiceProvider(roleInfo);
    }

    @Profile("deploy_to_ecs")
    @Bean
    public AWSCredentialsProvider containerCredentialsProvider() {

        return new EC2ContainerCredentialsProviderWrapper();
    }

    @Bean
    public AmazonKinesisVideo kinesisVideo(AWSCredentialsProvider awsCredentialsProvider) {
        AmazonKinesisVideoClientBuilder videoClientBuilder = AmazonKinesisVideoClientBuilder.standard();
        AmazonKinesisVideo amazonKinesisVideo = videoClientBuilder.withRegion(appConfig.getRegion())
                .withCredentials(awsCredentialsProvider).build();

        return amazonKinesisVideo;
    }

    @Bean
    public AmazonKinesisVideoMedia kinesisVideoMedia(AWSCredentialsProvider awsCredentialsProvider,
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
                    .withCredentials(awsCredentialsProvider);

        return amazonKinesisVideoMediaClientBuilder.build();
    }
}
