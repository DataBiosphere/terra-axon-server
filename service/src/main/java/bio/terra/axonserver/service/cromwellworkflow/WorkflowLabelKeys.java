package bio.terra.axonserver.service.cromwellworkflow;

public enum WorkflowLabelKeys {
  WORKSPACE_ID_LABEL_KEY("terra-workspace-id"),
  USER_EMAIL_LABEL_KEY("terra-user-email"),
  GCS_SOURCE_LABEL_KEY("terra-gcs-source-uri");

  private final String key;

  WorkflowLabelKeys(String key) {
    this.key = key;
  }

  public String getKey() {
    return key;
  }
}
