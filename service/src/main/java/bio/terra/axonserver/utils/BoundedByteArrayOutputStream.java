package bio.terra.axonserver.utils;

import java.io.ByteArrayOutputStream;

/**
 * A ByteArrayOutputStream that throws an IndexOutOfBoundsException if given byte limit is exceeded
 */
public class BoundedByteArrayOutputStream extends ByteArrayOutputStream {

  private final int limit;

  public BoundedByteArrayOutputStream(int limit) {
    this.limit = limit;
  }

  @Override
  public synchronized void write(int b) {
    if (count + 1 > limit) {
      throw new IndexOutOfBoundsException();
    }
    super.write(b);
  }

  @Override
  public synchronized void write(byte[] b, int off, int len) {
    if (count + len > limit) {
      throw new IndexOutOfBoundsException();
    }
    super.write(b, off, len);
  }
}
