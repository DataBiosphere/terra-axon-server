package bio.terra.axonserver.utils.dataproc;

import bio.terra.axonserver.model.ApiClusterInstanceGroupConfig;
import bio.terra.axonserver.model.ApiClusterLifecycleConfig;
import com.google.api.services.dataproc.model.InstanceGroupConfig;
import com.google.api.services.dataproc.model.LifecycleConfig;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/** Utility class to build dataproc cluster metadata objects */
public class DataprocMetadataBuilderUtils {
  // Build an api cluster instance group config from a dataproc cluster instance group config
  public static ApiClusterInstanceGroupConfig buildInstanceGroupConfig(
      InstanceGroupConfig instanceGroupConfig) {
    return Optional.ofNullable(instanceGroupConfig)
        .map(
            config -> {
              ApiClusterInstanceGroupConfig nodeGroupConfig =
                  new ApiClusterInstanceGroupConfig()
                      .numInstances(config.getNumInstances())
                      .machineType(config.getMachineTypeUri());

              Optional.ofNullable(config.getPreemptibility())
                  .map(ApiClusterInstanceGroupConfig.PreemptibilityEnum::fromValue)
                  .ifPresent(nodeGroupConfig::preemptibility);

              return nodeGroupConfig;
            })
        .orElse(null);
  }

  // Build an api cluster lifecycle config from a dataproc cluster lifecycle config
  public static ApiClusterLifecycleConfig buildLifecycleConfig(LifecycleConfig lifecycleConfig) {
    return Optional.ofNullable(lifecycleConfig)
        .map(
            config ->
                new ApiClusterLifecycleConfig()
                    .idleDeleteTtl(config.getIdleDeleteTtl())
                    .idleStartTime(
                        Optional.ofNullable(lifecycleConfig.getIdleStartTime())
                            .map(Instant::parse)
                            .map(Date::from)
                            .orElse(null))
                    .autoDeleteTtl(config.getAutoDeleteTtl())
                    .autoDeleteTime(
                        Optional.ofNullable(lifecycleConfig.getAutoDeleteTime())
                            .map(Instant::parse)
                            .map(Date::from)
                            .orElse(null)))
        .orElse(null);
  }
}
