package studio.liubai;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PreviewIdentity {
  private final String hash;

  public PreviewIdentity(
      @Value("${notes.data-dir}") String dir, @Value("${NOTES_PREVIEW_PASSWORD:}") String password)
      throws Exception {
    Path data = Path.of(dir);
    Files.createDirectories(data);
    try {
      Files.setPosixFilePermissions(data, PosixFilePermissions.fromString("rwx------"));
    } catch (UnsupportedOperationException ignored) {
    }
    if (password == null || password.isBlank()) {
      Path file = data.resolve("preview-password");
      if (!Files.exists(file)) {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        Files.writeString(
            file,
            Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),
            StandardOpenOption.CREATE_NEW);
      }
      try {
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
      } catch (UnsupportedOperationException ignored) {
      }
      password = Files.readString(file).strip();
    }
    if (password.length() < 12)
      throw new IllegalStateException("Preview password must contain at least 12 characters");
    hash = new BCryptPasswordEncoder().encode(password);
  }

  public boolean matches(String raw) {
    return raw != null && new BCryptPasswordEncoder().matches(raw, hash);
  }
}
