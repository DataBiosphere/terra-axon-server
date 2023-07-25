package bio.terra.axonserver.service.file;

import bio.terra.axonserver.app.configuration.FileConfiguration;
import bio.terra.axonserver.service.cloud.aws.GcpService;
import bio.terra.axonserver.service.convert.ConvertService;
import bio.terra.axonserver.service.exception.InvalidResourceTypeException;
import bio.terra.axonserver.service.iam.SamService;
import bio.terra.axonserver.service.wsm.WorkspaceManagerService;
import bio.terra.axonserver.utils.CloudStorageUtils;
import bio.terra.common.iam.BearerToken;
import bio.terra.workspace.model.ResourceDescription;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import com.google.cloud.storage.StorageOptions;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpRange;
import org.springframework.stereotype.Component;

/**
 * Service for getting a cloud file from a given terra controlled resource. Optionally converts the
 * file to a desired format.
 */
@Component
public class FileService {

  private final FileConfiguration fileConfig;
  private final GcpService gcpService;
  private final SamService samService;
  private final WorkspaceManagerService wsmService;
  private final ConvertService convertService;

  private record FileWithName(InputStream fileStream, String fileName) {}

  @Autowired
  public FileService(
      FileConfiguration fileConfig,
      GcpService gcpService,
      SamService samService,
      WorkspaceManagerService wsmService,
      ConvertService convertService) {
    this.fileConfig = fileConfig;
    this.gcpService = gcpService;
    this.samService = samService;
    this.wsmService = wsmService;
    this.convertService = convertService;
  }

  /**
   * Gets a fileStream for a given resource. Optionally converts the file to a desired format.
   *
   * @param token Bearer token for the requester
   * @param workspaceId The workspace that the resource is in
   * @param resourceId The id of the resource that the object is in
   * @param objectPath The path to the object in the bucket. Only used if the resource is a bucket.
   * @param convertTo The format to convert the file to. If null, the file is not converted.
   * @param byteRange The range of bytes to return. If null, the entire file is returned.
   * @return The file as a byte array
   */
  public InputStream getFile(
      BearerToken token,
      UUID workspaceId,
      UUID resourceId,
      @Nullable String objectPath,
      @Nullable String convertTo,
      @Nullable HttpRange byteRange) {

    ResourceDescription resource =
        wsmService.getResource(workspaceId, resourceId, token.getToken());

    FileWithName fileWithName = getFileHandler(workspaceId, resource, objectPath, byteRange, token);
    InputStream fileStream = fileWithName.fileStream;
    if (convertTo != null) {
      String fileExtension = FilenameUtils.getExtension(fileWithName.fileName);
      fileStream = convertService.convertFile(fileStream, fileExtension, convertTo, token);
    }
    return fileStream;
  }

  /**
   * @param token Bearer token for the requester
   * @param workspaceId The workspace that the resource is in
   * @param gcsURI gs:// URI path to a file in a Google Cloud Storage bucket
   * @param convertTo The format to convert the file to. If null, the file is not converted.
   * @return
   */
  public InputStream getFile(
      BearerToken token, UUID workspaceId, String gcsURI, @Nullable String convertTo) {
    FileWithName fileWithName = getGcsObjectFromURI(workspaceId, gcsURI, token);
    InputStream fileStream = fileWithName.fileStream;
    if (convertTo != null) {
      String fileExtension = FilenameUtils.getExtension(fileWithName.fileName);
      fileStream = convertService.convertFile(fileStream, fileExtension, convertTo, token);
    }
    return fileStream;
  }

  /**
   * Generate a V4 signed URL using the Google application default credentials and pet service
   * account email.
   *
   * <p>See <a href="https://cloud.google.com/storage/docs/access-control/signed-urls">Signed
   * URLs</a> and <a
   * href="https://cloud.google.com/storage/docs/access-control/signing-urls-with-helpers#client-libraries">
   * V4 signing process with Cloud Storage tools</a>
   *
   * @param token Bearer token for the requester.
   * @param projectId The GCP project ID.
   * @param bucketName Name of the GCS bucket.
   * @param objectName Path to object in the bucket.
   * @return A signed URL giving download access for one hour.
   */
  public URL generateV4GetObjectSignedUrl(
      BearerToken token, String projectId, String bucketName, String objectName)
      throws StorageException, IOException {
    String petSaEmail = samService.getPetServiceAccount(projectId, token);
    ImpersonatedCredentials targetCredentials =
        ImpersonatedCredentials.create(
            GoogleCredentials.getApplicationDefault(),
            petSaEmail,
            null,
            GcpService.getPetScopes(),
            300);

    Storage storage =
        StorageOptions.newBuilder()
            .setProjectId(projectId)
            .setCredentials(targetCredentials)
            .build()
            .getService();

    BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, objectName)).build();
    return storage.signUrl(
        blobInfo,
        fileConfig.signedUrlExpirationMinutes(),
        TimeUnit.MINUTES,
        Storage.SignUrlOption.withV4Signature());
  }

  private FileWithName getFileHandler(
      UUID workspaceId,
      ResourceDescription resource,
      @Nullable String objectPath,
      @Nullable HttpRange byteRange,
      BearerToken token) {

    return switch (resource.getMetadata().getResourceType()) {
      case GCS_OBJECT -> getGcsObjectFile(workspaceId, resource, objectPath, byteRange, token);
      case GCS_BUCKET -> getGcsBucketFile(workspaceId, resource, objectPath, byteRange, token);
      default -> throw new InvalidResourceTypeException(
          resource.getMetadata().getResourceType()
              + " is not a type of resource that contains files");
    };
  }

  private FileWithName getGcsObjectFromURI(UUID workspaceId, String gcsURI, BearerToken token) {
    GoogleCredentials googleCredentials = gcpService.getPetSACredentials(workspaceId, token);

    BlobId blob = BlobId.fromGsUtilUri(gcsURI);

    InputStream fileStream =
        CloudStorageUtils.getBucketObject(
            googleCredentials, blob.getBucket(), blob.getName(), null);
    return new FileWithName(fileStream, blob.getName());
  }

  private FileWithName getGcsObjectFile(
      UUID workspaceId,
      ResourceDescription resource,
      @Nullable String objectPath,
      @Nullable HttpRange byteRange,
      BearerToken token) {
    GoogleCredentials googleCredentials = gcpService.getPetSACredentials(workspaceId, token);

    String bucketName = resource.getResourceAttributes().getGcpGcsObject().getBucketName();
    // If objectPath is not provided, assume provided gcsObject is a prefix and retrieve the full
    // objectPath from the resource
    // If objectPath is not provided, assume provided gcsObject is a full path and use it
    if (objectPath == null) {
      objectPath = resource.getResourceAttributes().getGcpGcsObject().getFileName();
    }

    InputStream fileStream =
        CloudStorageUtils.getBucketObject(googleCredentials, bucketName, objectPath, byteRange);
    return new FileWithName(fileStream, objectPath);
  }

  private FileWithName getGcsBucketFile(
      UUID workspaceId,
      ResourceDescription resource,
      String objectPath,
      @Nullable HttpRange byteRange,
      BearerToken token) {
    GoogleCredentials googleCredentials = gcpService.getPetSACredentials(workspaceId, token);

    String bucketName = resource.getResourceAttributes().getGcpGcsBucket().getBucketName();
    InputStream fileStream =
        CloudStorageUtils.getBucketObject(googleCredentials, bucketName, objectPath, byteRange);
    return new FileWithName(fileStream, objectPath);
  }
}
