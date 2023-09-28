package bio.terra.axonserver.utils;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A tmp directory that will auto delete. For use in try-with-resources blocks. */
public class AutoDeletingTempDir implements AutoCloseable {

  private static final Logger logger = LoggerFactory.getLogger(AutoDeletingTempDir.class);

  private final Path dir;

  /**
   * @param dirPrefix Prefix of the created temporary directory
   */
  public AutoDeletingTempDir(String dirPrefix) throws IOException {
    FileAttribute<Set<PosixFilePermission>> attr =
        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
    dir = Files.createTempDirectory(dirPrefix, attr);
  }

  public Path getDir() {
    return dir;
  }

  @Override
  public void close() throws IOException {
    logger.info("Deleting AutoDeletingTempDir {}", dir.toString());
    Files.walkFileTree(
        dir, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, new DeletionFileVisitor());
  }
}
