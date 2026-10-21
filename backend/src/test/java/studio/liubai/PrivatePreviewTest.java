package studio.liubai;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:preview;DB_CLOSE_DELAY=-1",
      "notes.data-dir=/tmp/liubai-preview-test",
      "SEED_DEMO=false",
      "notes.public-access=false",
      "NOTES_PREVIEW_PASSWORD=valid-test-password-123"
    })
@AutoConfigureMockMvc
class PrivatePreviewTest {
  @Autowired MockMvc mvc;
  @Autowired NoteStore store;

  @Test
  void globalGateBlocksPublishedContentAndAttachments() throws Exception {
    Note n =
        store.create(
            new Note(
                null,
                "Public candidate",
                "should stay private",
                "diary",
                "",
                List.of(),
                false,
                true,
                false,
                null,
                LocalDate.now(),
                null,
                null,
                0));
    store.update(
        n.id(),
        new Note(
            n.id(),
            n.title(),
            n.content(),
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
            n.version()));
    mvc.perform(get("/api/public/" + n.shareId())).andExpect(status().isNotFound());
    mvc.perform(get("/api/public/" + n.shareId() + "/attachments/example"))
        .andExpect(status().isNotFound());
  }
}
