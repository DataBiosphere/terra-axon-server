package bio.terra.axonserver.service.cromwellworkflow;

public enum WorkflowOptionKeys {
  JES_GCS_ROOT("jes_gcs_root"),
  USER_SERVICE_ACCOUNT_JSON("user_service_account_json"),
  CALL_CACHE_HIT_PATH_PREFIXES("call_cache_hit_path_prefixes"),
  GOOGLE_PROJECT("google_project"),
  GOOGLE_COMPUTE_SERVICE_ACCOUNT("google_compute_service_account"),
  DEFAULT_RUNTIME_ATTRIBUTES("default_runtime_attributes");

  private final String key;

  WorkflowOptionKeys(String key) {
    this.key = key;
  }

  public String getKey() {
    return key;
  }
}
