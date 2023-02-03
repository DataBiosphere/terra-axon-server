package bio.terra.axonserver.service.file;

import bio.terra.axonserver.service.cloud.gcp.CloudStorageService;
import bio.terra.axonserver.service.convert.ConvertService;
import bio.terra.axonserver.service.exception.InvalidResourceTypeException;
import bio.terra.axonserver.service.iam.SamService;
import bio.terra.axonserver.service.wsm.WorkspaceManagerService;
import bio.terra.axonserver.utils.GcpUtils;
import bio.terra.common.iam.SamUser;
import bio.terra.workspace.model.ResourceDescription;
import com.google.auth.oauth2.GoogleCredentials;
import java.util.UUID;
import javax.annotation.Nullable;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

/**
 * Service for getting a cloud file from a given terra controlled resource. Optionally converts the
 * file to a desired format.
 */
@Component
public class FileService {

  private final SamService samService;
  private final WorkspaceManagerService wsmService;
  private final ConvertService convertService;

  private record FileWithName(byte[] file, String fileName) {}

  @Autowired
  public FileService(
      SamService samService, WorkspaceManagerService wsmService, ConvertService convertService) {
    this.samService = samService;
    this.wsmService = wsmService;
    this.convertService = convertService;
  }

  /**
   * Gets a file from a given resource. Optionally converts the file to a desired format.
   *
   * @param user The SAM user requesting the file
   * @param workspaceId The workspace that the resource is in
   * @param resourceId The id of the resource that the object is in
   * @param objectPath The path to the object in the bucket. Only used if the resource is a bucket.
   * @param convertTo The format to convert the file to. If null, the file is not converted.
   * @return The file as a byte array
   */
  public ByteArrayResource getFile(
      SamUser user,
      UUID workspaceId,
      UUID resourceId,
      @Nullable String objectPath,
      @Nullable String convertTo) {

    ResourceDescription resource =
        wsmService.getResource(user.getBearerToken().getToken(), workspaceId, resourceId);

    FileWithName fileWithName = getFileHandler(workspaceId, resource, objectPath, user);
    byte[] file = fileWithName.file;
    if (convertTo != null) {
      String fileExtension = FilenameUtils.getExtension(fileWithName.fileName);
      file = convertService.convertFile(file, fileExtension, convertTo, user);
    }
    return new ByteArrayResource(file);
  }

  private FileWithName getFileHandler(
      UUID workspaceId, ResourceDescription resource, @Nullable String objectPath, SamUser user) {

    return switch (resource.getMetadata().getResourceType()) {
      case GCS_OBJECT -> getGcsObjectFile(workspaceId, resource, objectPath, user);
      case GCS_BUCKET -> getGcsBucketFile(workspaceId, resource, objectPath, user);
      default -> throw new InvalidResourceTypeException(
          resource.getMetadata().getResourceType()
              + " is not a type of resource that contains files");
    };
  }

  private FileWithName getGcsObjectFile(
      UUID workspaceId, ResourceDescription resource, @Nullable String objectPath, SamUser user) {
    GoogleCredentials googleCredentials = getGoogleCredentials(workspaceId, user);

    String bucketName = resource.getResourceAttributes().getGcpGcsObject().getBucketName();
    // If objectPath is not provided, assume provided gcsObject is a prefix and retrieve the full
    // objectPath from the resource
    // If objectPath is not provided, assume provided gcsObject is a full path and use it
    if (objectPath == null) {
      objectPath = resource.getResourceAttributes().getGcpGcsObject().getFileName();
    }

    byte[] file =
        new CloudStorageService().getBucketObject(googleCredentials, bucketName, objectPath);
    return new FileWithName(file, objectPath);
  }

  private FileWithName getGcsBucketFile(
      UUID workspaceId, ResourceDescription resource, String objectPath, SamUser user) {
    GoogleCredentials googleCredentials = getGoogleCredentials(workspaceId, user);

    String bucketName = resource.getResourceAttributes().getGcpGcsBucket().getBucketName();
    byte[] file =
        new CloudStorageService().getBucketObject(googleCredentials, bucketName, objectPath);
    return new FileWithName(file, objectPath);
  }

  private GoogleCredentials getGoogleCredentials(UUID workspaceId, SamUser user) {
    String projectId =
        wsmService.getGcpContext(workspaceId, user.getBearerToken().getToken()).getProjectId();
    String petAccessToken = samService.getPetAccessToken(projectId, user.getBearerToken());
    return GcpUtils.getGoogleCredentialsFromToken(petAccessToken);
  }
}
