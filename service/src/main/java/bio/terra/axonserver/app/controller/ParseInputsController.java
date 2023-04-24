package bio.terra.axonserver.app.controller;

import bio.terra.axonserver.api.ParseInputsApi;
import bio.terra.axonserver.model.ApiParsedInputsReport;
import bio.terra.axonserver.service.file.FileService;
import bio.terra.common.exception.BadRequestException;
import bio.terra.common.iam.BearerToken;
import bio.terra.common.iam.BearerTokenFactory;
import cromwell.core.path.DefaultPath;
import cromwell.core.path.DefaultPathBuilder;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
// Import the File class
// Import the IOException class to handle errors
import scala.util.Try;

@Controller
public class ParseInputsController extends ControllerBase implements ParseInputsApi {

  private final FileService fileService;

  @Autowired
  public ParseInputsController(
      BearerTokenFactory bearerTokenFactory, HttpServletRequest request, FileService fileService) {
    super(bearerTokenFactory, request);
    this.fileService = fileService;
  }

  @Override
  public ResponseEntity<ApiParsedInputsReport> parseInputs(UUID workspaceId) {
    BearerToken token = getToken();
    String accessToken = token.getToken();
    if (accessToken == null) {
      throw new BadRequestException("Access token is null. Try refreshing your access.");
    }
    String result = "{ \"fake\": \"inputs\"}";
    // File myObj = new File("helloWorld.wdl");
    Try<DefaultPath> pathTry = DefaultPathBuilder.build("helloWorld.wdl");
    ApiParsedInputsReport actualResult = new ApiParsedInputsReport().inputs(result);
    return new ResponseEntity<>(actualResult, HttpStatus.OK);
  }
}
