package My_DAC_Project.My_DAC_Project.Controler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import My_DAC_Project.My_DAC_Project.Service.DAC_Service;
import My_DAC_Project.My_DAC_Project.Obj.DAC;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api")
public class DAC_Controler {

    private static final Logger logger = LoggerFactory.getLogger(DAC_Controler.class);

    @Autowired
    DAC_Service dacService;

    @PostMapping(value = "/convert", produces = "application/json")
    public ResponseEntity<DAC> convertTextToAudio(@RequestBody DAC request) {
        try {
            logger.info("Converting text to audio: {}", request.getText());

            // This does BOTH steps at once:
            // 1. generates the byte[]
            // 2. saves it to disk as a real .wav sound file
            DAC_Service.DacResult result = dacService.textToAudioFileAndBytes(request.getText());

            byte[] audioBytes = result.audioBytes;   // the bytes (sent back to frontend)
            String savedPath = result.filePath;       // where the real sound file was saved

            if (audioBytes == null || audioBytes.length == 0) {
                DAC errorResponse = new DAC();
                errorResponse.setErrorMessage("Audio generation returned empty bytes");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            }

            logger.info("Sound file saved at: {}", savedPath);

            DAC response = new DAC(request.getText(), audioBytes, dacService.getAvailableVoices(), savedPath);
            
            System.out.println(response);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument: {}", e.getMessage(), e);
            DAC errorResponse = new DAC();
            errorResponse.setErrorMessage(e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            logger.error("Error converting text to audio", e);
            DAC errorResponse = new DAC();
            errorResponse.setErrorMessage("Internal error generating audio");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping(value = "/voices", produces = "application/json")
    public ResponseEntity<String[]> listVoices() {
        return ResponseEntity.ok(dacService.getAvailableVoices());
    }
}