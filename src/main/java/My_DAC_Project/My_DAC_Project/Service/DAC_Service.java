package My_DAC_Project.My_DAC_Project.Service;

import com.sun.speech.freetts.Voice;
import com.sun.speech.freetts.VoiceManager;
import com.sun.speech.freetts.audio.AudioPlayer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

@Service
public class DAC_Service {

    private static final Logger logger = LoggerFactory.getLogger(DAC_Service.class);
    private static final String VOICE_REGISTRY =
        "com.sun.speech.freetts.en.us.cmu_us_kal.KevinVoiceDirectory";
    private static final String DEFAULT_VOICE = "kevin16";

    private static final int SAMPLE_RATE = 16000;
    private static final int BIT_DEPTH = 16;
    private static final int CHANNELS = 1;

    @Value("${audio.output.dir:generated-audio}")
    private String outputDir;

    private AudioFormat currentAudioFormat = new AudioFormat(
        SAMPLE_RATE, BIT_DEPTH, CHANNELS, true, false
    );

    @PostConstruct
    public void init() {
        System.setProperty("freetts.voices", VOICE_REGISTRY);
        logger.info("FreeTTS voice registry initialized: {}", VOICE_REGISTRY);
        Voice[] voices = VoiceManager.getInstance().getVoices();
        logger.info("Voices found on startup: {}", voices.length);
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
    	
    	    System.out.println("i am sound");
        return generateAudioBytes(text, DEFAULT_VOICE);
    }

