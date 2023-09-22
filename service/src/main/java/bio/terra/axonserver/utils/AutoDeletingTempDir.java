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

public class AutoDeletingTempDir implements AutoCloseable {
  private final Path dir;

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
    Files.walkFileTree(
        dir, EnumSet.noneOf(FileVisitOption.class), Integer.MAX_VALUE, new DeletionFileVisitor());
  }
}
