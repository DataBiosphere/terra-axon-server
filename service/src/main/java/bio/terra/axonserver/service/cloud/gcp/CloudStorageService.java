package bio.terra.axonserver.service.cloud.gcp;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.StorageOptions;

public class CloudStorageService {

  public CloudStorageService() {}

  public Blob getBucketObject(
      GoogleCredentials googleCredentials, String bucketName, String objectName) {

    return StorageOptions.newBuilder()
        .setCredentials(googleCredentials)
        .build()
        .getService()
        .get(bucketName, objectName);
  }
}
