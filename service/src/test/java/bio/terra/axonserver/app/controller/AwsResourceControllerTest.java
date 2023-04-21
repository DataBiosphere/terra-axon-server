package bio.terra.axonserver.app.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.axonserver.testutils.BaseUnitTest;
import bio.terra.common.flagsmith.FlagsmithService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

public class AwsResourceControllerTest extends BaseUnitTest {
  @MockBean private FlagsmithService flagsmithService;

  @Autowired private MockMvc mockMvc;

  private final UUID workspaceId = UUID.randomUUID();
  private final UUID resourceId = UUID.randomUUID();
  private final String path =
      String.format(
          "/api/workspaces/v1/%s/resources/%s/aws/consoleLink",
          workspaceId.toString(), resourceId.toString());

  @Test
  void awsOn() throws Exception {
    Mockito.when(flagsmithService.isFeatureEnabled("terra__aws_enabled"))
        .thenReturn(Optional.of(true));

    System.out.println(path);
    mockMvc.perform(get(path)).andExpect(status().isOk());
  }

  @Test
  void awsOff() throws Exception {
    Mockito.when(flagsmithService.isFeatureEnabled("terra__aws_enabled"))
        .thenReturn(Optional.of(false));

    mockMvc.perform(get(path)).andExpect(status().isNotImplemented());
  }
}
