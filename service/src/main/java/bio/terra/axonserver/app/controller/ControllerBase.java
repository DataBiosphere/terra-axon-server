package bio.terra.axonserver.app.controller;

import bio.terra.axonserver.app.configuration.SamConfiguration;
import bio.terra.common.iam.SamUser;
import bio.terra.common.iam.SamUserFactory;
import javax.servlet.http.HttpServletRequest;

/**
 * Super class for controllers containing common code. The code in here requires the @Autowired
 * beans from the @Controller classes, so it is better as a superclass rather than static methods.
 */
public class ControllerBase {

  private final SamConfiguration samConfiguration;
  private final SamUserFactory samUserFactory;
  private final HttpServletRequest request;

  public ControllerBase(
      SamConfiguration samConfiguration,
      SamUserFactory samUserFactory,
      HttpServletRequest request) {
    this.samConfiguration = samConfiguration;
    this.samUserFactory = samUserFactory;
    this.request = request;
  }

  public SamUser getUser() {
    return samUserFactory.from(request, samConfiguration.basePath());
  }
}
