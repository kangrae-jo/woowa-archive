package woowa;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.transcribe.TranscribeClient;
import software.amazon.awssdk.services.transcribe.model.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class TranscribeServiceV0 {
    private final TranscribeClient transcribeClient;
    private final S3Client s3Client;
    private final TranscriptionResultReader resultReader;
    private final Region region;

    public TranscribeServiceV0(@Value("${aws.region:ap-northeast-2}") String regionName,
                               TranscriptionResultReader resultReader) {
        this.region = Region.of(regionName);
        this.transcribeClient = TranscribeClient.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
        this.s3Client = S3Client.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
        this.resultReader = resultReader;
    }

    public String uploadToS3(String bucketName, String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IOException("파일을 찾을 수 없습니다: " + filePath);
        }

        String key = "transcribe/" + file.getName();
        byte[] fileBytes = Files.readAllBytes(Paths.get(filePath));

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(getContentType(file.getName()))
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(fileBytes));

        return key;
    }

    public void startTranscriptionJob(String jobName, String bucketName, String s3Key, String languageCode) {
        startTranscriptionJob(jobName, bucketName, s3Key, languageCode, null, null, null, null, null, null, null);
    }

    public void startTranscriptionJob(String jobName, String bucketName, String s3Key, String languageCode,
                                      Boolean showSpeakerLabels, Integer maxSpeakerLabels,
                                      Boolean channelIdentification,
                                      String vocabularyName, Boolean identifyLanguage, String[] languageOptions,
                                      Integer mediaSampleRateHertz) {
        Media media = Media.builder()
                .mediaFileUri("s3://" + bucketName + "/" + s3Key)
                .build();

        // 파일 확장자에 따라 MediaFormat 결정
        String extension = s3Key.substring(s3Key.lastIndexOf('.') + 1).toLowerCase();
        MediaFormat mediaFormat = switch (extension) {
            case "mp3" -> MediaFormat.MP3;
            case "wav" -> MediaFormat.WAV;
            case "flac" -> MediaFormat.FLAC;
            case "ogg" -> MediaFormat.OGG;
            case "m4a" -> MediaFormat.MP4;
            case "aiff", "aif" -> MediaFormat.WAV; // AIFF는 WAV로 처리
            default -> MediaFormat.MP3;
        };

        StartTranscriptionJobRequest.Builder requestBuilder = StartTranscriptionJobRequest.builder()
                .transcriptionJobName(jobName)
                .media(media)
                .mediaFormat(mediaFormat);

        // 샘플링 레이트 설정 - 제공된 경우에만 설정, 그렇지 않으면 AWS가 자동 감지
        if (mediaSampleRateHertz != null) {
            requestBuilder.mediaSampleRateHertz(mediaSampleRateHertz);
        }

        // 언어 설정
        if (identifyLanguage != null && identifyLanguage) {
            requestBuilder.identifyLanguage(identifyLanguage);
            if (languageOptions != null && languageOptions.length > 0) {
                // String 배열을 LanguageCode 배열로 변환
                LanguageCode[] languageCodes = java.util.Arrays.stream(languageOptions)
                        .map(LanguageCode::fromValue)
                        .toArray(LanguageCode[]::new);
                requestBuilder.languageOptions(languageCodes);
            }
        } else {
            requestBuilder.languageCode(languageCode);
        }

        // Settings 구성
        if (showSpeakerLabels != null || maxSpeakerLabels != null || channelIdentification != null
                || vocabularyName != null) {
            Settings.Builder settingsBuilder = Settings.builder();

            if (showSpeakerLabels != null && showSpeakerLabels) {
                settingsBuilder.showSpeakerLabels(true);
                // 화자 분류가 활성화되면 최대 화자 수가 반드시 필요
                if (maxSpeakerLabels != null && maxSpeakerLabels >= 2 && maxSpeakerLabels <= 10) {
                    settingsBuilder.maxSpeakerLabels(maxSpeakerLabels);
                } else {
                    // 기본값으로 2 설정
                    settingsBuilder.maxSpeakerLabels(2);
                }
            }
            if (channelIdentification != null) {
                settingsBuilder.channelIdentification(channelIdentification);
            }
            if (vocabularyName != null) {
                settingsBuilder.vocabularyName(vocabularyName);
            }

            requestBuilder.settings(settingsBuilder.build());
        }

        transcribeClient.startTranscriptionJob(requestBuilder.build());
    }

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
            } else if (status == TranscriptionJobStatus.FAILED) {
                throw new RuntimeException("전사 작업이 실패했습니다: " + job.failureReason());
            }

            TimeUnit.SECONDS.sleep(10); // 10초마다 상태 확인
        }
    }

    public String getTranscriptionResult(String jobName) {
        return resultReader.readTranscriptionResult(jobName);
    }

    private String getContentType(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        return switch (extension) {
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "flac" -> "audio/flac";
            case "ogg" -> "audio/ogg";
            case "m4a" -> "audio/mp4";
            case "aiff", "aif" -> "audio/aiff";
            default -> "audio/mpeg";
        };
    }

    public void close() {
        transcribeClient.close();
        s3Client.close();
    }
}
