package studio.liubai;

import jakarta.servlet.http.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.*;
import org.springframework.security.core.context.*;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AuthController {
  private final PreviewIdentity identity;
  private final boolean local;
  private final Environment env;
  private final Map<String, List<Long>> attempts = new ConcurrentHashMap<>();

  public AuthController(
      PreviewIdentity identity, @Value("${notes.local-login}") boolean local, Environment env) {
    this.identity = identity;
    this.local = local;
    this.env = env;
  }

  @GetMapping("/api/health")
  Map<String, String> health() {
    return Map.of("status", "ok");
  }

  @GetMapping("/api/auth/csrf")
  Map<String, String> csrf(CsrfToken csrf) {
    return Map.of("token", csrf.getToken(), "headerName", csrf.getHeaderName());
  }

  @GetMapping("/api/auth/session")
  Map<String, Object> session(Authentication authentication) {
    boolean authenticated = authentication != null && authentication.isAuthenticated();
    return Map.of(
        "authenticated",
        authenticated,
        "name",
        "留白的主人",
        "localLogin",
        local,
        "oidc",
        env.containsProperty("OIDC_ISSUER_URI")
            && env.containsProperty("OIDC_CLIENT_ID")
            && env.containsProperty("OIDC_CLIENT_SECRET")
            && env.containsProperty("OIDC_OWNER_SUBJECT"),
        "publicAccess",
        env.getProperty("notes.public-access", Boolean.class, false));
  }

  record Login(String password) {}

  @PostMapping("/api/auth/login")
  Map<String, Object> login(
      @RequestBody Login input, HttpServletRequest request, HttpServletResponse response) {
    if (!local) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "密码登录未启用");
    String key = request.getRemoteAddr();
    long now = Instant.now().toEpochMilli();
    if (attempts.size() > 10000) attempts.clear();
    List<Long> times =
        attempts.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()));
    synchronized (times) {
      times.removeIf(t -> now - t > 900000);
      if (times.size() >= 10)
        throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "尝试过于频繁，请 15 分钟后重试");
      times.add(now);
    }
    if (!identity.matches(input.password()))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "密码不正确");
    attempts.remove(key);
    request.getSession();
    request.changeSessionId();
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(
        new UsernamePasswordAuthenticationToken("owner", null, List.of(() -> "ROLE_OWNER")));
    SecurityContextHolder.setContext(context);
    new HttpSessionSecurityContextRepository().saveContext(context, request, response);
    return Map.of("authenticated", true);
  }
}
