package bio.terra.axonserver.service.convert;

import bio.terra.axonserver.service.calhoun.CalhounService;
import bio.terra.axonserver.service.exception.InvalidConvertToFormat;
import bio.terra.common.iam.SamUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Service for converting cloud files. */
@Component
public class ConvertService {
  // Calhoun is a service that converts .ipynb and .rmd files to .html
  private final CalhounService calhounService;

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
   * @param user The user requesting the conversion
   * @return The converted file
   * @throws InvalidConvertToFormat If the convertTo format is not supported
   */
  public byte[] convertFile(byte[] file, String fileExtension, String convertTo, SamUser user) {

    if (convertTo.equalsIgnoreCase("html")) {
      return convertToHtml(file, fileExtension, user);
    }
    throw new InvalidConvertToFormat("Invalid convertTo format: " + convertTo);
  }

  /**
   * Converts a file to html. Routes to the appropriate conversion service based on the given input
   * file extension.
   *
   * @param file The file to convert
   * @param fileExtension The extension of the file to convert
   * @param user The user requesting the conversion
   * @return The converted file
   * @throws InvalidConvertToFormat If the file extension is not supported
   */
  private byte[] convertToHtml(byte[] file, String fileExtension, SamUser user) {
    return switch (fileExtension) {
      case "ipynb" -> calhounService.convertNotebook(user.getBearerToken().getToken(), file);
      case "rmd" -> calhounService.convertRmd(user.getBearerToken().getToken(), file);
      default -> throw new InvalidConvertToFormat(
          "Unsupported file conversion: Cannot convert " + fileExtension + " to html");
    };
  }
}
