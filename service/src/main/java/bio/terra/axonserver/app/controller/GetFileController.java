package bio.terra.axonserver.app.controller;

import bio.terra.axonserver.api.GetFileApi;
import bio.terra.axonserver.service.file.FileService;
import bio.terra.axonserver.service.iam.AuthenticatedUserRequest;
import bio.terra.axonserver.service.iam.AuthenticatedUserRequestFactory;
import java.util.UUID;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class GetFileController extends ControllerBase implements GetFileApi {

  private final FileService fileService;

  @Autowired
  public GetFileController(
      AuthenticatedUserRequestFactory authenticatedUserRequestFactory,
      HttpServletRequest request,
      FileService fileService) {
    super(authenticatedUserRequestFactory, request);
    this.fileService = fileService;
  }

  @Override
  public ResponseEntity<Resource> getFile(
      UUID workspaceId, UUID resourceId, @Nullable String convertTo) {

    AuthenticatedUserRequest userRequest = this.getAuthenticatedInfo();

    ByteArrayResource resourceObj =
        this.fileService.getFile(userRequest, workspaceId, resourceId, null, convertTo);
    return new ResponseEntity<>(resourceObj, HttpStatus.OK);
  }

  @Override
  public ResponseEntity<Resource> getFileInBucket(
      UUID workspaceId, UUID resourceId, String objectPath, @Nullable String convertTo) {

    AuthenticatedUserRequest userRequest = this.getAuthenticatedInfo();

    Resource resourceObj =
        this.fileService.getFile(userRequest, workspaceId, resourceId, objectPath, convertTo);
    return new ResponseEntity<>(resourceObj, HttpStatus.OK);
  }
}
