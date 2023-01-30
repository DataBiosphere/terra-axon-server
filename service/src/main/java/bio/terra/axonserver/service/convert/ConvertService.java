package bio.terra.axonserver.service.convert;

import bio.terra.axonserver.service.calhoun.CalhounService;
import bio.terra.axonserver.service.exception.InvalidConvertToFormat;
import bio.terra.common.iam.SamUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ConvertService {

  private final CalhounService calhounService;

  @Autowired
  public ConvertService(CalhounService calhounService) {
    this.calhounService = calhounService;
  }

  public byte[] convert(byte[] file, String fileExtension, String convertTo, SamUser userRequest) {
    return switch (convertTo.toLowerCase()) {
      case "html" -> this.convertToHtml(file, fileExtension, userRequest);
      default -> throw new InvalidConvertToFormat("Invalid convertTo format: " + convertTo);
    };
  }

  private byte[] convertToHtml(byte[] file, String fileExtension, SamUser userRequest) {
    return switch (fileExtension) {
      case "ipynb" -> this.calhounService.convertNotebook(
          userRequest.getBearerToken().getToken(), file);
      case "rmd" -> this.calhounService.convertRmd(userRequest.getBearerToken().getToken(), file);
      default -> throw new InvalidConvertToFormat(
          "Unsupported file conversion: Cannot convert " + fileExtension + " to html");
    };
  }
}
