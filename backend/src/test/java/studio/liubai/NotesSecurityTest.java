package studio.liubai;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:security;DB_CLOSE_DELAY=-1",
      "notes.data-dir=/tmp/liubai-security-test",
      "SEED_DEMO=false",
      "notes.public-access=true",
      "NOTES_PREVIEW_PASSWORD=valid-test-password-123"
    })
@AutoConfigureMockMvc
@Transactional
class NotesSecurityTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired NoteStore store;

  Map<String, Object> input() {
    return new HashMap<>(
        Map.of(
            "title",
            "Private test note",
            "content",
            "Secret search phrase",
            "space",
            "diary",
            "category",
            "Tests",
            "tags",
            List.of("test"),
            "entryDate",
            "2026-10-21",
            "draft",
            false,
            "published",
            true,
            "version",
            0));
  }

  JsonNode create() throws Exception {
    return json.readTree(
        mvc.perform(
                post("/api/notes")
                    .with(user("owner"))
                    .with(csrf())
                    .contentType("application/json")
                    .content(json.writeValueAsBytes(input())))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  @Test
  void anonymousCannotReadSearchOrWrite() throws Exception {
    JsonNode note = create();
    String id = note.get("id").asText();
    mvc.perform(get("/api/notes")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/notes/" + id)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/backup")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/export")).andExpect(status().isUnauthorized());
    mvc.perform(
            put("/api/notes/" + id)
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsBytes(input())))
        .andExpect(status().isUnauthorized());
    mvc.perform(delete("/api/notes/" + id).with(csrf())).andExpect(status().isUnauthorized());
    mvc.perform(
            multipart("/api/notes/" + id + "/attachments")
                .file("file", "private".getBytes())
                .with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createsAreAlwaysPrivateDraftsEvenWithForgedFlags() throws Exception {
    JsonNode n = create();
    assertThat(n.get("published").asBoolean()).isFalse();
    assertThat(n.get("draft").asBoolean()).isTrue();
    mvc.perform(get("/api/public/" + n.get("shareId").asText())).andExpect(status().isNotFound());
  }

  @Test
  void csrfRequiredForAuthenticatedWritesAndLogin() throws Exception {
    mvc.perform(
            post("/api/notes")
                .with(user("owner"))
                .contentType("application/json")
                .content(json.writeValueAsBytes(input())))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content("{\"password\":\"valid-test-password-123\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void explicitPublishAndRevocation() throws Exception {
    JsonNode n = create();
    Map<String, Object> data =
        json.convertValue(n, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    data.put("published", true);
    data.put("draft", false);
    JsonNode published =
        json.readTree(
            mvc.perform(
                    put("/api/notes/" + n.get("id").asText())
                        .with(user("owner"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(json.writeValueAsBytes(data)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    String path = "/api/public/" + n.get("shareId").asText();
    mvc.perform(get(path))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").value("Secret search phrase"));
    data.put("version", published.get("version").asLong());
    data.put("published", false);
    mvc.perform(
            put("/api/notes/" + n.get("id").asText())
                .with(user("owner"))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsBytes(data)))
        .andExpect(status().isOk());
    mvc.perform(get(path)).andExpect(status().isNotFound());
  }

  @Test
  void staleUpdatesCannotOverwriteAnotherDevice() throws Exception {
    JsonNode n = create();
    Map<String, Object> data =
        json.convertValue(n, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    String path = "/api/notes/" + n.get("id").asText();
    data.put("title", "Changed on device A");
    mvc.perform(
            put(path)
                .with(user("owner"))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsBytes(data)))
        .andExpect(status().isOk());
    data.put("title", "Stale device B");
    mvc.perform(
            put(path)
                .with(user("owner"))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsBytes(data)))
        .andExpect(status().isConflict());
    mvc.perform(get(path).with(user("owner")))
        .andExpect(jsonPath("$.title").value("Changed on device A"));
  }

  @Test
  void uploadIsAuthenticatedTypedAndBoundToItsNote() throws Exception {
    JsonNode n = create(), other = create();
    String path = "/api/notes/" + n.get("id").asText() + "/attachments";
    MockMultipartFile bad =
        new MockMultipartFile(
            "file", "attack.svg", "image/svg+xml", "<svg onload='alert(1)'/>".getBytes());
    mvc.perform(multipart(path).file(bad).with(user("owner")).with(csrf()))
        .andExpect(status().isBadRequest());
    MockMultipartFile fake =
        new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes());
    mvc.perform(multipart(path).file(fake).with(user("owner")).with(csrf()))
        .andExpect(status().isBadRequest());
    MockMultipartFile file =
        new MockMultipartFile("file", "日记.txt", "text/plain", "private attachment".getBytes());
    JsonNode attachment =
        json.readTree(
            mvc.perform(multipart(path).file(file).with(user("owner")).with(csrf()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    String url = attachment.get("url").asText();
    mvc.perform(get(url)).andExpect(status().isUnauthorized());
    mvc.perform(get(url).with(user("owner")))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "private, no-store"))
        .andExpect(content().string("private attachment"));
    mvc.perform(
            get("/api/notes/"
                    + other.get("id").asText()
                    + "/attachments/"
                    + attachment.get("id").asText())
                .with(user("owner")))
        .andExpect(status().isNotFound());
    mvc.perform(
            get(
                "/api/public/"
                    + n.get("shareId").asText()
                    + "/attachments/"
                    + attachment.get("id").asText()))
        .andExpect(status().isNotFound());
  }

  @Test
  void deletedRecordsCannotBeRead() throws Exception {
    JsonNode n = create();
    String path = "/api/notes/" + n.get("id").asText();
    mvc.perform(delete(path).with(user("owner")).with(csrf())).andExpect(status().isNoContent());
    mvc.perform(get(path).with(user("owner"))).andExpect(status().isNotFound());
  }

  @Test
  void backupsRestoreAsNewPrivateDrafts() throws Exception {
    create();
    String backup =
        mvc.perform(get("/api/backup").with(user("owner")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    mvc.perform(
            post("/api/restore")
                .with(user("owner"))
                .with(csrf())
                .contentType("application/json")
                .content(backup))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.imported").value(1));
    assertThat(store.all()).hasSize(2).allMatch(n -> !n.published() && n.draft());
  }

  @Test
  void invalidNotesAreRejected() throws Exception {
    Map<String, Object> n = input();
    n.put("space", "invalid");
    mvc.perform(
            post("/api/notes")
                .with(user("owner"))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsBytes(n)))
        .andExpect(status().isBadRequest());
    n = input();
    n.put("tags", List.of("bad\nlabel"));
    mvc.perform(
            post("/api/notes")
                .with(user("owner"))
                .with(csrf())
                .contentType("application/json")
                .content(json.writeValueAsBytes(n)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void passwordLoginCreatesSessionAndLogoutRevokesIt() throws Exception {
    mvc.perform(
            post("/api/auth/login")
                .with(csrf())
                .contentType("application/json")
                .content("{\"password\":\"wrong\"}"))
        .andExpect(status().isUnauthorized());
    MvcResult login =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content("{\"password\":\"valid-test-password-123\"}"))
            .andExpect(status().isOk())
            .andReturn();
    MockHttpSession session = (MockHttpSession) login.getRequest().getSession();
    mvc.perform(get("/api/notes").session(session)).andExpect(status().isOk());
    mvc.perform(post("/api/auth/logout").session(session).with(csrf()))
        .andExpect(status().isNoContent());
    assertThat(session.isInvalid()).isTrue();
  }
}
