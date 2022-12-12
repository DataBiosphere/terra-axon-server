# Developing Terra Axon Server in the Broad Institute Environment

### Writing Configuration

Running TAS and the Test Runner integration tests requires many service accounts and database
coordinates. That information is stored in Broad's Vault server. We do not want the main
code to directly depend on Vault. For example, Verily's Terra deployment will not use
Vault. So the code depends on files that hold the information.

The `scripts/write-config.sh` script is used to collect all of the needed data from vault and
store it in files in the gradle `${rootDir}` in the directory `config/`. Having a Broad Institute
account is the pre-requisite for fetching data from vault.

View current usage information for `write-config.sh` by entering
```sh
./scripts/write-config.sh help
```

### Running
Start server locally with swagger
```
./gradlew bootRun
```