package My_DAC_Project.My_DAC_Project.Service;

import com.sun.speech.freetts.Voice;
import com.sun.speech.freetts.VoiceManager;
import com.sun.speech.freetts.audio.AudioPlayer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class DAC_Service {

    private static final Logger logger = LoggerFactory.getLogger(DAC_Service.class);

    private static final String VOICE_REGISTRY = "com.sun.speech.freetts.en.us.cmu_us_kal.KevinVoiceDirectory";
    private static final String DEFAULT_VOICE = "kevin16";

    // just defualt values, gets overwritten later with the real ones from freetts
    private static final int SAMPLE_RATE = 16000;
    private static final int BIT_DEPTH = 16;
    private static final int CHANNELS = 1;

    @Value("${audio.output.dir:generated-audio}")
    private String outputDir;

    private AudioFormat currentAudioFormat = new AudioFormat(SAMPLE_RATE, BIT_DEPTH, CHANNELS, true, false);

    @PostConstruct
    public void init() {
        // need this so freetts knows where the voices are at
        System.setProperty("freetts.voices", VOICE_REGISTRY);

        Voice[] voices = VoiceManager.getInstance().getVoices();
        logger.info("found {} voice(s) on startup", voices.length);
        for (Voice v : voices) {
            logger.info(" - {}", v.getName());
        }
    }

    public String[] getAvailableVoices() {
        Voice[] voices = VoiceManager.getInstance().getVoices();
        String[] names = new String[voices.length];
        for (int i = 0; i < voices.length; i++) {
            names[i] = voices[i].getName();
        }
        return names;
    }

    public byte[] generateAudioBytes(String text) throws Exception {
        return generateAudioBytes(text, DEFAULT_VOICE);
    }

    public synchronized byte[] generateAudioBytes(String text, String voiceName) throws Exception {
        System.setProperty("freetts.voices", VOICE_REGISTRY);

        VoiceManager voiceManager = VoiceManager.getInstance();
        String chosen = (voiceName == null || voiceName.isBlank()) ? DEFAULT_VOICE : voiceName;

        Voice voice = voiceManager.getVoice(chosen);
        if (voice == null) {
            throw new RuntimeException("Voice '" + chosen + "' not found. Available: "
                    + String.join(", ", getAvailableVoices()));
        }

        ByteArrayOutputStream rawAudio = new ByteArrayOutputStream();

        // freetts normally plays sound out loud, but we dont want that.
        // so we trick it into thinking this is a real audio player, and just
        // grab the bytes for ourself instead of sending to speaker
        voice.setAudioPlayer(new AudioPlayer() {
            @Override public void setAudioFormat(AudioFormat format) {
                if (format != null) currentAudioFormat = format;
            }

            @Override public AudioFormat getAudioFormat() {
                return currentAudioFormat;
            }

            @Override public void pause() {}
            @Override public void resume() {}
            @Override public void reset() { rawAudio.reset(); }
            @Override public boolean drain() { return true; }
            @Override public void close() {}
            @Override public float getVolume() { return 1.0f; }
            @Override public void setVolume(float v) {}
            @Override public void startFirstSampleTimer() {}
            @Override public void showMetrics() {}
            @Override public long getTime() { return 0L; }
            @Override public void resetTime() {}
            @Override public void cancel() {}

            // this is the important part, freetts calls this over and over
            // while its talking and we just keep stacking the bytes together
            @Override public boolean write(byte[] audioData) {
                rawAudio.write(audioData, 0, audioData.length);
                return true;
            }

            @Override public boolean write(byte[] audioData, int offset, int size) {
                rawAudio.write(audioData, offset, size);
                return true;
            }

            @Override public void begin(int size) {}
            @Override public boolean end() { return true; }
        });

        voice.allocate();
        try {
            voice.speak(text);
        } finally {
            try {
                voice.deallocate();
            } catch (Exception e) {
                logger.warn("couldnt deallocate voice properly", e);
            }
        }

        byte[] pcmBytes = rawAudio.toByteArray();
        if (pcmBytes.length == 0) {
            throw new RuntimeException("got no audio back for this text, something went wrong");
        }

        // dont just use the constants up top, freetts might give us a diffrent
        // format then what we expected so we grab the real one here
        int sampleRate = (int) currentAudioFormat.getSampleRate();
        int actualBitDepth = currentAudioFormat.getSampleSizeInBits();
        int actualChannels = currentAudioFormat.getChannels();

        return convertPcmToWav(pcmBytes, sampleRate, actualBitDepth, actualChannels);
    }

    // makes the audio, saves it as a wav file, and gives back both the bytes
    // and where the file ended up on disk
    public DacResult textToAudioFileAndBytes(String text) throws Exception {
        byte[] audioBytes = generateAudioBytes(text);

        Path dirPath = Paths.get(outputDir);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path filePath = dirPath.resolve("voice-" + timestamp + ".wav");
        Files.write(filePath, audioBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        byte[] savedAudioBytes = Files.readAllBytes(filePath);

        logger.info("saved file at {}", filePath.toAbsolutePath());

        return new DacResult(savedAudioBytes, filePath.toAbsolutePath().toString());
    }

    // just a little holder class so we can return 2 things at once
    public static class DacResult {
        public final byte[] audioBytes;
        public final String filePath;

        public DacResult(byte[] audioBytes, String filePath) {
            this.audioBytes = audioBytes;
            this.filePath = filePath;
        }
    }

    private byte[] convertPcmToWav(byte[] pcmData, int sampleRate, int bitDepth, int channels) throws IOException {
        if (pcmData == null || pcmData.length == 0) {
            throw new IOException("No PCM audio data received from the speech engine");
        }
        if (sampleRate <= 0 || bitDepth <= 0 || channels <= 0) {
            throw new IOException("Invalid audio format values for WAV conversion");
        }

        int bytesPerSample = Math.max(1, bitDepth / 8);
        int bytesPerFrame = channels * bytesPerSample;
        byte[] normalizedPcm = normalizePcmData(pcmData, bitDepth);

        if (normalizedPcm.length % bytesPerFrame != 0) {
            int paddedLength = ((normalizedPcm.length + bytesPerFrame - 1) / bytesPerFrame) * bytesPerFrame;
            normalizedPcm = Arrays.copyOf(normalizedPcm, paddedLength);
        }

        AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, sampleRate, bitDepth, channels, bytesPerFrame, sampleRate, false);
        AudioInputStream audioInputStream = new AudioInputStream(
                new ByteArrayInputStream(normalizedPcm),
                format,
                normalizedPcm.length / bytesPerFrame
        );

        ByteArrayOutputStream wav = new ByteArrayOutputStream();
        AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, wav);
        return wav.toByteArray();
    }

    private byte[] normalizePcmData(byte[] pcmData, int bitDepth) {
        if (bitDepth != 16 || pcmData.length < 2) {
            return pcmData;
        }

        byte[] normalized = new byte[pcmData.length];
        for (int i = 0; i + 1 < pcmData.length; i += 2) {
            normalized[i] = pcmData[i + 1];
            normalized[i + 1] = pcmData[i];
        }
        return normalized;
    }
}