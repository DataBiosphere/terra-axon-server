package bio.terra.axonserver.service.calhoun;

import bio.terra.axonserver.app.configuration.CalhounConfiguration;
import bio.terra.calhoun.api.ConvertApi;
import bio.terra.calhoun.client.ApiClient;
import bio.terra.calhoun.client.ApiException;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.InternalServerErrorException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Service for interacting with Calhoun notebook conversion service. <a
 * href="https://github.com/DataBiosphere/calhoun">Calhoun Repo</a>
 */
@Component
public class CalhounService {

  private final CalhounConfiguration calhounConfig;

  @Autowired
  public CalhounService(CalhounConfiguration calhounConfig) {
    this.calhounConfig = calhounConfig;
  }

  private ApiClient getApiClient(String accessToken) {
    ApiClient apiClient = getApiClient();
    apiClient.setAccessToken(accessToken);
    return apiClient;
  }

  private ApiClient getApiClient() {
    return new ApiClient().setBasePath(calhounConfig.basePath());
  }

  /**
   * Convert a .ipynb jupyter notebook file to a html file.
   *
   * @param accessToken user access token
   * @param fileStream notebook file to convert
   * @return converted notebook file
   * @throws BadRequestException if conversion fails
   */
  public InputStream convertNotebook(String accessToken, InputStream fileStream) {
    try {
      File convertedFile = new ConvertApi(getApiClient(accessToken)).convertNotebook(fileStream);
      try {
        return new FileInputStream(convertedFile);
      } catch (Exception e) {
        throw new InternalServerErrorException("Failed to parse converted notebook");
      }
    } catch (ApiException apiException) {
      throw new BadRequestException("Failed to convert notebook");
    }
  }

  /**
   * Convert a .rmd R markdown file to a html file.
   *
   * @param accessToken user access token
   * @param fileStream notebook file to convert
   * @return converted notebook file
   * @throws BadRequestException if conversion fails
   */
  public InputStream convertRmd(String accessToken, InputStream fileStream) {
    try {
      File convertedFile = new ConvertApi(getApiClient(accessToken)).convertRmd(fileStream);
      try {
        return new FileInputStream(convertedFile);
      } catch (Exception e) {
        throw new InternalServerErrorException("Failed to parse converted R markdown");
      }
    } catch (ApiException apiException) {
      throw new BadRequestException("Failed to convert R markdown");
    }
  }
}
