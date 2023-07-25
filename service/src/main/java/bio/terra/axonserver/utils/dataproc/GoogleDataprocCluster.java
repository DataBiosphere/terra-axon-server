package bio.terra.axonserver.utils.dataproc;

import bio.terra.axonserver.service.exception.ComponentNotFoundException;
import bio.terra.axonserver.utils.CloudStorageUtils;
import bio.terra.axonserver.utils.ResourceUtils;
import bio.terra.cloudres.common.ClientConfig;
import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.api.services.common.OperationUtils;
import bio.terra.cloudres.google.dataproc.ClusterName;
import bio.terra.cloudres.google.dataproc.DataprocCow;
import bio.terra.workspace.model.GcpDataprocClusterAttributes;
import bio.terra.workspace.model.ResourceDescription;
import bio.terra.workspace.model.ResourceType;
import com.google.api.services.dataproc.model.Cluster;
import com.google.api.services.dataproc.model.Operation;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import javax.ws.rs.InternalServerErrorException;

/** Utility class for running common dataproc cluster operations on a Google Dataproc cluster. */
public class GoogleDataprocCluster {

  private static final ClientConfig clientConfig =
      ClientConfig.Builder.newBuilder().setClient("terra-axon-server").build();
  private final ClusterName clusterName;
  private final DataprocCow dataprocCow;

  /** For testing use only, allows use of mock {@link DataprocCow}. */
  @VisibleForTesting
  public GoogleDataprocCluster(ClusterName clusterName, DataprocCow dataprocCow) {
    this.clusterName = clusterName;
    this.dataprocCow = dataprocCow;
  }

  private static ClusterName buildClusterName(GcpDataprocClusterAttributes attributes) {
    return ClusterName.builder()
        .projectId(attributes.getProjectId())
        .region(attributes.getRegion())
        .name(attributes.getClusterId())
        .build();
  }

  private GoogleDataprocCluster(ResourceDescription resource, String accessToken)
      throws GeneralSecurityException, IOException {
    this(
        buildClusterName(resource.getResourceAttributes().getGcpDataprocCluster()),
        DataprocCow.create(
            clientConfig, CloudStorageUtils.getGoogleCredentialsFromToken(accessToken)));
  }

  /** Factory method to create an instance of class {@link GoogleDataprocCluster}. */
  public static GoogleDataprocCluster create(
      ResourceDescription resourceDescription, String accessToken) {
    ResourceUtils.validateResourceType(ResourceType.DATAPROC_CLUSTER, resourceDescription);

    try {
      return new GoogleDataprocCluster(resourceDescription, accessToken);
    } catch (GeneralSecurityException | IOException e) {
      throw new InternalServerErrorException(e);
    }
  }

  /** Do not use, made public for test spies. */
  @VisibleForTesting
  public void pollForSuccess(Operation operation, String errorMessage) {
    OperationCow<Operation> operationCow = dataprocCow.regionOperations().operationCow(operation);

    try {
      operationCow =
          OperationUtils.pollUntilComplete(
              operationCow, Duration.ofSeconds(5), Duration.ofMinutes(3));
    } catch (InterruptedException | IOException e) {
      throw new InternalServerErrorException(errorMessage, e);
    }

    if (operationCow.getOperation().getError() != null) {
      throw new InternalServerErrorException(
          errorMessage + operationCow.getOperation().getError().getMessage());
    }
  }

  private static String getOperationErrorMessage(String operation) {
    return String.format("Cluster operation '%s' failed.", operation);
  }

  /**
   * Start the cluster.
   *
   * <p>If wait is true, the call blocks until the cluster has reached status {@link
   * ClusterStatus#RUNNING}. Otherwise. the call returns immediately, and caller may subsequently
   * check status by calling {@link #getStatus()}.
   *
   * @param wait if true, wait for operation to complete, otherwise return immediately.
   */
  public void start(boolean wait) {
    try {
      Operation startOperation = dataprocCow.clusters().start(clusterName).execute();
      if (wait) {
        pollForSuccess(startOperation, getOperationErrorMessage("start"));
      }
    } catch (IOException e) {
      throw new InternalServerErrorException(getOperationErrorMessage("start"), e);
    }
  }

  /**
   * Stop the cluster.
   *
   * <p>If wait is true, the call blocks until the cluster has reached status {@link
   * ClusterStatus#STOPPED}. Otherwise. the call returns immediately, and caller may subsequently
   * check status by calling {@link #getStatus()}.
   *
   * @param wait if true, wait for operation to complete, otherwise return immediately.
   */
  public void stop(boolean wait) {
    try {
      Operation stopOperation = dataprocCow.clusters().stop(clusterName).execute();
      if (wait) {
        pollForSuccess(stopOperation, getOperationErrorMessage("stop"));
      }
    } catch (IOException e) {
      throw new InternalServerErrorException(getOperationErrorMessage("stop"), e);
    }
  }

  private Cluster get(String operation) {
    try {
      return dataprocCow.clusters().get(clusterName).execute();
    } catch (IOException e) {
      throw new InternalServerErrorException(getOperationErrorMessage(operation), e);
    }
  }

  /**
   * Gets current status of the Dataproc cluster.
   *
   * @return current status
   */
  public ClusterStatus getStatus() {
    try {
      return ClusterStatus.valueOf(get("get status").getStatus().getState());
    } catch (IllegalArgumentException e) {
      return ClusterStatus.STATE_UNSPECIFIED;
    }
  }

  /**
   * Gets the proxy url for the cluster's Jupyter component.
   *
   * @return a URL providing access to the dataproc cluster jupyter lab on the manager node
   */
  public String getComponentUrl(String componentKey) {
    String componentUrl =
        get("get component url").getConfig().getEndpointConfig().getHttpPorts().get(componentKey);
    if (componentUrl == null) {
      throw new ComponentNotFoundException(
          String.format(
              "Unable to get web UI url for component %s, ensure that the component is enabled in the cluster configuration",
              componentKey));
    }
    return componentUrl;
  }
}
