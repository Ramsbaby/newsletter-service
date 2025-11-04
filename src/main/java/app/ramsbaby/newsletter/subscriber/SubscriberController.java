package app.ramsbaby.newsletter.subscriber;

import app.ramsbaby.newsletter.config.AppProps;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/subscribers")
@CrossOrigin(origins = "*")
public class SubscriberController {

    private final SubscriberService subscriberService;
    private final AppProps props;

    public SubscriberController(SubscriberService subscriberService, AppProps props) {
        this.subscriberService = subscriberService;
        this.props = props;
    }

    @GetMapping
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(subscriberService.listAll());
    }

    @PostMapping
    public ResponseEntity<?> subscribe(@RequestParam @Email @NotBlank String email) {
        subscriberService.subscribe(email);
        HttpHeaders headers = new HttpHeaders();
        String target = (props.getSiteUrl() != null ? props.getSiteUrl() : "/") + "/success/";
        headers.add(HttpHeaders.LOCATION, target);
        return new ResponseEntity<>(headers, HttpStatus.SEE_OTHER);
    }

    @GetMapping("/confirm")
    public ResponseEntity<?> confirm(@RequestParam String token) {
        subscriberService.confirm(token);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/unsubscribe")
    public ResponseEntity<?> unsubscribe(@RequestParam String token) {
        subscriberService.unsubscribe(token);
        return ResponseEntity.ok().build();
    }

    /**
     * 구독자 삭제 (Admin 전용)
     * 
     * ID로 삭제: DELETE /api/subscribers/123
     * 이메일로 삭제: DELETE /api/subscribers?email=user@example.com
     * 
     * @param id 구독자 ID (optional)
     * @param email 구독자 이메일 (optional)
     * @return 204 No Content (성공), 404 Not Found (없음), 400 Bad Request (파라미터 누락)
     */
    @DeleteMapping({"/{id}", ""})
    public ResponseEntity<?> delete(
            @PathVariable(required = false) Long id,
            @RequestParam(required = false) @Email String email) {
        
        // ID와 email 둘 다 없으면 400
        if (id == null && email == null) {
            return ResponseEntity.badRequest()
                .body("ID 또는 email 파라미터 중 하나가 필요합니다.");
        }

        // ID 우선 처리
        int deleted;
        if (id != null) {
            deleted = subscriberService.deleteById(id);
        } else {
            deleted = subscriberService.deleteByEmail(email);
        }

        // 결과 반환
        if (deleted > 0) {
            return ResponseEntity.noContent().build(); // 204 No Content
        } else {
            return ResponseEntity.notFound().build(); // 404 Not Found
        }
    }
}

