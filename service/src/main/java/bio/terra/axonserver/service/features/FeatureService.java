package bio.terra.axonserver.service.features;

import bio.terra.axonserver.service.exception.FeatureNotEnabledException;
import bio.terra.common.flagsmith.FlagsmithService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FeatureService {
  private final FlagsmithService flagsmithService;

  @Autowired
  FeatureService(FlagsmithService flagsmithService) {
    this.flagsmithService = flagsmithService;
  }

  public boolean isFeatureEnabled(String featureName) {
    return flagsmithService.isFeatureEnabled(featureName).orElse(false);
  }

  public void featureEnabledCheck(String featureName) {
    if (!isFeatureEnabled(featureName)) {
      throw new FeatureNotEnabledException(String.format("Feature %s not supported", featureName));
    }
  }
}
