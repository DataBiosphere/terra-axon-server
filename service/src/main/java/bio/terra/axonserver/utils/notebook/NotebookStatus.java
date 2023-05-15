package bio.terra.axonserver.utils.notebook;

/**
 * Enum representing Notebook Status. This is a union of all the status values that can be returned
 * by Google and AWS notebooks, with some semantically redundant (but differently named) AWS status
 * values translated to Google equivalents.
 */
public enum NotebookStatus {
  ACTIVE,
  DELETED,
  DELETING,
  FAILED,
  INITIALIZING,
  PENDING,
  PROVISIONING,
  REGISTERING,
  STARTING,
  STATE_UNSPECIFIED,
  STOPPED,
  STOPPING,
  SUSPENDED,
  SUSPENDING,
  UPDATING,
  UPGRADING,
}
