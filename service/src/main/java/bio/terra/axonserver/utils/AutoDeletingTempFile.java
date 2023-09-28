package bio.terra.axonserver.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoDeletingTempFile implements AutoCloseable {
  private static final Logger logger = LoggerFactory.getLogger(AutoDeletingTempFile.class);
  private final File file;

  public AutoDeletingTempFile(String filePrefix, String fileSuffix) throws IOException {
    FileAttribute<Set<PosixFilePermission>> attr =
        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
    file = Files.createTempFile(filePrefix, fileSuffix, attr).toFile();
    if (!SystemUtils.IS_OS_UNIX) {
      file.setReadable(true, true);
      file.setWritable(true, true);
      file.setExecutable(true, true);
    }
  }

  public File getFile() {
    return file;
  }

  @Override
  public void close() throws IOException {
    logger.info("Deleting AutoDeletingTempFile {}", file);
    Files.deleteIfExists(file.toPath());
  }
}
