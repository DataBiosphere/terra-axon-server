package bio.terra.axonserver.utils;

import bio.terra.axonserver.service.exception.CloudObjectReadException;
import bio.terra.common.exception.BadRequestException;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.ReadChannel;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.StorageOptions;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.apache.commons.io.FilenameUtils;
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
   * @return Contents of the object
   */
  public static File getBucketObject(
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

    BlobId blobId = BlobId.of(bucketName, objectName);
    try (ReadChannel reader =
        StorageOptions.newBuilder()
            .setCredentials(googleCredentials)
            .build()
            .getService()
            .reader(blobId)) {

      Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rwx------");
      File outputFile =
          File.createTempFile(
              FilenameUtils.getBaseName(objectName) + "-",
              "." + FilenameUtils.getExtension(objectName),
              new File(CreateDownloadsFolder.DOWNLOADS_DIR.toString()));
      Files.setPosixFilePermissions(Paths.get(outputFile.getPath()), permissions);
      FileOutputStream outputStream = new FileOutputStream(outputFile);

      long startByteIdx =
          Optional.ofNullable(byteRange).isPresent() ? byteRange.getRangeStart(Long.MAX_VALUE) : 0;
      long endByteIdx =
          Optional.ofNullable(byteRange).isPresent()
              ? byteRange.getRangeEnd(Long.MAX_VALUE)
              : Long.MAX_VALUE - 1;
      long bytesToRead = endByteIdx - startByteIdx;

      ByteBuffer buffer = ByteBuffer.allocate((int) Math.min(MAX_BUFFER_SIZE, bytesToRead));

      long totalBytesRead = 0;
      int bytesRead;
      reader.seek(startByteIdx);
      while (totalBytesRead < bytesToRead && reader.read(buffer) > 0) {
        bytesRead = buffer.position();

        // write bytes in buffer to output stream
        buffer.flip();
        outputStream.write(buffer.array(), 0, buffer.limit());

        // clear buffer and increment bytesRead
        buffer.clear();
        totalBytesRead += bytesRead;
      }

      outputFile.deleteOnExit();
      outputStream.close();
      return outputFile;

    } catch (IndexOutOfBoundsException e) {
      throw new CloudObjectReadException("Object size too large: " + objectName);
    } catch (IOException e) {
      throw new CloudObjectReadException("Error reading object: " + objectName);
    }
  }
}
