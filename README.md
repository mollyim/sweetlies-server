SweetLies Server [Work in progress 🚧]
================

**SweetLies Server** is a server prototype to which Signal-compatible clients can connect.

This version is forked from [signal-server](https://github.com/signalapp/Signal-server).

## Quick Start Guide

To run a fully functional SweetLies Server there are two major stages:

* **Build Stage** - In this stage the server applications are compiled, tested, and packaged into Docker images for release or deployment.
* **Deploy Stage** - In this stage Terraform is used to set up the infrastructure on the cloud and deploy a new SweetLies cluster.

See more details on [Build](#build-test-steps) & [Deploy](#deployment) stages below.

Here are some steps to help in getting started. They are provided as a way to allow "get up and running" quickly the server on the local computer.

Tested on the latest **Ubuntu 22.04 LTS**.

1. Install dependencies:

   ```sh
   sudo apt update
   sudo apt install docker docker-compose openjdk-11-jdk protobuf-compiler
   ```

2. Run Docker to non-root user:

   ```sh
   sudo usermod -aG docker $USER
   newgrp - docker
   ```

3. Clone the repo with `git`:

   ```sh
   git clone ...
   cd sweetlies-server
   ```

4. Bring up all needed containers, link them and mount data volumes:

   ```sh
   ./gradlew localStackComposeUp
   ```

   This starts a docker-compose stack called `localstack` in the background.

5. Provision the DynamoDB and BigTable instances locally with Terraform:

   ```sh
   ./gradlew tfDevApply
   ```

6. Spin up the server. Two shells are required: a shell to run `whisper-service` and a shell to run `storage-service`:

   ```sh
   ./gradlew :storage-service:runServer
   ```

   ```sh
   ./gradlew :whisper-service:runServer
   ```

   The server applications run on foreground and don't finish. Quit with ctrl+c.

7. While the server is running, open any [compatible app](#compatible-clients) to test it.

8. Cleanup:

   ```sh
   ./gradlew localStackComposeDown
   ```

   Optionally, add the argument `-PremoveVolumes=true` to destroy the data volumes and start over.

## Prototype Notes

This prototype is based on Signal-Server v6.70.0.

It is currenctly in early stage development and supports account registration and websocket notifications only. Any other feature is unavailable.

Missing:

* Contact Discovery Service (CDS)
* Chat and group messaging (due to the lack of CDS)
* Audio & video calls
* FCM push notifications
* CDN features
* SMS-based account authentication
* Signal PIN
* Badges and subscriptions API
* Anti-spam captcha verification
* MobileCoin Payments

## Architecture Overview

SweetLies Server architecture is described in detail in ... TBD

## Directory Structure

The source is modularized in one single source tree. It contains the application code, including its dependencies, and a collection of templates for provisioning and deploying the server infrastructure.

* `service`
  * Core messaging service aka "whisper-service"
* `redis-dispatch`
  * Internal core module
* `gcm-sender-async`
  * Internal core module
* `websockets-resources`
  * Internal core module
* `storage-service`
  * Key-value distributed database forked from [storage-service](https://github.com/signalapp/storage-service)
* `terraform`
  * Tarraform deployment modules
* `localstack`
  * Local cloud stack for the dev environment

## Build & Test Steps

SweetLies Server is a multi-module Gradle project. Upstream uses the Maven build system instead, but it was migrated to Gradle for greater flexibility. It contains the usual `debug` and `release` build variants.

To build from source you will need a Linux development machine.

1. Make sure you have installed the dependencies:

   * `JDK` 11 which comes in `openjdk-11-jdk` in Debian
   * `protobuf-compiler` 3.12.4 or later
   * `git`

2. Clone the repo with `git`:

   ```sh
   git clone ...
   cd sweetlies-server
   ```

3. Compile:

   ```sh
   ./gradlew assemble
   ````

4. Run the unit tests:

   ```sh
   ./gradlew check
   ````

## Configuration

The server consists of two monolithic applications: the messaging (whisper) service and the storage service.

Each application is configured by its own YAML file in the `{service,storage}/src/main/resources/config` folder. The config files, are in turn, tied to a specific type of deployment `{dev,staging,prod}.yml`. When the application is packaged as a JAR file, the config files are bundled as Java resources. Much of the configuration is read from OS environment variables though, to be able to run the applications in Docker containers and multiple environments without recompilation.

### Messaging Service

List of environment variables:

| Variable | Default value | Required |
|----------|---------------|----------|
| `HTTP_HOST_DEV` | `127.0.0.1` | Y |
| `HTTP_PORT_DEV` | `8080` | Y |
| `ADMIN_HOST_DEV` | `127.0.0.1` | Y |
| `ADMIN_HTTP_PORT_DEV` | `8081` | Y |
| `ACCOUNTS_DB_URL_DEV` | (none) | Y |
| `ABUSE_DB_URL_DEV` | (none) | Y |
| `REDIS_CLUSTER_BASE_URL_DEV` | (none) | Y |
| `AWS_REGION` | (none) | Y |
| `AWS_ACCESS_KEY_ID` | (none) | Y |
| `AWS_SECRET_ACCESS_KEY` | (none) | Y |
| `AWS_ENDPOINT_OVERRIDE` | (none) | N |
| `AWS_EC2_METADATA_DISABLED` | `false` | N |

### Storage Service

List of environment variables:

| Variable | Default value | Required |
|----------|---------------|----------|
| `HTTP_HOST_DEV` | `127.0.0.1` | Y |
| `HTTP_PORT_DEV` | `10080` | Y |
| `ADMIN_HOST_DEV` | `127.0.0.1` | Y |
| `ADMIN_HTTP_PORT_DEV` | `10081` | Y |
| `GOOGLE_APPLICATION_CREDENTIALS` | (none) | Y |
| `BIGTABLE_EMULATOR_HOST` | (none) | N |

## How to Run

To run the server interactively with Gradle use `runServer` task with `whisper-service` and `storage` submodules, passing the arguments with `--args`. For example, to show the help of `whisper-service`:

```sh
$ ./gradlew :whisper-service:runServer --args="-h"

> Task :whisper-service:runServer
usage: java -jar project.jar [-h] [-v]
                             {server,check,rmuser,certificate,zkparams,version,check-dynamic-config,accountdb,abusedb}
                             ...

positional arguments:
  {server,check,rmuser,certificate,zkparams,version,check-dynamic-config,accountdb,abusedb}
                         available commands

named arguments:
  -h, --help             show this help message and exit
  -v, --version          show the application version and exit
```

As it can be been, `whisper-service` offers some basic actions as commands. The most important command `server` spins up the HTTP server and runs the application in server mode.

If no arguments are supplied, `runServer` assumes you want to run the **server mode** in the dev environment. It will run the application with args `server config/dev.yml`, also setting the environment variables for the local stack. More about the local stack in the [dev environment](#dev-environment) section.

## Debugging

1. Open the project in any Java IDE (preferable IntelliJ-based IDEs)
2. Create a run configuration of the desired application with `runServer` task as target
3. Debug the task

## Docker Images

For building containers for the Java applications the project uses [Jib](https://github.com/GoogleContainerTools/jib).

To produce the Docker images just run `./gradlew jibDockerBuild`. The gradle task will build the images and load them directly to the Docker daemon, tagged as follow:

* `sweetlies-whisper-service:latest`
* `sweetlies-storage-service:latest`

To push to a Docker registry run `jib` task instead.

You typically use `docker run sweetlies-whisper-service` and `docker run sweetlies-storage-service` to test the containers.

## Deployment Guide

Deployments are managed by [Terraform](https://www.terraform.io/). Requires `terraform` 1.0 or later.

## Dev Environment

For normal development use the `dev` environment with `debug` variant.

### Prerequisites

* `docker`
* `docker-compose`

### Local Stack Overview

The local stack describes the minimal stack to run SweetLies Server locally for development. The goal is to keep the dev environment as close as possible to production emulating cloud dependencies.

It is defined in `localstack/docker-compose.yml` file and includes:

- 1 AWS emulator for DynamoDB and S3
- 1 Bigtable emulator
- 1 Redis cluster with 3 nodes
- 1 Envoy HTTP API gateway
- 2 Postgres databases

Envoy is set up as a TLS termination proxy and requires Docker's host-networking mode (only works on Linux). This way the apps can connect to dev without disabling the TLS certificate pinning. Envoy listens at:

- 127.0.0.1:8443 and forwards to `whisper-service` HTTP port on 127.0.0.1:8080
- 127.0.0.1:10443 and forwards to `storage-service` HTTP port on 127.0.0.1:10080

### Configuration

No additional configuration is needed for the dev environment. The secret keys are hardcoded in the dev config file. ⚠️ Do NOT use any of these keys in PRODUCTION ⚠️

To generate new secret keys:

**Unidentified Delivery**

```sh
./gradlew :whisper-service:runServer --args="certificate --ca"
./gradlew :whisper-service:runServer --args="certificate --key=<ca-private-key> --id=<certificate-serial-number>"
```

**ZK Parameters**

```sh
./gradlew :whisper-service:runServer --args="zkparams"
```

**CA and Self-signed Certificates**

```sh
cat << EOF > dev-localhost.ext
# dev-localhost.ext
authorityKeyIdentifier=keyid,issuer
basicConstraints=CA:FALSE
keyUsage = digitalSignature, nonRepudiation, keyEncipherment
subjectAltName = @alt_names

[alt_names]
DNS.1 = localhost
EOF
openssl genrsa -out dev-ca.key 2048
openssl req -new -x509 -nodes -days 3650 -key dev-ca.key -out dev-ca-cert.pem -subj /CN=SelfSigned
openssl req -newkey rsa:2048 -nodes -days 3650 -keyout dev-localhost.key -out dev-localhost.csr -subj /CN=localhost
openssl x509 -req -days 3650 -CAcreateserial -in dev-localhost.csr -out dev-localhost.pem -CA dev-ca-cert.pem -CAkey dev-ca.key -extfile dev-localhost.ext
```

For the server config, export the localhost private key and certificate to a PKCS12 keystore `dev-localhost.p12` with password `test`:

```sh
openssl pkcs12 -export -out dev-localhost.p12 -inkey dev-localhost.key -in dev-localhost.pem -password pass:test
```

For Android clients (forked from Signal) import `dev-ca-cert-pem` into a BKS v1 keystore BKS `whisper.store` with password `whisper`. Useful tool: [KeyStore Explorer](https://keystore-explorer.org/).

### Deploy

Deploying the dev environment is pretty straightforward with `tfDevApply` task, that configures the local stack according to Terraform `main` module.

This task executes `localStackComposeUp` first, to bring up docker-compose. There is nothing particular about starting docker-compose from Gradle, except that it manages the dependencies between tasks.

To check for errors:

```sh
docker-compose -f localstack/docker-compose.yml logs
```

The deployment will not run any server instance in dev. Instead, the server is intended to be [run interactively](#how-to-run) from CLI or Java IDEs.

### Cleanup

To delete resources created by Terraform:

```sh
./gradlew tfDevDestroy
```

## Staging Environment

TBD

## Production Environment

TBD

## Compatible Clients

Currently the following [libsignal](https://github.com/signalapp/libsignal) versions are supported:

* `libsignal` 0.9.7

### Molly app

[Molly](https://github.com/mollyim/mollyim-android) is a fork of Signal for Android that supports SweetLies.

To run the app for the dev environment, follow these instructions.

**Using Android Studio**

1. Install [Android Studio](https://developer.android.com/studio/index.html).

2. Clone the repo with `git` and switch to `experimental/sweetlies` branch:

   ```sh
   git clone https://github.com/mollyim/mollyim-android.git
   cd mollyim-android
   git checkout experimental/sweetlies
   ```

3. Open the project in Android Studio.

4. Ensure build variant `devFreeDebug` is selected and press Run. Molly should now start on the device. You can find the generated APK in `app/build/outputs/apk/devFree/debug/`.

5. Allow the Android device to connect to the server running on the host computer.

   ```sh
   adb reverse tcp:8443 tcp:8443
   adb reverse tcp:10443 tcp:10443
   ```

   When more than one Android device is connected to the computer, use `adb -e` to specify the emulator or `adb -d` for USB.

6. Go to the app and proceed with the registration. While the SMS verification is disabled any phone number will work.

## License

Licensed under the AGPLv3: https://www.gnu.org/licenses/agpl-3.0.html
