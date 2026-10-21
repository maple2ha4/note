package studio.liubai;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@Controller
class WebConfig {
  @GetMapping({"/", "/share/{share}"})
  String index() {
    return "forward:/index.html";
  }
}

@RestControllerAdvice
class Errors {
  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<?> status(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode())
        .body(Map.of("message", e.getReason() == null ? "请求无法完成" : e.getReason()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> validation(MethodArgumentNotValidException e) {
    return ResponseEntity.badRequest().body(Map.of("message", "请检查标题、日期、标签与内容长度"));
  }

  @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
  ResponseEntity<?> upload() {
    return ResponseEntity.status(413).body(Map.of("message", "附件不能超过 10MB"));
  }
}
