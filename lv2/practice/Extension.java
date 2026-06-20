import java.util.Map;
import software.amazon.awssdk.services.transcribe.model.MediaFormat;
import software.amazon.awssdk.utils.internal.EnumUtils;

public enum Extension {

    MP3("audio/mpeg", MediaFormat.MP3),
    WAV("audio/wav", MediaFormat.WAV),
    FLAC("audio/flac", MediaFormat.FLAC),
    OGG("audio/ogg", MediaFormat.OGG),
    M4A("audio/mp4", MediaFormat.MP4),
    AIFF("audio/aiff", MediaFormat.WAV),
    AIF("audio/aiff", MediaFormat.WAV);

    private static final Map<String, Extension> VALUE_MAP = EnumUtils.uniqueIndex(Extension.class, Extension::toString);

    public final String contentType;
    public final MediaFormat mediaFormat;

    Extension(String contentType, MediaFormat mediaFormat) {
        this.contentType = contentType;
        this.mediaFormat = mediaFormat;
    }

    public static Extension fromValue(String value) {
        if (value == null) {
            return null;
        }
        return VALUE_MAP.getOrDefault(value, Extension.MP3);
    }

    public String getContentType() {
        return contentType;
    }

    public MediaFormat getMediaFormat() {
        return mediaFormat;
    }

}
