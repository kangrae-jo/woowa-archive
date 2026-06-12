import java.io.IOException;

public interface TranscribeService {

    String upload(String bucketName, String filePath) throws IOException;

    void startTranscriptionJob(
            String jobName,
            String bucketName,
            String s3Key,
            String languageCode
    );

    void startTranscriptionJob(
            String jobName,
            String bucketName,
            String s3Key,
            String languageCode,
            Boolean showSpeakerLabels,
            Integer maxSpeakerLabels,
            Boolean channelIdentification,
            String vocabularyName,
            Boolean identifyLanguage,
            String[] languageOptions,
            Integer mediaSampleRateHertz
    );

    void waitForCompletion(String jobName) throws InterruptedException;

    String getTranscriptionResult(String jobName);

}
