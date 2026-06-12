package woowa;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.transcribe.TranscribeClient;
import software.amazon.awssdk.services.transcribe.model.GetTranscriptionJobRequest;
import software.amazon.awssdk.services.transcribe.model.LanguageCode;
import software.amazon.awssdk.services.transcribe.model.Media;
import software.amazon.awssdk.services.transcribe.model.MediaFormat;
import software.amazon.awssdk.services.transcribe.model.Settings;
import software.amazon.awssdk.services.transcribe.model.StartTranscriptionJobRequest;
import software.amazon.awssdk.services.transcribe.model.TranscriptionJob;
import software.amazon.awssdk.services.transcribe.model.TranscriptionJobStatus;

@Service
public class S3TranscribeServiceV1 implements TranscribeService, AutoCloseable {

    private final TranscribeClient transcribeClient;
    private final S3Client s3Client;
    private final TranscriptionResultReader resultReader;

    public S3TranscribeServiceV1(
            @Value("${aws.region:ap-northeast-2}") String regionName,
            TranscriptionResultReader resultReader
    ) {
        Region region = Region.of(regionName);
        DefaultCredentialsProvider credentialsProvider = DefaultCredentialsProvider.builder().build();
        this.transcribeClient = TranscribeClient.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
        this.s3Client = S3Client.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
        this.resultReader = resultReader;
    }

    @Override
    public String upload(String bucketName, String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IOException("파일을 찾을 수 없습니다: " + filePath);
        }

        String key = "transcribe/" + file.getName() + "/" + LocalDateTime.now();
        Extension extension = getExtensionType(file.getName());
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(extension.getContentType())
                .build();

        byte[] fileBytes = Files.readAllBytes(Paths.get(filePath));
        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(fileBytes));

        return key;
    }

    @Override
    public void startTranscriptionJob(
            String jobName,
            String bucketName,
            String s3Key,
            String languageCode
    ) {
        startTranscriptionJob(jobName, bucketName, s3Key, languageCode, null, null, null, null, null, null, null);
    }

    @Override
    public void startTranscriptionJob(
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
    ) {
        Media media = Media.builder()
                .mediaFileUri("s3://" + bucketName + "/" + s3Key)
                .build();

        MediaFormat mediaFormat = getExtensionType(s3Key)
                .getMediaFormat();

        Settings settings = buildSettings(
                showSpeakerLabels,
                maxSpeakerLabels,
                channelIdentification,
                vocabularyName
        );

        StartTranscriptionJobRequest startTranscriptionJobRequest = buildStartTranscriptionJobRequest(
                jobName,
                media,
                mediaFormat,
                settings,
                languageCode,
                identifyLanguage,
                languageOptions,
                mediaSampleRateHertz
        );

        transcribeClient.startTranscriptionJob(startTranscriptionJobRequest);
    }

    private Settings buildSettings(
            Boolean showSpeakerLabels,
            Integer maxSpeakerLabels,
            Boolean channelIdentification,
            String vocabularyName
    ) {
        Settings.Builder settingsBuilder = Settings.builder();

        if (maxSpeakerLabels != null && !Boolean.TRUE.equals(showSpeakerLabels)) {
            throw new IllegalArgumentException("maxSpeakerLabels는 showSpeakerLabels가 true일 때만 설정할 수 있습니다.");
        }

        if (Boolean.TRUE.equals(showSpeakerLabels)) {
            settingsBuilder.showSpeakerLabels(true);
            if (maxSpeakerLabels == null) {
                settingsBuilder.maxSpeakerLabels(2);
            } else if (maxSpeakerLabels < 2 || maxSpeakerLabels > 10) {
                throw new IllegalArgumentException("maxSpeakerLabels는 2 이상 10 이하만 가능합니다.");
            } else {
                settingsBuilder.maxSpeakerLabels(maxSpeakerLabels);
            }
        }

        if (channelIdentification != null) {
            settingsBuilder.channelIdentification(channelIdentification);
        }

        if (vocabularyName != null) {
            settingsBuilder.vocabularyName(vocabularyName);
        }

        return settingsBuilder.build();
    }

    private StartTranscriptionJobRequest buildStartTranscriptionJobRequest(
            String jobName,
            Media media,
            MediaFormat mediaFormat,
            Settings settings,
            String languageCode,
            Boolean identifyLanguage,
            String[] languageOptions,
            Integer mediaSampleRateHertz
    ) {
        StartTranscriptionJobRequest.Builder requestBuilder = StartTranscriptionJobRequest.builder()
                .transcriptionJobName(jobName)
                .media(media)
                .mediaFormat(mediaFormat)
                .settings(settings);

        // 샘플링 레이트 설정 - 제공된 경우에만 설정, 그렇지 않으면 AWS가 자동 감지
        if (mediaSampleRateHertz != null) {
            requestBuilder.mediaSampleRateHertz(mediaSampleRateHertz);
        }

        // 언어 설정
        if (identifyLanguage != null && identifyLanguage) {
            requestBuilder.identifyLanguage(true);
            if (languageOptions != null && languageOptions.length > 0) {
                // String 배열을 LanguageCode 배열로 변환
                LanguageCode[] languageCodes = Arrays.stream(languageOptions)
                        .map(LanguageCode::fromValue)
                        .toArray(LanguageCode[]::new);
                requestBuilder.languageOptions(languageCodes);
            }
        } else {
            requestBuilder.languageCode(languageCode);
        }

        return requestBuilder.build();
    }

    @Override
    public void waitForCompletion(String jobName) throws InterruptedException {
        while (true) {
            GetTranscriptionJobRequest request = GetTranscriptionJobRequest.builder()
                    .transcriptionJobName(jobName)
                    .build();

            TranscriptionJob job = transcribeClient.getTranscriptionJob(request).transcriptionJob();
            TranscriptionJobStatus status = job.transcriptionJobStatus();

            System.out.println("현재 상태: " + status);
            if (status == TranscriptionJobStatus.COMPLETED) {
                break;
            }
            if (status == TranscriptionJobStatus.FAILED) {
                throw new RuntimeException("전사 작업이 실패했습니다: " + job.failureReason());
            }
            TimeUnit.SECONDS.sleep(10); // 10초마다 상태 확인
        }
    }

    @Override
    public String getTranscriptionResult(String jobName) {
        return resultReader.readTranscriptionResult(jobName);
    }

    @Override
    public void close() {
        transcribeClient.close();
        s3Client.close();
    }

    private Extension getExtensionType(String name) {
        String extensionType = name.substring(name.lastIndexOf('.') + 1).toUpperCase();
        return Extension.fromValue(extensionType);
    }

}
