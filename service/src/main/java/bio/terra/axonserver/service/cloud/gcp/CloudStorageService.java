package bio.terra.axonserver.service.cloud.gcp;

import bio.terra.axonserver.utils.BoundedByteArrayOutputStream;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.ReadChannel;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.StorageOptions;
import java.io.IOException;
import java.nio.ByteBuffer;
import javax.ws.rs.InternalServerErrorException;

/** Service for interacting with Google Cloud Storage */
public class CloudStorageService {
  private static final int MAX_OBJECT_SIZE = 512 * 1024 * 1024; // 512 MB

  public CloudStorageService() {}

  /**
   * Get the contents of a GCS bucket object
   *
   * @param googleCredentials Google credentials to use for the request
   * @param bucketName Name of the bucket
   * @param objectName Name of the object
   * @return Contents of the object
   */
  public byte[] getBucketObject(
      GoogleCredentials googleCredentials, String bucketName, String objectName) {

    BlobId blobId = BlobId.of(bucketName, objectName);
    try (ReadChannel reader =
        StorageOptions.newBuilder()
            .setCredentials(googleCredentials)
            .build()
            .getService()
            .reader(blobId)) {

      BoundedByteArrayOutputStream outputStream = new BoundedByteArrayOutputStream(MAX_OBJECT_SIZE);
      ByteBuffer bytes = ByteBuffer.allocate(64 * 1024);
      while (reader.read(bytes) > 0) {
        bytes.flip();
        outputStream.write(bytes.array(), 0, bytes.limit());
        bytes.clear();
      }

      return outputStream.toByteArray();
    } catch (IOException e) {
      throw new InternalServerErrorException("Error reading bucket object" + objectName);
    }
  }
}
