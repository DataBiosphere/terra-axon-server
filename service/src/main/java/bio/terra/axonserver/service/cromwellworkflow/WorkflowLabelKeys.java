package bio.terra.axonserver.service.cromwellworkflow;

public enum WorkflowLabelKeys {
  WORKSPACE_ID_LABEL_KEY("terra-workspace-id"),
  USER_EMAIL_LABEL_KEY("terra-user-email"),

  WORKFLOW_SOURCE_URL_LABEL_KEY("terra-workflow-source-url"),

  // TODO: Deprecate in favor of just using workflow URL.
  // Will make change once UI is updated
  GCS_SOURCE_LABEL_KEY("terra-gcs-source-uri");

  private final String key;

  WorkflowLabelKeys(String key) {
    this.key = key;
  }

  public String getKey() {
    return key;
  }
}
