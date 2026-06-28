package My_DAC_Project.My_DAC_Project.Controler;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import My_DAC_Project.My_DAC_Project.Service.DAC_Service;
import My_DAC_Project.My_DAC_Project.Obj.DAC;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@CrossOrigin(
    origins = {
        "https://madhavantech.github.io",
        "http://localhost:5173",
        "http://localhost:5174",
        "http://127.0.0.1:5173",
        "http://127.0.0.1:5174",
    },
    allowedHeaders = "*",
    allowCredentials = "true",
    methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS}
)
@RequestMapping("/api")
public class DAC_Controler {

    private static final Logger logger = LoggerFactory.getLogger(DAC_Controler.class);

    @Autowired
    DAC_Service dacService;

    @PostMapping(value = "/convert", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> convertTextToAudio(@RequestBody(required = false) DAC request) {
        try {
            if (request == null || request.getText() == null || request.getText().isBlank()) {
                Map<String, Object> errorResponse = new LinkedHashMap<>();
                errorResponse.put("errorMessage", "Text is required");
                return ResponseEntity.badRequest().body(errorResponse);
            }

            logger.info("converting text to audio: {}", request.getText());

            DAC_Service.DacResult result = dacService.textToAudioFileAndBytes(request.getText());
            byte[] audioBytes = result.audioBytes;
            String savedPath = result.filePath;

            if (audioBytes == null || audioBytes.length == 0) {
                Map<String, Object> errorResponse = new LinkedHashMap<>();
                errorResponse.put("errorMessage", "Audio generation returned empty bytes");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
            }

            logger.info("file saved at: {}", savedPath);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("text", request.getText());
            response.put("audioData", Base64.getEncoder().encodeToString(audioBytes));
            response.put("voices", dacService.getAvailableVoices());
            response.put("filePath", savedPath);
            response.put("message", "Audio generated successfully");

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            logger.error("invalid argument: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("errorMessage", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            logger.error("error converting text to audio", e);
            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("errorMessage", "Internal error generating audio");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping(value = "/voices", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String[]> listVoices() {
        return ResponseEntity.ok(dacService.getAvailableVoices());
    }

    @GetMapping(value = "/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "UP");
        payload.put("service", "dac-backend");
        return ResponseEntity.ok(payload);
    }
}