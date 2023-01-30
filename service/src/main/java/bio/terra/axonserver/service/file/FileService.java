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
import org.javatuples.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

@Component
public class FileService {

  private final WorkspaceManagerService wsmService;
  private final ConvertService convertService;

  @Autowired
  public FileService(WorkspaceManagerService wsmService, ConvertService convertService) {
    this.wsmService = wsmService;
    this.convertService = convertService;
  }

  public ByteArrayResource getFile(
      SamUser userRequest,
      UUID workspaceId,
      UUID resourceId,
      @Nullable String objectPath,
      @Nullable String convertTo) {

    ResourceDescription resource = this.getResource(userRequest, workspaceId, resourceId);

    Pair<byte[], String> fileWithName = this.getFileHandler(resource, objectPath, userRequest);
    byte[] file = fileWithName.getValue0();
    String fileName = fileWithName.getValue1();

    if (convertTo != null) {
      String fileExtension = FilenameUtils.getExtension(fileName);
      file = this.convertService.convert(file, fileExtension, convertTo, userRequest);
    }
    return new ByteArrayResource(file);
  }

  private Pair<byte[], String> getFileHandler(
      ResourceDescription resource, @Nullable String objectPath, SamUser userRequest) {

    return switch (resource.getMetadata().getResourceType()) {
      case GCS_OBJECT -> {
        String objectName = resource.getResourceAttributes().getGcpGcsObject().getFileName();
        yield new Pair<byte[], String>(this.getGcsObjectFile(resource, userRequest), objectName);
      }
      case GCS_BUCKET -> new Pair<byte[], String>(
          this.getGcsBucketFile(resource, objectPath, userRequest), objectPath);
      default -> throw new InvalidResourceTypeException(
          resource.getMetadata().getResourceType()
              + " is not a type of resource that contains files");
    };
  }

  private byte[] getGcsObjectFile(ResourceDescription resource, SamUser userRequest) {
    GoogleCredentials googleCredentials = GcpUtils.getGoogleCredentialsFromUserRequest(userRequest);

    String bucketName = resource.getResourceAttributes().getGcpGcsObject().getBucketName();
    String objectPath = resource.getResourceAttributes().getGcpGcsObject().getFileName();
    return new CloudStorageService()
        .getBucketObject(googleCredentials, bucketName, objectPath)
        .getContent();
  }

  private byte[] getGcsBucketFile(
      ResourceDescription resource, String objectPath, SamUser userRequest) {
    GoogleCredentials googleCredentials = GcpUtils.getGoogleCredentialsFromUserRequest(userRequest);

    String bucketName = resource.getResourceAttributes().getGcpGcsBucket().getBucketName();
    return new CloudStorageService()
        .getBucketObject(googleCredentials, bucketName, objectPath)
        .getContent();
  }

  private ResourceDescription getResource(SamUser userRequest, UUID workspaceId, UUID resourceId) {
    return this.wsmService.getResource(
        userRequest.getBearerToken().getToken(), workspaceId, resourceId);
  }
}
