package bio.terra.axonserver.service.calhoun;

import bio.terra.axonserver.app.configuration.CalhounConfiguration;
import bio.terra.calhoun.api.ConvertApi;
import bio.terra.calhoun.client.ApiClient;
import bio.terra.calhoun.client.ApiException;
import java.io.File;
import java.nio.file.Files;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.InternalServerErrorException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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

  public byte[] convertNotebook(String accessToken, byte[] file) {
    try {
      File convertedFile = new ConvertApi(getApiClient(accessToken)).convertNotebook(file);
      try {
        return Files.readAllBytes(convertedFile.toPath());
      } catch (Exception e) {
        throw new InternalServerErrorException("Failed to parse converted notebook");
      }
    } catch (ApiException apiException) {
      throw new BadRequestException("Failed to convert notebook");
    }
  }

  public byte[] convertRmd(String accessToken, byte[] file) {
    try {
      File convertedFile = new ConvertApi(getApiClient(accessToken)).convertRmd(file);
      try {
        return Files.readAllBytes(convertedFile.toPath());
      } catch (Exception e) {
        throw new InternalServerErrorException("Failed to read converted R markdown");
      }
    } catch (ApiException apiException) {
      throw new BadRequestException("Failed to convert R markdown");
    }
  }
}
