package bio.terra.axonserver.utils;

import bio.terra.axonserver.service.exception.CloudObjectReadException;
import bio.terra.common.exception.BadRequestException;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.ReadChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.annotation.Nullable;
import org.springframework.http.HttpRange;
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
   * @param byteRange Byte range to read from the object
   * @return InputStream for the object content
   */
  public static InputStream getBucketObject(
      GoogleCredentials googleCredentials,
      String bucketName,
      String objectName,
      @Nullable HttpRange byteRange) {

    // decode encoded slashes in object path
    try {
      objectName = URLDecoder.decode(objectName, StandardCharsets.UTF_8.toString());
    } catch (Exception e) {
      throw new BadRequestException("Bad object path: " + objectName);
    }

    try {
      // Get the ReadChannel for the object
      Storage gcs =
          StorageOptions.newBuilder().setCredentials(googleCredentials).build().getService();
      Blob blob = gcs.get(BlobId.of(bucketName, objectName));
      if (blob == null) {
        throw new BadRequestException("GCS Object not found: Bad bucketName or objectName.");
      }
      ReadChannel readChannel = blob.reader();

      // Seek to the specified readChannel range if byteRange is provided
      if (byteRange != null) {
        readChannel.seek(byteRange.getRangeStart(Long.MAX_VALUE));
        readChannel.limit(byteRange.getRangeEnd(Long.MAX_VALUE));
      }
      return Channels.newInputStream(readChannel);
    } catch (IOException e) {
      throw new CloudObjectReadException("Error reading GCS object: " + objectName);
    }
  }
}
