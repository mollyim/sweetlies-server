package org.whispersystems.textsecuregcm.util;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import org.whispersystems.textsecuregcm.configuration.DynamoDbClientConfiguration;
import org.whispersystems.textsecuregcm.configuration.DynamoDbConfiguration;
import java.net.URI;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClientBuilder;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

public class DynamoDbFromConfig {

  public static DynamoDbClient client(DynamoDbConfiguration config, AwsCredentialsProvider credentialsProvider) {
    DynamoDbClientBuilder builder = DynamoDbClient.builder()
        .region(Region.of(config.getRegion()))
        .overrideConfiguration(ClientOverrideConfiguration.builder()
            .apiCallTimeout(config.getClientExecutionTimeout())
            .apiCallAttemptTimeout(config.getClientRequestTimeout())
            .build());
    final String endpoint = System.getenv("AWS_ENDPOINT_OVERRIDE");
    if (endpoint != null && !endpoint.isEmpty()) {
      builder.endpointOverride(URI.create(endpoint));
    } else {
      builder.credentialsProvider(credentialsProvider);
    }
    return builder.build();
  }

  public static DynamoDbAsyncClient asyncClient(
      DynamoDbClientConfiguration config,
      AwsCredentialsProvider credentialsProvider) {
    DynamoDbAsyncClientBuilder builder =  DynamoDbAsyncClient.builder()
        .region(Region.of(config.getRegion()))
        .credentialsProvider(credentialsProvider)
        .overrideConfiguration(ClientOverrideConfiguration.builder()
            .apiCallTimeout(config.getClientExecutionTimeout())
            .apiCallAttemptTimeout(config.getClientRequestTimeout())
            .build());
    final String endpoint = System.getenv("AWS_ENDPOINT_OVERRIDE");
    if (endpoint != null && !endpoint.isEmpty()) {
      builder.endpointOverride(URI.create(endpoint));
    } else {
      builder.credentialsProvider(credentialsProvider);
    }
    return builder.build();
  }

  public static AmazonDynamoDB legacyClient(
      DynamoDbConfiguration config,
      AWSCredentialsProvider legacyCredentialsProvider) {
    AmazonDynamoDBClientBuilder builder = AmazonDynamoDBClientBuilder.standard()
        .withClientConfiguration(
            new ClientConfiguration()
                .withClientExecutionTimeout(((int) config.getClientExecutionTimeout().toMillis()))
                .withRequestTimeout((int) config.getClientRequestTimeout().toMillis()));
    final String endpoint = System.getenv("AWS_ENDPOINT_OVERRIDE");
    if (endpoint != null && !endpoint.isEmpty()) {
      builder.withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint, config.getRegion()));
    } else {
      builder.setCredentials(legacyCredentialsProvider);
      builder.withRegion(config.getRegion());
    }
    return builder.build();
  }
}
