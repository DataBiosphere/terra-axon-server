package bio.terra.axonserver.service.file;

import bio.terra.axonserver.service.cloud.gcp.CloudStorageService;
import bio.terra.axonserver.service.convert.ConvertService;
import bio.terra.axonserver.service.exception.InvalidResourceTypeException;
import bio.terra.axonserver.service.iam.AuthenticatedUserRequest;
import bio.terra.axonserver.service.wsm.WorkspaceManagerService;
import bio.terra.axonserver.utils.GcpUtils;
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

  WorkspaceManagerService wsmService;
  ConvertService convertService;

  @Autowired
  public FileService(WorkspaceManagerService wsmService, ConvertService convertService) {
    this.wsmService = wsmService;
    this.convertService = convertService;
  }

  public ByteArrayResource getFile(
      AuthenticatedUserRequest userRequest,
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
      ResourceDescription resource,
      @Nullable String objectPath,
      AuthenticatedUserRequest userRequest) {

    return switch (resource.getMetadata().getResourceType()) {
      case GCS_OBJECT -> {
        String objectName = resource.getResourceAttributes().getGcpGcsObject().getFileName();
        yield new Pair<byte[], String>(this.getGcsObjectFile(resource, userRequest), objectName);
      }
      case GCS_BUCKET -> new Pair<byte[], String>(
          this.getGcsBucketFile(resource, objectPath, userRequest), objectPath);
      default -> throw new InvalidResourceTypeException(
          "Not a file containing resource: " + resource.getMetadata().getResourceType());
    };
  }

  private byte[] getGcsObjectFile(
      ResourceDescription resource, AuthenticatedUserRequest userRequest) {
    GoogleCredentials googleCredentials = GcpUtils.getGoogleCredentialsFromUserRequest(userRequest);

    String bucketName = resource.getResourceAttributes().getGcpGcsObject().getBucketName();
    String objectPath = resource.getResourceAttributes().getGcpGcsObject().getFileName();
    return new CloudStorageService()
        .getBucketObject(googleCredentials, bucketName, objectPath)
        .getContent();
  }

  private byte[] getGcsBucketFile(
      ResourceDescription resource, String objectPath, AuthenticatedUserRequest userRequest) {
    GoogleCredentials googleCredentials = GcpUtils.getGoogleCredentialsFromUserRequest(userRequest);

    String bucketName = resource.getResourceAttributes().getGcpGcsBucket().getBucketName();
    return new CloudStorageService()
        .getBucketObject(googleCredentials, bucketName, objectPath)
        .getContent();
  }

  private ResourceDescription getResource(
      AuthenticatedUserRequest userRequest, UUID workspaceId, UUID resourceId) {
    return this.wsmService.getResource(userRequest.getRequiredToken(), workspaceId, resourceId);
  }
}
