package bio.terra.axonserver.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import org.springframework.core.io.InputStreamResource;

// Custom Spring Resource that deletes the underlying file when the Input Stream is closed
public class CleanupInputStreamResource extends InputStreamResource {
  File file;

  public CleanupInputStreamResource(File file) throws FileNotFoundException {
    super(
        new FileInputStream(file) {
          @Override
          public void close() throws IOException {
            super.close();
            Files.delete(file.toPath());
          }
        });
    this.file = file;
  }

  // Override contentLength to avoid reading input stream twice as suggested by InputStreamResource
  // docs
  @Override
  public long contentLength() {
    return file.length();
  }
}
