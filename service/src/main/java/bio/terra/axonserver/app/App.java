package bio.terra.axonserver.app;

import bio.terra.common.logging.LoggingInitializer;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(
    exclude = {
      // We don't make use of DataSource in this application, so exclude it from
      // scanning.
      DataSourceAutoConfiguration.class,
    },
    scanBasePackages = {
      // Scan for logging-related components & configs
      "bio.terra.common.logging",
      // Scan for tracing-related components & configs
      "bio.terra.common.tracing",
      // Scan all service-specific packages beneath the current package
      "bio.terra.axonserver"
    })
@ConfigurationPropertiesScan("bio.terra.axonserver")
public class App {
  public static void main(String[] args) {
    new SpringApplicationBuilder(App.class).initializers(new LoggingInitializer()).run(args);
  }
}
