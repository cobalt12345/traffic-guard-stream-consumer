package den.tal.stream;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AppConfig {

    @Getter
    @Value("${aws.role_arn}")
    private String trafficGuardRoleArn;

    @Getter
    @Value("${aws.region}")
    private String region;

    @Getter
    @Value("${kinesis.video.stream.name}")
    private String videoStreamName;

    /**
     * Use VPC endpoint to avoid traffic over internet.
     */
    @Getter
    @Value("${kinesis.service.endpoint:#{null}}")
    private String kinesisServiceEndpoint;

    /**
     * Use VPC endpoint to avoid traffic over internet.
     */
    @Getter
    @Value("${s3.service.endpoint:#{null}}")
    private String s3ServiceEndpoint;
}