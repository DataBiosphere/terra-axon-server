package bio.terra.axonserver.service.cloud.gcp;

import bio.terra.axonserver.service.exception.CloudObjectReadException;
import bio.terra.axonserver.utils.BoundedByteArrayOutputStream;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.ReadChannel;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.StorageOptions;
import java.io.IOException;
import java.nio.ByteBuffer;

/** Service for interacting with Google Cloud Storage */
public class CloudStorageService {

  private static final int MAX_OBJECT_SIZE = 512 * 1024 * 1024; // 512 MB
  private static final int MAX_BUFFER_SIZE = 64 * 1024; // 64 KB

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
      ByteBuffer bytes = ByteBuffer.allocate(MAX_BUFFER_SIZE);
      while (reader.read(bytes) > 0) {
        bytes.flip();
        outputStream.write(bytes.array(), 0, bytes.limit());
        bytes.clear();
      }

      return outputStream.toByteArray();
    } catch (IOException e) {
      throw new CloudObjectReadException("Error reading object" + objectName);
    }
  }
}
