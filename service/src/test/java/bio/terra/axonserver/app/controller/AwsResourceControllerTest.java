package bio.terra.axonserver.app.controller;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bio.terra.axonserver.model.ApiSignedUrlReport;
import bio.terra.axonserver.service.cloud.aws.AwsService;
import bio.terra.axonserver.service.exception.InvalidResourceTypeException;
import bio.terra.axonserver.service.wsm.WorkspaceManagerService;
import bio.terra.axonserver.testutils.BaseUnitTest;
import bio.terra.common.exception.InternalServerErrorException;
import bio.terra.common.exception.NotFoundException;
import bio.terra.common.flagsmith.FlagsmithService;
import bio.terra.workspace.model.AwsCredential;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.ResourceDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URL;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

public class AwsResourceControllerTest extends BaseUnitTest {
  @MockBean private FlagsmithService flagsmithService;
  @MockBean private WorkspaceManagerService wsmService;
  @MockBean private AwsService awsService;

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  private final String featureFlag = "terra__aws_enabled";

  private final UUID workspaceId = UUID.randomUUID();
  private final UUID resourceId = UUID.randomUUID();
  private final String fakeToken = "faketoken";
  private final String path =
      String.format(
          "/api/workspaces/v1/%s/resources/%s/aws/consoleUrl",
          workspaceId.toString(), resourceId.toString());

  @Test
  void getSignedConsoleUrl_awsOn() throws Exception {

    Mockito.when(flagsmithService.isFeatureEnabled(featureFlag)).thenReturn(Optional.of(true));

    ResourceDescription mockResourceDescription = mock(ResourceDescription.class);

    // Expect the provided fake token will be passed to getResource, and return our mocked resource.

    Mockito.when(wsmService.getResource(fakeToken, workspaceId, resourceId))
        .thenReturn(mockResourceDescription);

    // Expect the provided fake token and WS ID will be passed to getHighestRole, return a canned
    // highest role.

    IamRole iamRole = IamRole.WRITER;
    Mockito.when(wsmService.getHighestRole(fakeToken, workspaceId)).thenReturn(iamRole);

    // Expect the provided fake token, mock resource, cannd IAM role, and any duration will be
    // passed to getAwsResourceCredential and return a mocked credential.  We will verify passed
    // duration was in range after the fact.

    AwsCredential mockCredential = mock(AwsCredential.class);
    Mockito.when(
            wsmService.getAwsResourceCredential(
                eq(fakeToken),
                eq(mockResourceDescription),
                eq(WorkspaceManagerService.inferAwsCredentialAccessScope(iamRole)),
                any()))
        .thenReturn(mockCredential);

    URL fakeUrl = new URL("https://example.com");

    // Expect the mocked resource, cred, and any duration are passed to createSignedConsoleUrl, and
    // return our fake URL.  We will verify passed duration was in range after the fact.

    Mockito.when(
            awsService.createSignedConsoleUrl(
                eq(mockResourceDescription), eq(mockCredential), any()))
        .thenReturn(fakeUrl);

    // Verify that the response was OK and we got our fake URL back.

    String serializedGetResponse =
        mockMvc
            .perform(get(path).header("Authorization", String.format("bearer %s", fakeToken)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    ApiSignedUrlReport apiSignedUrlReport =
        objectMapper.readValue(serializedGetResponse, ApiSignedUrlReport.class);

    assertEquals(fakeUrl.toString(), apiSignedUrlReport.getSignedUrl());

    ArgumentCaptor<Integer> integerArgumentCaptor = ArgumentCaptor.forClass(Integer.class);

    // Verify that credential duration requested from WSM was within range.

    verify(wsmService)
        .getAwsResourceCredential(any(), any(), any(), integerArgumentCaptor.capture());

    assertThat(
        integerArgumentCaptor.getValue(),
        greaterThanOrEqualTo(WorkspaceManagerService.AWS_RESOURCE_CREDENTIAL_DURATION_MIN));
    assertThat(
        integerArgumentCaptor.getValue(),
        lessThanOrEqualTo(WorkspaceManagerService.AWS_RESOURCE_CREDENTIAL_DURATION_MAX));

    // Verify that credential duration requested from the console federation endpoint was within
    // range.

    verify(awsService).createSignedConsoleUrl(any(), any(), integerArgumentCaptor.capture());

    assertThat(
        integerArgumentCaptor.getValue(),
        greaterThanOrEqualTo(AwsService.MIN_CONSOLE_SESSION_DURATION));
    assertThat(
        integerArgumentCaptor.getValue(),
        lessThanOrEqualTo(AwsService.MAX_CONSOLE_SESSION_DURATION));
  }

  @Test
  void getSignedConsoleUrl_awsOff() throws Exception {
    Mockito.when(flagsmithService.isFeatureEnabled(featureFlag)).thenReturn(Optional.of(false));
    mockMvc.perform(get(path)).andExpect(status().isNotImplemented());
    Mockito.verifyNoInteractions(wsmService, awsService);
  }

  @Test
  void getSignedConsoleUrl_resourceNotFound() throws Exception {
    Mockito.when(flagsmithService.isFeatureEnabled(featureFlag)).thenReturn(Optional.of(true));
    Mockito.when(wsmService.getResource(fakeToken, workspaceId, resourceId))
        .thenThrow(new NotFoundException("not found"));
    mockMvc
        .perform(get(path).header("Authorization", String.format("bearer %s", fakeToken)))
        .andExpect(status().isNotFound());
  }

  @Test
  void getSignedConsoleUrl_internalError() throws Exception {
    Mockito.when(flagsmithService.isFeatureEnabled(featureFlag)).thenReturn(Optional.of(true));
    Mockito.when(wsmService.getResource(fakeToken, workspaceId, resourceId))
        .thenThrow(new InternalServerErrorException("internal error"));
    mockMvc
        .perform(get(path).header("Authorization", String.format("bearer %s", fakeToken)))
        .andExpect(status().isInternalServerError());
  }

  @Test
  void getSignedConsoleUrl_badResourceType() throws Exception {
    Mockito.when(flagsmithService.isFeatureEnabled(featureFlag)).thenReturn(Optional.of(true));

    ResourceDescription mockResourceDescription = mock(ResourceDescription.class);

    IamRole highestRole = IamRole.WRITER;
    Mockito.when(wsmService.getResource(fakeToken, workspaceId, resourceId))
        .thenReturn(mockResourceDescription);
    Mockito.when(wsmService.getHighestRole(fakeToken, workspaceId)).thenReturn(highestRole);
    Mockito.when(
            wsmService.getAwsResourceCredential(
                eq(fakeToken),
                eq(mockResourceDescription),
                eq(WorkspaceManagerService.inferAwsCredentialAccessScope(highestRole)),
                any()))
        .thenThrow(new InvalidResourceTypeException("invalid resource type"));

    mockMvc
        .perform(get(path).header("Authorization", String.format("bearer %s", fakeToken)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getSignedConsoleUrl_unauthorized() throws Exception {
    Mockito.when(flagsmithService.isFeatureEnabled(featureFlag)).thenReturn(Optional.of(true));

    ResourceDescription mockResourceDescription = mock(ResourceDescription.class);

    IamRole highestRole = IamRole.DISCOVERER;
    Mockito.when(wsmService.getResource(fakeToken, workspaceId, resourceId))
        .thenReturn(mockResourceDescription);
    Mockito.when(wsmService.getHighestRole(fakeToken, workspaceId)).thenReturn(highestRole);

    mockMvc
        .perform(get(path).header("Authorization", String.format("bearer %s", fakeToken)))
        .andExpect(status().isForbidden());
  }
}
