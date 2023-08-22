package bio.terra.axonserver.utils.dataproc;

import bio.terra.axonserver.model.ApiClusterInstanceGroupConfig;
import bio.terra.axonserver.model.ApiClusterLifecycleConfig;
import bio.terra.axonserver.model.ApiClusterSoftwareConfig;
import com.google.api.services.dataproc.model.InstanceGroupConfig;
import com.google.api.services.dataproc.model.LifecycleConfig;
import com.google.api.services.dataproc.model.SoftwareConfig;
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
                      .machineType(config.getMachineTypeUri())
                      .bootDiskSizeGb(config.getDiskConfig().getBootDiskSizeGb());

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

  // Build an api cluster software config from a dataproc cluster software config
  public static ApiClusterSoftwareConfig buildSoftwareConfig(SoftwareConfig softwareConfig) {
    return Optional.ofNullable(softwareConfig)
        .map(
            config ->
                new ApiClusterSoftwareConfig()
                    .imageVersion(config.getImageVersion())
                    .optionalComponents(config.getOptionalComponents()))
        .orElse(null);
  }
}
