package studio.liubai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1",
      "notes.data-dir=/tmp/liubai-demo-test",
      "SEED_DEMO=true",
      "NOTES_PREVIEW_PASSWORD=valid-test-password-123"
    })
class DemoStartupTest {
  @Autowired NoteStore store;
  @Autowired DemoContent seed;

  @Test
  void seedStartsSuccessfullyAndDoesNotDuplicateOnRestart() {
    assertThat(store.all()).hasSize(8).allMatch(n -> !n.published());
    seed.run();
    assertThat(store.all()).hasSize(8);
  }
}
