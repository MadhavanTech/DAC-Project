package My_DAC_Project.My_DAC_Project.Obj;

public class DAC {

    private byte[] audioData;
    private String text;
    private String[] voices;
    private String errorMessage;
    private String filePath;

    public DAC() {}

    public DAC(String text, byte[] audioData, String[] voices) {
        this.text = text;
        this.voices = voices;
        this.audioData = audioData;
        this.errorMessage = null;
    }

    public DAC(String text, byte[] audioData, String[] voices, String filePath) {
        this.text = text;
        this.voices = voices;
        this.audioData = audioData;
        this.filePath = filePath;
        this.errorMessage = null;
    }

    
    public byte[] getAudioData() {
        return audioData;
    }

    public String getText() {
        return text;
    }

    public String[] getVoices() {
        return voices;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setAudioData(byte[] audioData) {
        this.audioData = audioData;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setVoices(String[] voices) {
        this.voices = voices;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
}