    public synchronized byte[] generateAudioBytes(String text, String voiceName) throws Exception {
        System.setProperty("freetts.voices", VOICE_REGISTRY);

        VoiceManager voiceManager = VoiceManager.getInstance();
        String chosen = (voiceName == null || voiceName.isBlank()) ? DEFAULT_VOICE : voiceName;
        logger.info("Requesting voice: {}", chosen);

        Voice voice = voiceManager.getVoice(chosen);
        if (voice == null) {
            String available = String.join(", ", getAvailableVoices());
            throw new RuntimeException("Voice '" + chosen + "' not found. Available: " + available);
        }

        ByteArrayOutputStream rawAudio = new ByteArrayOutputStream();

        voice.setAudioPlayer(new AudioPlayer() {
            @Override public void setAudioFormat(AudioFormat format) {
                if (format != null) {
                    currentAudioFormat = format;
                }
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
            @Override public boolean write(byte[] audioData) {
                logger.info("write(byte[]) called, length={}, odd={}", audioData.length, (audioData.length % 2 != 0));
                rawAudio.write(audioData, 0, audioData.length);
                return true;
            }
            @Override public boolean write(byte[] audioData, int offset, int size) {
                logger.info("write(byte[],offset,size) called, offset={}, size={}, odd={}", offset, size, (size % 2 != 0));
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
                logger.warn("Error during voice.deallocate()", e);
            }
        }

        byte[] pcmBytes = rawAudio.toByteArray();
        logger.info("Generated {} bytes of PCM audio (odd total={})", pcmBytes.length, (pcmBytes.length % 2 != 0));

        if (pcmBytes.length == 0) {
            throw new RuntimeException("PCM audio is empty — voice.speak() produced no output");
        }

        // DIAGNOSTIC: log the ACTUAL format FreeTTS reported, not what we assumed
        logger.info("Actual audio format used: sampleRate={}, sampleSizeInBits={}, channels={}, encoding={}, bigEndian={}",
            currentAudioFormat.getSampleRate(),
            currentAudioFormat.getSampleSizeInBits(),
            currentAudioFormat.getChannels(),
            currentAudioFormat.getEncoding(),
            currentAudioFormat.isBigEndian());

        int sampleRate = (int) currentAudioFormat.getSampleRate();
        int actualBitDepth = currentAudioFormat.getSampleSizeInBits();
        int actualChannels = currentAudioFormat.getChannels();

        // Use the REAL reported values instead of the hardcoded constants —
        // if FreeTTS reports something different from BIT_DEPTH/CHANNELS,
        // using the wrong values here corrupts the WAV header vs actual data.
        byte[] wavBytes = convertPcmToWav(pcmBytes, sampleRate, actualBitDepth, actualChannels);
        logger.info("WAV bytes generated: {}", wavBytes.length);
        return wavBytes;
    }

    /**
     * SIMPLE VERSION:
     * 1. Generates the audio bytes (byte[]) from text.
     * 2. Saves those same bytes to disk as a real .wav sound file.
     * 3. Returns BOTH: the byte[] (for sending to frontend / Base64)
     *    and the file path (where the sound file was saved).
     */
    public DacResult textToAudioFileAndBytes(String text) throws Exception {
        // Step 1: get the bytes (this is just numbers, not a file yet)
        byte[] audioBytes = generateAudioBytes(text);

        // Step 2: write those exact bytes to a .wav file on disk (now it IS a sound file)
        Path dirPath = Paths.get(outputDir);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
        }
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path filePath = dirPath.resolve("output_" + timestamp + ".wav");
        Files.write(filePath, audioBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        logger.info("Saved sound file: {}", filePath.toAbsolutePath());

        // Step 3: return both things together
        return new DacResult(audioBytes, filePath.toAbsolutePath().toString());
    }

    /**
     * Simple holder for the two things you want: the bytes AND the file path.
     */
    public static class DacResult {
        public final byte[] audioBytes;
        public final String filePath;

        public DacResult(byte[] audioBytes, String filePath) {
            this.audioBytes = audioBytes;
            this.filePath = filePath;
        }
    }

    private byte[] convertPcmToWav(byte[] pcmData, int sampleRate,
                                    int bitDepth, int channels) throws IOException {
        ByteArrayOutputStream wav = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(wav);

        int dataSize = pcmData.length;
        int byteRate = sampleRate * channels * (bitDepth / 8);
        short blockAlign = (short) (channels * (bitDepth / 8));

        dos.writeBytes("RIFF");
        writeLittleEndianInt(dos, 36 + dataSize);
        dos.writeBytes("WAVE");

        dos.writeBytes("fmt ");
        writeLittleEndianInt(dos, 16);
        writeLittleEndianShort(dos, (short) 1);
        writeLittleEndianShort(dos, (short) channels);
        writeLittleEndianInt(dos, sampleRate);
        writeLittleEndianInt(dos, byteRate);
        writeLittleEndianShort(dos, blockAlign);
        writeLittleEndianShort(dos, (short) bitDepth);

        dos.writeBytes("data");
        writeLittleEndianInt(dos, dataSize);

        // CRITICAL FIX: FreeTTS outputs big-endian 16-bit PCM samples,
        // but the WAV format requires little-endian PCM data.
        // Without this swap, every sample's two bytes are reversed,
        // which corrupts the waveform into garbled/static audio
        // even though the WAV header itself is perfectly valid.
        if (bitDepth == 16) {
            byte[] swapped = new byte[pcmData.length];
            for (int i = 0; i + 1 < pcmData.length; i += 2) {
                swapped[i] = pcmData[i + 1];
                swapped[i + 1] = pcmData[i];
            }
            dos.write(swapped);
        } else {
            dos.write(pcmData);
        }

        dos.flush();

        return wav.toByteArray();
    }

    private void writeLittleEndianInt(DataOutputStream dos, int value) throws IOException {
        dos.writeByte(value & 0xFF);
        dos.writeByte((value >> 8) & 0xFF);
        dos.writeByte((value >> 16) & 0xFF);
        dos.writeByte((value >> 24) & 0xFF);
    }

    private void writeLittleEndianShort(DataOutputStream dos, short value) throws IOException {
        dos.writeByte(value & 0xFF);
        dos.writeByte((value >> 8) & 0xFF);
    }
}