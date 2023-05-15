package bio.terra.axonserver.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.axonserver.testutils.BaseUnitTest;
import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class AutoDeletingTempFileTest extends BaseUnitTest {
  File file;

  @Test
  public void testFileDeletion() throws IOException {
    try (AutoDeletingTempFile tempFile = new AutoDeletingTempFile("prefix", "suffix")) {
      file = tempFile.getFile();
      assertTrue(file.exists());
    }
    assertFalse(file.exists());
  }
}
