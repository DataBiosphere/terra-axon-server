package bio.terra.axonserver.utils;

import bio.terra.axonserver.model.ApiSubmitWorkflowRequestBodyWorkflowOptions;
import java.util.HashMap;
import java.util.Map;

public class WorkflowOptionsConverter {
  public static Map<String, Object> convertToMap(
      ApiSubmitWorkflowRequestBodyWorkflowOptions workflowOptions) {
    Map<String, Object> map = new HashMap<>();

    if (workflowOptions != null) {
      addToMapIfNotNull(map, "jes_gcs_root", workflowOptions.getJesGcsRoot());
      addToMapIfNotNull(
          map,
          "delete_intermediate_output_files",
          workflowOptions.getDeleteIntermediateOutputFiles());
      addToMapIfNotNull(map, "memory_retry_multiplier", workflowOptions.getMemoryRetryMultiplier());
      addToMapIfNotNull(map, "write_to_cache", workflowOptions.getWriteToCache());
      addToMapIfNotNull(map, "read_from_cache", workflowOptions.getReadFromCache());
      addToMapIfNotNull(map, "final_workflow_log_dir", workflowOptions.getFinalWorkflowLogDir());
      addToMapIfNotNull(map, "final_call_logs_dir", workflowOptions.getFinalCallLogsDir());
    }

    return map;
  }

  private static void addToMapIfNotNull(Map<String, Object> map, String key, Object value) {
    if (value != null) {
      map.put(key, value);
    }
  }
}
