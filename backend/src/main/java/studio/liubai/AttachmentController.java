package studio.liubai;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AttachmentController {
  final NoteStore store;
  final JdbcTemplate db;
  final Path dir;
  final boolean publicAccess;

  public AttachmentController(
      NoteStore store,
      JdbcTemplate db,
      @Value("${notes.data-dir}") String data,
      @Value("${notes.public-access}") boolean access)
      throws IOException {
    this.store = store;
    this.db = db;
    dir = Path.of(data, "uploads");
    Files.createDirectories(dir);
    publicAccess = access;
  }

  @PostMapping("/api/notes/{id}/attachments")
  Map<String, Object> upload(@PathVariable String id, @RequestParam MultipartFile file)
      throws IOException {
    store.get(id);
    if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "附件大小须为 1B–10MB");
    String name =
        Optional.ofNullable(file.getOriginalFilename())
            .orElse("attachment")
            .replaceAll("[\\\\/\\r\\n\\x00-\\x1F]", "_");
    if (name.length() > 200) name = name.substring(name.length() - 200);
    String ext =
        name.contains(".")
            ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
            : "";
    Map<String, String> types =
        Map.of(
            "png",
            "image/png",
            "jpg",
            "image/jpeg",
            "jpeg",
            "image/jpeg",
            "webp",
            "image/webp",
            "gif",
            "image/gif",
            "pdf",
            "application/pdf",
            "txt",
            "text/plain",
            "md",
            "text/markdown",
            "csv",
            "text/csv",
            "zip",
            "application/zip");
    String type = types.get(ext);
    if (type == null)
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "支持 PNG、JPEG、WebP、GIF、PDF、TXT、MD、CSV、ZIP");
    byte[] bytes = file.getBytes();
    boolean valid =
        switch (ext) {
          case "png" ->
              bytes.length >= 8
                  && bytes[0] == (byte) 137
                  && bytes[1] == 80
                  && bytes[2] == 78
                  && bytes[3] == 71;
          case "jpg", "jpeg" ->
              bytes.length >= 3
                  && bytes[0] == (byte) 255
                  && bytes[1] == (byte) 216
                  && bytes[2] == (byte) 255;
          case "gif" ->
              bytes.length >= 6
                  && new String(bytes, 0, 3, java.nio.charset.StandardCharsets.US_ASCII)
                      .equals("GIF");
          case "webp" ->
              bytes.length >= 12
                  && new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII)
                      .equals("RIFF")
                  && new String(bytes, 8, 4, java.nio.charset.StandardCharsets.US_ASCII)
                      .equals("WEBP");
          case "pdf" ->
              bytes.length >= 5
                  && new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)
                      .equals("%PDF-");
          case "zip" -> bytes.length >= 4 && bytes[0] == 80 && bytes[1] == 75;
          default -> true;
        };
    if (!valid) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件内容与扩展名不匹配");
    String aid = UUID.randomUUID().toString(), key = UUID.randomUUID().toString();
    Files.write(dir.resolve(key), bytes, StandardOpenOption.CREATE_NEW);
    try {
      db.update(
          "INSERT INTO attachments VALUES(?,?,?,?,?,?)", aid, id, name, type, file.getSize(), key);
    } catch (RuntimeException e) {
      Files.deleteIfExists(dir.resolve(key));
      throw e;
    }
    return Map.of(
        "id", aid, "name", name, "url", "/api/notes/" + id + "/attachments/" + aid, "type", type);
  }

  @GetMapping("/api/notes/{id}/attachments/{aid}")
  ResponseEntity<Resource> download(@PathVariable String id, @PathVariable String aid) {
    store.get(id);
    return file(id, aid);
  }

  @GetMapping("/api/public/{share}/attachments/{aid}")
  ResponseEntity<Resource> shared(@PathVariable String share, @PathVariable String aid) {
    if (!publicAccess) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    return file(store.publicNote(share).id(), aid);
  }

  private ResponseEntity<Resource> file(String id, String aid) {
    Map<String, Object> row =
        db
            .query(
                "SELECT * FROM attachments WHERE id=? AND note_id=?",
                (r, i) ->
                    Map.<String, Object>of(
                        "key",
                        r.getString("storage_key"),
                        "name",
                        r.getString("filename"),
                        "type",
                        r.getString("media_type")),
                aid,
                id)
            .stream()
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    String type = row.get("type").toString();
    String disposition = type.startsWith("image/") ? "inline" : "attachment";
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.builder(disposition)
                .filename(row.get("name").toString(), java.nio.charset.StandardCharsets.UTF_8)
                .build()
                .toString())
        .header("Cache-Control", "private, no-store")
        .header("X-Content-Type-Options", "nosniff")
        .contentType(MediaType.parseMediaType(type))
        .body(new FileSystemResource(dir.resolve(row.get("key").toString())));
  }
}
