package bio.terra.axonserver.utils;

import bio.terra.axonserver.model.ApiSubmitWorkflowRequestBodyWorkflowOptions;
import java.util.HashMap;
import java.util.Map;

public class WorkflowOptionsConverter {
  public static Map<String, String> convertToMap(
      ApiSubmitWorkflowRequestBodyWorkflowOptions workflowOptions) {
    Map<String, String> map = new HashMap<>();
    map.put("jes_gcs_root", workflowOptions.getJesGcsRoot());
    map.put("delete_intermediate_output_files", workflowOptions.getDeleteIntermediateOutputFiles());
    map.put("memory_retry_multiplier", workflowOptions.getMemoryRetryMultiplier());
    map.put("write_to_cache", workflowOptions.getWriteToCache());
    map.put("read_from_cache", workflowOptions.getReadFromCache());
    map.put("final_workflow_log_dir", workflowOptions.getFinalWorkflowLogDir());
    map.put("final_call_logs_dir", workflowOptions.getFinalCallLogsDir());
    return map;
  }
}
