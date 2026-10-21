package studio.liubai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.zip.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class NotesController {
  private final NoteStore store;
  private final boolean publicAccess;

  public NotesController(NoteStore store, @Value("${notes.public-access}") boolean publicAccess) {
    this.store = store;
    this.publicAccess = publicAccess;
  }

  public record Input(
      @NotBlank @Size(max = 300) String title,
      @NotNull @Size(max = 1000000) String content,
      @Pattern(regexp = "knowledge|thoughts|diary") @NotNull String space,
      @NotNull @Size(max = 80) String category,
      @NotNull @Size(max = 30)
          List<@NotBlank @Size(max = 40) @Pattern(regexp = "[^\\r\\n,]+") String> tags,
      boolean favorite,
      boolean published,
      boolean draft,
      @NotNull LocalDate entryDate,
      long version) {
    Note note() {
      return new Note(
          null,
          title.strip(),
          content,
          space,
          category.strip(),
          tags,
          favorite,
          published,
          draft,
          null,
          entryDate,
          null,
          null,
          version);
    }
  }

  @GetMapping("/api/notes")
  List<Note> list() {
    return store.all();
  }

  @GetMapping("/api/notes/{id}")
  Note get(@PathVariable String id) {
    return store.get(id);
  }

  @PostMapping("/api/notes")
  Note create(@Valid @RequestBody Input input) {
    return store.create(input.note());
  }

  @PutMapping("/api/notes/{id}")
  Note update(@PathVariable String id, @Valid @RequestBody Input input) {
    return store.update(id, input.note());
  }

  @DeleteMapping("/api/notes/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void delete(@PathVariable String id) {
    store.delete(id);
  }

  @GetMapping("/api/public/{share}")
  Note shared(@PathVariable String share) {
    if (!publicAccess) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    Note n = store.publicNote(share);
    return new Note(
        n.id(),
        n.title(),
        n.content()
            .replace(
                "/api/notes/" + n.id() + "/attachments/", "/api/public/" + share + "/attachments/"),
        n.space(),
        n.category(),
        n.tags(),
        false,
        true,
        false,
        n.shareId(),
        n.entryDate(),
        n.createdAt(),
        n.updatedAt(),
        n.version());
  }

  @GetMapping("/api/export")
  ResponseEntity<byte[]> export() throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
      for (Note n : store.all()) {
        zip.putNextEntry(new ZipEntry(n.space() + "/" + n.id() + ".md"));
        String metadata =
            "---\ntitle: "
                + new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(n.title())
                + "\nspace: "
                + n.space()
                + "\ncategory: "
                + new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(n.category())
                + "\ntags: "
                + new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(n.tags())
                + "\ndate: "
                + n.entryDate()
                + "\nprivate: true\n---\n\n";
        zip.write((metadata + n.content()).getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
      zip.putNextEntry(new ZipEntry("README.txt"));
      zip.write(
          "留白 · Notes Markdown 导出。附件和完整元数据请使用 JSON 备份及服务端 .data 目录备份；恢复文档默认私密。"
              .getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=liubai-notes.zip")
        .contentType(MediaType.parseMediaType("application/zip"))
        .body(bytes.toByteArray());
  }

  @GetMapping("/api/backup")
  Map<String, Object> backup() {
    return Map.of(
        "format",
        "liubai-notes",
        "version",
        1,
        "exportedAt",
        LocalDateTime.now(),
        "notes",
        store.all());
  }

  public record Backup(
      @NotNull String format, int version, @NotNull @Size(max = 500) List<@Valid Input> notes) {}

  @PostMapping("/api/restore")
  @org.springframework.transaction.annotation.Transactional
  Map<String, Integer> restore(@Valid @RequestBody Backup backup) {
    if (!backup.format().equals("liubai-notes") || backup.version() != 1)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的备份格式");
    for (Input n : backup.notes()) store.create(n.note());
    return Map.of("imported", backup.notes().size());
  }
}
