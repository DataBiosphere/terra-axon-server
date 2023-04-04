package bio.terra.axonserver.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CreateDownloadsFolder {

  Logger logger = LoggerFactory.getLogger(CreateDownloadsFolder.class);
  public static final Path DOWNLOADS_DIR = Paths.get(System.getProperty("user.home"), "/downloads");

  // Create a private directory to store temporary files used by GetFile controller
  @PostConstruct
  public void run() {
    try {
      if (!Files.exists(DOWNLOADS_DIR)) {
        Files.createDirectories(DOWNLOADS_DIR);
        logger.info("Created downloads directory: " + DOWNLOADS_DIR.toString());
      }
    } catch (IOException e) {
      logger.error("Failed to create downloads directory: " + DOWNLOADS_DIR.toString());
      throw new RuntimeException(e);
    }
  }
}
