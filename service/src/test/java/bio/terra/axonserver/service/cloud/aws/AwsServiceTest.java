package bio.terra.axonserver.service.cloud.aws;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import bio.terra.axonserver.service.exception.InvalidResourceTypeException;
import bio.terra.axonserver.testutils.BaseUnitTest;
import bio.terra.cloudres.aws.console.ConsoleCow;
import bio.terra.common.exception.InternalServerErrorException;
import bio.terra.workspace.model.AwsCredential;
import bio.terra.workspace.model.AwsS3StorageFolderAttributes;
import bio.terra.workspace.model.ControlledResourceMetadata;
import bio.terra.workspace.model.ResourceAttributesUnion;
import bio.terra.workspace.model.ResourceDescription;
import bio.terra.workspace.model.ResourceMetadata;
import bio.terra.workspace.model.ResourceType;
import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.model.Credentials;

public class AwsServiceTest extends BaseUnitTest {
  @Autowired private AwsService awsService;
  private final ConsoleCow mockConsoleCow = mock(ConsoleCow.class);
  private final ResourceDescription mockResourceDescription = mock(ResourceDescription.class);
  private final ResourceMetadata mockResourceMetadata = mock(ResourceMetadata.class);

  private final AwsCredential fakeAwsCredential =
      new AwsCredential()
          .accessKeyId("accesskeyid")
          .secretAccessKey("secretaccesskey")
          .sessionToken("sessiontoken");

  @Test
  void createSignedConsoleUrl_s3StorageFolder() throws IOException {

    // Chain together mocks to plumb resource type
    Mockito.when(mockResourceDescription.getMetadata()).thenReturn(mockResourceMetadata);
    Mockito.when(mockResourceMetadata.getResourceType())
        .thenReturn(ResourceType.AWS_S3_STORAGE_FOLDER);

    // Chain together mocks to plumb region
    Region region = Region.US_EAST_1;
    ControlledResourceMetadata mockControlledResourceMetadata =
        mock(ControlledResourceMetadata.class);
    Mockito.when(mockResourceMetadata.getControlledResourceMetadata())
        .thenReturn(mockControlledResourceMetadata);
    Mockito.when(mockControlledResourceMetadata.getRegion()).thenReturn(region.toString());

    // Chain together mocks to plumb bucket name and prefix
    String bucketName = "fakebucketname";
    String prefix = "fakeprefix";
    ResourceAttributesUnion mockResourceAttributes = mock(ResourceAttributesUnion.class);
    AwsS3StorageFolderAttributes mockS3FolderAttributes = mock(AwsS3StorageFolderAttributes.class);
    Mockito.when(mockResourceDescription.getResourceAttributes())
        .thenReturn(mockResourceAttributes);
    Mockito.when(mockResourceAttributes.getAwsS3StorageFolder()).thenReturn(mockS3FolderAttributes);
    Mockito.when(mockS3FolderAttributes.getBucketName()).thenReturn(bucketName);
    Mockito.when(mockS3FolderAttributes.getPrefix()).thenReturn(prefix);

    Mockito.when(mockConsoleCow.createSignedUrl(any(), any(), any()))
        .thenThrow(new IOException())
        .thenReturn(new URL("https://example.com"));

    // First call should throw
    assertThrows(
        InternalServerErrorException.class,
        () ->
            awsService.createSignedConsoleUrl(
                mockConsoleCow,
                mockResourceDescription,
                fakeAwsCredential,
                AwsService.MAX_CONSOLE_SESSION_DURATION));

    // Second call should succeed
    URL consoleUrl =
        awsService.createSignedConsoleUrl(
            mockConsoleCow,
            mockResourceDescription,
            fakeAwsCredential,
            AwsService.MAX_CONSOLE_SESSION_DURATION);

    // Verify passed parameters
    ArgumentCaptor<Credentials> credentialsArgumentCaptor =
        ArgumentCaptor.forClass(Credentials.class);
    ArgumentCaptor<Integer> integerArgumentCaptor = ArgumentCaptor.forClass(Integer.class);
    ArgumentCaptor<URL> urlArgumentCaptor = ArgumentCaptor.forClass(URL.class);
    Mockito.verify(mockConsoleCow, times(2))
        .createSignedUrl(
            credentialsArgumentCaptor.capture(),
            integerArgumentCaptor.capture(),
            urlArgumentCaptor.capture());

    Credentials capturedCredentials = credentialsArgumentCaptor.getValue();
    assertEquals(fakeAwsCredential.getAccessKeyId(), capturedCredentials.accessKeyId());
    assertEquals(fakeAwsCredential.getSecretAccessKey(), capturedCredentials.secretAccessKey());
    assertEquals(fakeAwsCredential.getSessionToken(), capturedCredentials.sessionToken());

    assertEquals(AwsService.MAX_CONSOLE_SESSION_DURATION, integerArgumentCaptor.getValue());

    URL capturedUrl = urlArgumentCaptor.getValue();
    assertEquals(AwsService.CONSOLE_URL_SCHEME, capturedUrl.getProtocol());
    assertEquals(AwsService.CONSOLE_URL_S3_FOLDER_HOST, capturedUrl.getHost());
    assertEquals(
        String.format("%s/%s", AwsService.CONSOLE_URL_S3_FOLDER_PATH_PREFIX, bucketName),
        capturedUrl.getPath());

    String[] queryParams =
        URLDecoder.decode(capturedUrl.getQuery(), StandardCharsets.UTF_8).split("&");
    assertThat(String.format("%s=%s/", "prefix", prefix), is(in(queryParams)));
    assertThat(String.format("%s=%s", "region", region.toString()), is(in(queryParams)));
  }

  @Test
  void createSignedConsoleUrl_durationOutOfRange() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          awsService.createSignedConsoleUrl(
              mockConsoleCow,
              mockResourceDescription,
              fakeAwsCredential,
              AwsService.MIN_CONSOLE_SESSION_DURATION - 1);
        });
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          awsService.createSignedConsoleUrl(
              mockConsoleCow,
              mockResourceDescription,
              fakeAwsCredential,
              AwsService.MAX_CONSOLE_SESSION_DURATION + 1);
        });
    Mockito.verifyNoInteractions(mockConsoleCow, mockResourceDescription);
  }

  @Test
  void createSignedConsoleUrl_badResourceType() {
    // Chain together mocks to plumb bad resource type
    Mockito.when(mockResourceDescription.getMetadata()).thenReturn(mockResourceMetadata);
    Mockito.when(mockResourceMetadata.getResourceType()).thenReturn(ResourceType.GCS_BUCKET);

    assertThrows(
        InvalidResourceTypeException.class,
        () ->
            awsService.createSignedConsoleUrl(
                mockConsoleCow,
                mockResourceDescription,
                fakeAwsCredential,
                AwsService.MAX_CONSOLE_SESSION_DURATION));
    Mockito.verifyNoInteractions(mockConsoleCow);
  }
}
