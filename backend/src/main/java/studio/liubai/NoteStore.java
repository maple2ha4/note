package studio.liubai;

import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.*;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class NoteStore {
  final JdbcTemplate db;
  final java.nio.file.Path uploads;
  final RowMapper<Note> mapper =
      (r, i) ->
          new Note(
              r.getString("id"),
              r.getString("title"),
              r.getString("content"),
              r.getString("space"),
              r.getString("category"),
              r.getString("tags").isEmpty() ? List.of() : List.of(r.getString("tags").split("\n")),
              r.getBoolean("favorite"),
              r.getBoolean("published"),
              r.getBoolean("draft"),
              r.getString("share_id"),
              r.getDate("entry_date").toLocalDate(),
              r.getTimestamp("created_at").toLocalDateTime(),
              r.getTimestamp("updated_at").toLocalDateTime(),
              r.getLong("version"));

  public NoteStore(
      JdbcTemplate db,
      @org.springframework.beans.factory.annotation.Value("${notes.data-dir}") String data) {
    this.db = db;
    this.uploads = java.nio.file.Path.of(data, "uploads");
  }

  public List<Note> all() {
    return db.query("SELECT * FROM notes WHERE owner_id='owner' ORDER BY updated_at DESC", mapper);
  }

  public Note get(String id) {
    return db.query("SELECT * FROM notes WHERE id=? AND owner_id='owner'", mapper, id).stream()
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在"));
  }

  public Note publicNote(String share) {
    return db
        .query(
            "SELECT * FROM notes WHERE share_id=? AND published=TRUE AND draft=FALSE AND"
                + " owner_id='owner'",
            mapper,
            share)
        .stream()
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "内容未公开"));
  }

  public Note create(Note input) {
    String id = UUID.randomUUID().toString();
    LocalDateTime now = LocalDateTime.now();
    db.update(
        "INSERT INTO"
            + " notes(id,owner_id,title,content,space,category,tags,favorite,published,draft,share_id,entry_date,created_at,updated_at,version)"
            + " VALUES(?,'owner',?,?,?,?,?,?,FALSE,TRUE,?,?,?,?,0)",
        id,
        input.title(),
        input.content(),
        input.space(),
        input.category(),
        String.join("\n", input.tags()),
        input.favorite(),
        UUID.randomUUID().toString(),
        input.entryDate(),
        now,
        now);
    return get(id);
  }

  public Note update(String id, Note input) {
    get(id);
    int count =
        db.update(
            "UPDATE notes SET"
                + " title=?,content=?,space=?,category=?,tags=?,favorite=?,published=?,draft=?,entry_date=?,updated_at=?,version=version+1"
                + " WHERE id=? AND owner_id='owner' AND version=?",
            input.title(),
            input.content(),
            input.space(),
            input.category(),
            String.join("\n", input.tags()),
            input.favorite(),
            input.published(),
            input.draft(),
            input.entryDate(),
            LocalDateTime.now(),
            id,
            input.version());
    if (count == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "记录已在其他窗口更新，请重新打开后编辑");
    return get(id);
  }

  public void delete(String id) {
    get(id);
    List<String> files =
        db.query(
            "SELECT storage_key FROM attachments WHERE note_id=?", (r, i) -> r.getString(1), id);
    db.update("DELETE FROM notes WHERE id=? AND owner_id='owner'", id);
    for (String key : files)
      try {
        java.nio.file.Files.deleteIfExists(uploads.resolve(key));
      } catch (java.io.IOException e) {
        org.slf4j.LoggerFactory.getLogger(NoteStore.class)
            .warn("Attachment cleanup deferred after deleting note {}", id);
      }
  }
}
