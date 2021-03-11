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
}