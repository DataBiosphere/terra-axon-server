package bio.terra.axonserver.app.controller;

import bio.terra.axonserver.api.GetFileApi;
import bio.terra.axonserver.service.file.FileService;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.iam.BearerToken;
import bio.terra.common.iam.BearerTokenFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.InternalServerErrorException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
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
      BearerTokenFactory bearerTokenFactory, HttpServletRequest request, FileService fileService) {
    super(bearerTokenFactory, request);
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
    return buildResponse(workspaceId, resourceId, null, convertTo);
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
    return buildResponse(workspaceId, resourceId, objectPath, convertTo);
  }

  private ResponseEntity<Resource> buildResponse(
      UUID workspaceId, UUID resourceId, @Nullable String objectPath, @Nullable String convertTo) {
    BearerToken token = getToken();

    HttpRange byteRange = getByteRange();

    File resourceObj =
        fileService.getFile(token, workspaceId, resourceId, objectPath, convertTo, byteRange);
    String contentType =
        convertTo == null
            ? URLConnection.guessContentTypeFromName(resourceObj.getPath())
            : URLConnection.guessContentTypeFromName((resourceObj.getName() + "." + convertTo));

    HttpStatus resStatus = byteRange == null ? HttpStatus.OK : HttpStatus.PARTIAL_CONTENT;
    HttpHeaders resHeaders = new HttpHeaders();
    resHeaders.set(HttpHeaders.CONTENT_TYPE, contentType);

    try {
      InputStream inputStream = new FileInputStream(resourceObj);

      return new ResponseEntity<>(new InputStreamResource(inputStream), resHeaders, resStatus);
    } catch (IOException e) {
      throw new InternalServerErrorException("Failed to read: " + resourceObj.getName());
    }
  }

  private HttpRange getByteRange() {
    String rangeHeader = getServletRequest().getHeader("Range");
    if (rangeHeader == null) {
      return null;
    }

    List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
    if (ranges.size() != 1) {
      throw new BadRequestException("Only one range is supported");
    }
    return ranges.get(0);
  }
}
