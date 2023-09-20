package bio.terra.axonserver.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

public class AutoDeletingTempDirTest {

  @Test
  public void testTempDirCreation() throws IOException {
    try (AutoDeletingTempDir tempDir = new AutoDeletingTempDir("test")) {
      Path dir = tempDir.getDir();
      assertTrue(Files.exists(dir));
      assertTrue(Files.isDirectory(dir));
    }
  }

  @Test
  public void testTempDirAutoDeletion() throws IOException {
    Path dir;
    try (AutoDeletingTempDir tempDir = new AutoDeletingTempDir("test")) {
      dir = tempDir.getDir();
    }
    assertFalse(Files.exists(dir));
  }

  @Test
  public void testTempDirFileDeletion() throws IOException {
    Path dir;
    Path file;
    try (AutoDeletingTempDir tempDir = new AutoDeletingTempDir("test")) {
      dir = tempDir.getDir();
      file = Files.createFile(dir.resolve("testFile.txt"));
    }
    assertFalse(Files.exists(dir));
    assertFalse(Files.exists(file));
  }
}
