package bio.terra.axonserver.service.convert;

import bio.terra.axonserver.service.calhoun.CalhounService;
import bio.terra.axonserver.service.exception.InvalidConvertToFormat;
import bio.terra.common.iam.SamUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Service for converting cloud files. */
@Component
public class ConvertService {

  private final CalhounService
      calhounService; // Calhoun is a service that converts .ipynb and .rmd files to .html

  @Autowired
  public ConvertService(CalhounService calhounService) {
    this.calhounService = calhounService;
  }

  /**
   * Converts a file to a different format. Routes to the appropriate conversion service based on
   * expected convertTo format.
   *
   * @param file The file to convert
   * @param fileExtension The extension of the file to convert
   * @param convertTo The format to convert the file to
   * @param userRequest The user requesting the conversion
   * @return The converted file
   * @throws InvalidConvertToFormat If the convertTo format is not supported
   */
  public byte[] convertFile(
      byte[] file, String fileExtension, String convertTo, SamUser userRequest) {
    return switch (convertTo.toLowerCase()) {
      case "html" -> this.convertToHtml(file, fileExtension, userRequest);
      default -> throw new InvalidConvertToFormat("Invalid convertTo format: " + convertTo);
    };
  }

  /**
   * Converts a file to html. Routes to the appropriate conversion service based on the given input
   * file extension.
   *
   * @param file The file to convert
   * @param fileExtension The extension of the file to convert
   * @param userRequest The user requesting the conversion
   * @return The converted file
   * @throws InvalidConvertToFormat If the file extension is not supported
   */
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
