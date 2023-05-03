package bio.terra.axonserver.service.cloud.aws;

import bio.terra.axonserver.service.exception.InvalidResourceTypeException;
import bio.terra.cloudres.aws.console.ConsoleCow;
import bio.terra.cloudres.common.ClientConfig;
import bio.terra.common.exception.InternalServerErrorException;
import bio.terra.workspace.model.AwsCredential;
import bio.terra.workspace.model.AwsS3StorageFolderAttributes;
import bio.terra.workspace.model.ResourceDescription;
import bio.terra.workspace.model.ResourceMetadata;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sts.model.Credentials;

/** Service for interacting with AWS API surface. */
@Component
public class AwsService {

  public static final Integer MIN_CONSOLE_SESSION_DURATION = 900;
  public static final Integer MAX_CONSOLE_SESSION_DURATION = 43200;
  @VisibleForTesting public static final String CONSOLE_URL_SCHEME = "https";

  @VisibleForTesting
  public static final String CONSOLE_URL_S3_FOLDER_HOST = "s3.console.aws.amazon.com";

  @VisibleForTesting public static final String CONSOLE_URL_S3_FOLDER_PATH_PREFIX = "/s3/buckets";

  private static final ClientConfig clientConfig =
      ClientConfig.Builder.newBuilder().setClient("terra-axon-server").build();

  private URL getDestinationForAwsS3StorageFolder(
      String region, AwsS3StorageFolderAttributes awsS3StorageFolder) {
    try {
      String prefix = String.format("%s/", awsS3StorageFolder.getPrefix());
      return new URIBuilder()
          .setScheme(CONSOLE_URL_SCHEME)
          .setHost(CONSOLE_URL_S3_FOLDER_HOST)
          .setPath(
              String.format(
                  "%s/%s", CONSOLE_URL_S3_FOLDER_PATH_PREFIX, awsS3StorageFolder.getBucketName()))
          .addParameter("prefix", prefix)
          .addParameter("region", region)
          .build()
          .toURL();
    } catch (MalformedURLException | URISyntaxException e) {
      // This should not be possible since we are doing a parameterized build of the URL.
      throw new InternalServerErrorException(e);
    }
  }

  /** For test use only, allows a test to pass a mock {@link ConsoleCow} instance. */
  @VisibleForTesting
  public URL createSignedConsoleUrl(
      ConsoleCow consoleCow,
      ResourceDescription resourceDescription,
      AwsCredential awsCredential,
      Integer duration) {

    Preconditions.checkArgument(duration >= MIN_CONSOLE_SESSION_DURATION);
    Preconditions.checkArgument(duration <= MAX_CONSOLE_SESSION_DURATION);

    ResourceMetadata resourceMetadata = resourceDescription.getMetadata();
    URL destinationUrl =
        switch (resourceMetadata.getResourceType()) {
          case AWS_S3_STORAGE_FOLDER -> getDestinationForAwsS3StorageFolder(
              resourceMetadata.getControlledResourceMetadata().getRegion(),
              resourceDescription.getResourceAttributes().getAwsS3StorageFolder());
          default -> throw new InvalidResourceTypeException(
              String.format(
                  "Resource type %s not supported", resourceMetadata.getResourceType().toString()));
        };

    try {
      Credentials credentials =
          Credentials.builder()
              .accessKeyId(awsCredential.getAccessKeyId())
              .secretAccessKey(awsCredential.getSecretAccessKey())
              .sessionToken(awsCredential.getSessionToken())
              .build();
      return consoleCow.createSignedUrl(credentials, duration, destinationUrl);
    } catch (IOException e) {
      throw new InternalServerErrorException(e);
    }
  }

  /**
   * Gets a signed URL providing access to a single AWS resource in the AWS Console.
   *
   * @param resourceDescription description of resource to create console link to
   * @param awsCredential credentials providing access to the resource
   * @param duration duration in seconds for the console session, must be between {@link
   *     #MIN_CONSOLE_SESSION_DURATION} and {@link #MAX_CONSOLE_SESSION_DURATION} (inclusive)
   * @return A signed URL providing authorized access to the AWS console. This should be treated as
   *     a credential.
   * @throws IllegalArgumentException if passed duration is outside of range {@link *
   *     #MIN_CONSOLE_SESSION_DURATION} to {@link #MAX_CONSOLE_SESSION_DURATION} (inclusive)
   * @throws InvalidResourceTypeException if passed resource is not of a supported AWS resource type
   * @throws InternalServerErrorException on I/O errors when calling AWS federation endpoint
   */
  public URL createSignedConsoleUrl(
      ResourceDescription resourceDescription, AwsCredential awsCredential, Integer duration) {
    ConsoleCow consoleCow = ConsoleCow.create(clientConfig);
    return createSignedConsoleUrl(consoleCow, resourceDescription, awsCredential, duration);
  }
}
