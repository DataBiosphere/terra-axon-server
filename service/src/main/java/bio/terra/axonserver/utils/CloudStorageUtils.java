package bio.terra.axonserver.utils;

import bio.terra.axonserver.service.exception.CloudObjectReadException;
import bio.terra.common.exception.BadRequestException;
import com.google.api.gax.paging.Page;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.ReadChannel;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.Storage.BlobListOption;
import com.google.cloud.storage.StorageOptions;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.springframework.http.HttpRange;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

/** Service for interacting with Google Cloud Storage */
public class CloudStorageUtils {

  /**
   * Get the contents of a GCS bucket object
   *
   * @param googleCredentials Google credentials to use for the request
   * @param bucketName Name of the bucket
   * @param objectName Name of the object
   * @param byteRange Byte range to read from the object
   * @return InputStream for the object content
   */

  @Retryable(
      maxAttempts = 3,
      backoff = @Backoff(delay = 1000))
  public static InputStream getBucketObject(
      GoogleCredentials googleCredentials,
      String bucketName,
      String objectName,
      @Nullable HttpRange byteRange) {
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

  /**
   * Recursively downloads all files in a given GCS directory to a local directory.
   *
   * @param googleCredentials Google credentials to use for the request
   * @param bucketName Name of the bucket
   * @param directoryPath Path to directory to download in gcs
   * @param localDestination Path where files should be written locally
   * @param filterSuffix Optional suffix to filter for
   */
  public static void downloadGcsDir(
      GoogleCredentials googleCredentials,
      String bucketName,
      String directoryPath,
      String localDestination,
      String filterSuffix) {
    Storage gcs =
        StorageOptions.newBuilder().setCredentials(googleCredentials).build().getService();
    Page<Blob> blobs = gcs.list(bucketName, BlobListOption.prefix(directoryPath));
    for (Blob blob : blobs.iterateAll()) {
      String blobName = blob.getName();
      if (blobName.endsWith(filterSuffix)) {
        Blob blobToRead = gcs.get(BlobId.of(bucketName, blobName));
        byte[] content = blobToRead.getContent();

        String relativePath = blobName.substring(directoryPath.length());
        File localFile = Paths.get(localDestination, relativePath).toFile();
        if (!localFile.getParentFile().mkdirs() && !localFile.getParentFile().exists()) {
          throw new RuntimeException("Error creating local directory for downloaded dependency");
        }
        ;

        try (FileOutputStream fos = new FileOutputStream(localFile)) {
          fos.write(content);
        } catch (IOException e) {
          throw new RuntimeException("Error downloading dependency: " + e);
        }
      }
    }
  }

  /**
   * Parse a bucket name and object name from a gcs URI
   *
   * @param gcsUri gs:// URI to parse
   */
  public static String[] extractBucketAndObjectFromUri(String gcsUri) {
    if (gcsUri == null || gcsUri.isEmpty()) {
      throw new IllegalArgumentException("Invalid GCS URI: Input is null or empty.");
    }

    Pattern pattern = Pattern.compile("gs://([^/]*)/(.*)");
    Matcher matcher = pattern.matcher(gcsUri);

    if (matcher.matches()) {
      String bucketName = matcher.group(1);
      String objectPath = matcher.group(2);
      return new String[] {bucketName, objectPath};
    }
    throw new IllegalArgumentException("Invalid GCS URI: " + gcsUri);
  }
}
