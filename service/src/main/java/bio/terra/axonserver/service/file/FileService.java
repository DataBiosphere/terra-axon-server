package bio.terra.axonserver.service.file;

import bio.terra.axonserver.service.cloud.gcp.CloudStorageService;
import bio.terra.axonserver.service.convert.ConvertService;
import bio.terra.axonserver.service.exception.InvalidResourceTypeException;
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

  private final WorkspaceManagerService wsmService;
  private final ConvertService convertService;

  private record FileWithName(byte[] file, String fileName) {}

  @Autowired
  public FileService(WorkspaceManagerService wsmService, ConvertService convertService) {
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

    ResourceDescription resource = this.getResource(user, workspaceId, resourceId);

    FileWithName fileWithName = this.getFileHandler(resource, objectPath, user);
    byte[] file = fileWithName.file;
    if (convertTo != null) {
      String fileExtension = FilenameUtils.getExtension(fileWithName.fileName);
      file = this.convertService.convertFile(file, fileExtension, convertTo, user);
    }
    return new ByteArrayResource(file);
  }

  private FileWithName getFileHandler(
      ResourceDescription resource, @Nullable String objectPath, SamUser userRequest) {

    return switch (resource.getMetadata().getResourceType()) {
      case GCS_OBJECT -> this.getGcsObjectFile(resource, userRequest);
      case GCS_BUCKET -> this.getGcsBucketFile(resource, objectPath, userRequest);
      default -> throw new InvalidResourceTypeException(
          resource.getMetadata().getResourceType()
              + " is not a type of resource that contains files");
    };
  }

  private FileWithName getGcsObjectFile(ResourceDescription resource, SamUser userRequest) {
    GoogleCredentials googleCredentials =
        GcpUtils.getGoogleCredentialsFromToken(userRequest.getBearerToken().getToken());

    String bucketName = resource.getResourceAttributes().getGcpGcsObject().getBucketName();
    String objectPath = resource.getResourceAttributes().getGcpGcsObject().getFileName();
    byte[] file =
        new CloudStorageService().getBucketObject(googleCredentials, bucketName, objectPath);
    return new FileWithName(file, objectPath);
  }

  private FileWithName getGcsBucketFile(
      ResourceDescription resource, String objectPath, SamUser userRequest) {
    GoogleCredentials googleCredentials =
        GcpUtils.getGoogleCredentialsFromToken(userRequest.getBearerToken().getToken());

    String bucketName = resource.getResourceAttributes().getGcpGcsBucket().getBucketName();
    byte[] file =
        new CloudStorageService().getBucketObject(googleCredentials, bucketName, objectPath);
    return new FileWithName(file, objectPath);
  }

  private ResourceDescription getResource(SamUser userRequest, UUID workspaceId, UUID resourceId) {
    return this.wsmService.getResource(
        userRequest.getBearerToken().getToken(), workspaceId, resourceId);
  }
}
