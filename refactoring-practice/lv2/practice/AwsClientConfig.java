import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.transcribe.TranscribeClient;

@Configuration
public class AwsClientConfig {

    @Bean
    public Region awsRegion(
            @Value("${aws.region:ap-northeast-2}")
            String regionName
    ) {
        return Region.of(regionName);
    }

    @Bean
    public DefaultCredentialsProvider awsCredentialsProvider() {
        return DefaultCredentialsProvider.builder().build();
    }

    @Bean
    public S3Client s3Client(
            Region awsRegion,
            DefaultCredentialsProvider credentialsProvider
    ) {
        return S3Client.builder()
                .region(awsRegion)
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean
    public TranscribeClient transcribeClient(
            Region awsRegion,
            DefaultCredentialsProvider credentialsProvider
    ) {
        return TranscribeClient.builder()
                .region(awsRegion)
                .credentialsProvider(credentialsProvider)
                .build();
    }

}
