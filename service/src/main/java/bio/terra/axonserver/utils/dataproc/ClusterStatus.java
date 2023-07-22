package bio.terra.axonserver.utils.dataproc;

/**
 * Enum representing Cluster Status. See
 * https://cloud.google.com/dataproc/docs/reference/rest/v1/projects.regions.clusters#ClusterStatus
 */
public enum ClusterStatus {
  CREATING,
  DELETING,
  ERROR,
  ERROR_DUE_TO_UPDATE,
  RUNNING,
  STARTING,
  STATE_UNSPECIFIED,
  STOPPED,
  STOPPING,
  UNKNOWN,
  UPDATING
}
