package bio.terra.axonserver.utils;

import bio.terra.axonserver.service.exception.CloudObjectReadException;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.ReadChannel;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.StorageOptions;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import org.springframework.util.unit.DataSize;

/** Service for interacting with Google Cloud Storage */
public class CloudStorageUtils {

  private static final int MAX_OBJECT_SIZE = (int) DataSize.ofMegabytes(512).toBytes();
  private static final int MAX_BUFFER_SIZE = (int) DataSize.ofKilobytes(64).toBytes();

  // Google pet service account scopes for accessing Google Cloud APIs.
  private static final List<String> PET_SA_SCOPES =
      ImmutableList.of(
          "openid", "email", "profile", "https://www.googleapis.com/auth/cloud-platform");

  public CloudStorageUtils() {}

  public static List<String> getPetScopes() {
    return PET_SA_SCOPES;
  }

  /**
   * Get GoogleCredentials from an access token
   *
   * @param token token to use for the credentials
   * @return GoogleCredentials
   */
  public static GoogleCredentials getGoogleCredentialsFromToken(String token) {
    // The expirationTime argument is only used for refresh tokens, not access tokens.
    AccessToken accessToken = new AccessToken(token, null);
    return GoogleCredentials.create(accessToken);
  }

  /**
   * Get the contents of a GCS bucket object
   *
   * @param googleCredentials Google credentials to use for the request
   * @param bucketName Name of the bucket
   * @param objectName Name of the object
   * @return Contents of the object
   */
  public static byte[] getBucketObject(
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
      throw new CloudObjectReadException("Error reading object: " + objectName);
    }
  }
}
