package bio.terra.axonserver.service.features;

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

  public boolean awsEnabled() {
    return flagsmithService.isFeatureEnabled("terra__aws_enabled").orElse(false);
  }
}
