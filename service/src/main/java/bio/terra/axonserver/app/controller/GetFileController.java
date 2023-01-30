package bio.terra.axonserver.app.controller;

import bio.terra.axonserver.api.GetFileApi;
import bio.terra.axonserver.app.configuration.SamConfiguration;
import bio.terra.axonserver.service.file.FileService;
import bio.terra.common.iam.SamUser;
import bio.terra.common.iam.SamUserFactory;
import java.util.UUID;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

/**
 * Controller for the GetFileApi. This controller is responsible for handling all incoming requests
 * for files.
 */
@Controller
public class GetFileController extends ControllerBase implements GetFileApi {

  private final FileService fileService;

  @Autowired
  public GetFileController(
      SamConfiguration samConfiguration,
      SamUserFactory samUserFactory,
      HttpServletRequest request,
      FileService fileService) {
    super(samConfiguration, samUserFactory, request);
    this.fileService = fileService;
  }

  /**
   * Get a file from a workspace. This method is responsible for handling all requests to the
   * /api/workspaces/{workspaceId}/files/{resourceId} endpoint.
   *
   * @param workspaceId - UUID of the workspace to retrieve the file from
   * @param resourceId - UUID of the file to retrieve
   * @param convertTo - Optional parameter to convert the file to a different format
   * @return - A ResponseEntity containing the file
   */
  @Override
  public ResponseEntity<Resource> getFile(
      UUID workspaceId, UUID resourceId, @Nullable String convertTo) {

    SamUser user = this.getUser();

    ByteArrayResource resourceObj =
        this.fileService.getFile(user, workspaceId, resourceId, null, convertTo);
    return new ResponseEntity<>(resourceObj, HttpStatus.OK);
  }

  /**
   * Get a file from a bucket. This method is responsible for handling all requests to the
   * /api/workspaces/{workspaceId}/files/{resourceId}/{objectPath} endpoint.
   *
   * @param workspaceId - UUID of the workspace to retrieve the file from
   * @param resourceId - UUID of the bucket to retrieve the file from
   * @param objectPath - Path to the file in the bucket
   * @param convertTo - Optional parameter to convert the file to a different format
   * @return - A ResponseEntity containing the file
   */
  @Override
  public ResponseEntity<Resource> getFileInBucket(
      UUID workspaceId, UUID resourceId, String objectPath, @Nullable String convertTo) {

    SamUser user = this.getUser();

    Resource resourceObj =
        this.fileService.getFile(user, workspaceId, resourceId, objectPath, convertTo);
    return new ResponseEntity<>(resourceObj, HttpStatus.OK);
  }
}
