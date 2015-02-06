/**
 * Copyright 2015 Confluent Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
package io.confluent.kafkarest.unit;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.easymock.IExpectationSetters;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;

import io.confluent.kafkarest.ConsumerManager;
import io.confluent.kafkarest.Context;
import io.confluent.kafkarest.KafkaRestApplication;
import io.confluent.kafkarest.KafkaRestConfig;
import io.confluent.kafkarest.MetadataObserver;
import io.confluent.kafkarest.TestUtils;
import io.confluent.kafkarest.entities.ConsumerInstanceConfig;
import io.confluent.kafkarest.entities.ConsumerRecord;
import io.confluent.kafkarest.entities.CreateConsumerInstanceResponse;
import io.confluent.kafkarest.entities.TopicPartitionOffset;
import io.confluent.kafkarest.resources.ConsumersResource;
import io.confluent.rest.EmbeddedServerTestHarness;
import io.confluent.rest.RestConfigException;
import io.confluent.rest.exceptions.RestNotFoundException;

import static io.confluent.kafkarest.TestUtils.assertErrorResponse;
import static io.confluent.kafkarest.TestUtils.assertOKResponse;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class ConsumerResourceTest
    extends EmbeddedServerTestHarness<KafkaRestConfig, KafkaRestApplication> {

  private MetadataObserver mdObserver;
  private ConsumerManager consumerManager;
  private Context ctx;

  private static final String groupName = "testgroup";
  private static final String topicName = "testtopic";
  private static final String instanceId = "uniqueid";
  private static final String instancePath = "/consumers/" + groupName + "/instances/" + instanceId;

  private static final String not_found_message = "not found";

  public ConsumerResourceTest() throws RestConfigException {
    mdObserver = EasyMock.createMock(MetadataObserver.class);
    consumerManager = EasyMock.createMock(ConsumerManager.class);
    ctx = new Context(config, mdObserver, null, consumerManager);

    addResource(new ConsumersResource(ctx));
  }

  @Before
  @Override
  public void setUp() throws Exception {
    super.setUp();
    EasyMock.reset(mdObserver, consumerManager);
  }

  @Test
  public void testCreateInstanceRequestsNewInstance() {
    for (TestUtils.RequestMediaType mediatype : TestUtils.V1_ACCEPT_MEDIATYPES) {
      for (String requestMediatype : TestUtils.V1_REQUEST_ENTITY_TYPES) {
        expectCreateGroup(new ConsumerInstanceConfig());
        EasyMock.replay(consumerManager);

        Response response = request("/consumers/" + groupName, mediatype.header)
            .post(Entity.entity(null, requestMediatype));
        assertOKResponse(response, mediatype.expected);
        final CreateConsumerInstanceResponse
            ciResponse =
            response.readEntity(CreateConsumerInstanceResponse.class);
        assertEquals(instanceId, ciResponse.getInstanceId());
        assertThat(ciResponse.getBaseUri(),
                   allOf(startsWith("http://"), containsString(instancePath)));

        EasyMock.verify(consumerManager);
        EasyMock.reset(consumerManager);
      }
    }
  }

  @Test
  public void testCreateInstanceWithConfig() {
    ConsumerInstanceConfig config = new ConsumerInstanceConfig();
    config.setId("testid");

    for (TestUtils.RequestMediaType mediatype : TestUtils.V1_ACCEPT_MEDIATYPES) {
      for (String requestMediatype : TestUtils.V1_REQUEST_ENTITY_TYPES) {
        expectCreateGroup(config);
        EasyMock.replay(consumerManager);

        Response response = request("/consumers/" + groupName, mediatype.header)
            .post(Entity.entity(config, requestMediatype));
        assertOKResponse(response, mediatype.expected);
        final CreateConsumerInstanceResponse
            ciResponse =
            response.readEntity(CreateConsumerInstanceResponse.class);
        assertEquals(instanceId, ciResponse.getInstanceId());
        assertThat(ciResponse.getBaseUri(),
                   allOf(startsWith("http://"), containsString(instancePath)));

        EasyMock.verify(consumerManager);
        EasyMock.reset(consumerManager);
      }
    }
  }

  @Test
  public void testInvalidInstanceOrTopic() {
    for (TestUtils.RequestMediaType mediatype : TestUtils.V1_ACCEPT_MEDIATYPES) {
      for (String requestMediatype : TestUtils.V1_REQUEST_ENTITY_TYPES) {
        // Trying to access either an invalid consumer instance or a missing topic should trigger
        // an error
        expectCreateGroup(new ConsumerInstanceConfig());
        expectReadTopic(topicName, null, new RestNotFoundException(not_found_message, 1000));
        EasyMock.replay(consumerManager);

        Response response = request("/consumers/" + groupName, mediatype.header)
            .post(Entity.entity(null, requestMediatype));
        assertOKResponse(response, mediatype.expected);
        final CreateConsumerInstanceResponse
            createResponse =
            response.readEntity(CreateConsumerInstanceResponse.class);

        final Response
            readResponse =
            request(instanceBasePath(createResponse) + "/topics/" + topicName, mediatype.header)
                .get();
        assertErrorResponse(Response.Status.NOT_FOUND, readResponse,
                            1000, not_found_message, mediatype.expected);

        EasyMock.verify(consumerManager);
        EasyMock.reset(consumerManager);
      }
    }
  }

  @Test
  public void testReadCommit() {
    List<ConsumerRecord> expectedReadLimit = Arrays.asList(
        new ConsumerRecord("key1".getBytes(), "value1".getBytes(), 0, 10)
    );
    List<ConsumerRecord> expectedReadNoLimit = Arrays.asList(
        new ConsumerRecord("key2".getBytes(), "value2".getBytes(), 1, 15),
        new ConsumerRecord("key3".getBytes(), "value3".getBytes(), 2, 20)
    );
    List<TopicPartitionOffset> expectedOffsets = Arrays.asList(
        new TopicPartitionOffset(topicName, 0, 10, 10),
        new TopicPartitionOffset(topicName, 1, 15, 15),
        new TopicPartitionOffset(topicName, 2, 20, 20)
    );

    for (TestUtils.RequestMediaType mediatype : TestUtils.V1_ACCEPT_MEDIATYPES) {
      for (String requestMediatype : TestUtils.V1_REQUEST_ENTITY_TYPES) {
        expectCreateGroup(new ConsumerInstanceConfig());
        expectReadTopic(topicName, 10, expectedReadLimit, null);
        expectReadTopic(topicName, expectedReadNoLimit, null);
        expectCommit(expectedOffsets, null);
        EasyMock.replay(consumerManager);

        Response response = request("/consumers/" + groupName, mediatype.header)
            .post(Entity.entity(null, requestMediatype));
        assertOKResponse(response, mediatype.expected);
        final CreateConsumerInstanceResponse
            createResponse =
            response.readEntity(CreateConsumerInstanceResponse.class);

        // Read with size limit
        String readUrl = instanceBasePath(createResponse) + "/topics/" + topicName;
        Invocation.Builder builder = getJerseyTest().target(readUrl)
            .queryParam("max_bytes", 10).request();
        builder.accept(mediatype.header);
        Response readLimitResponse = builder.get();
        assertOKResponse(readLimitResponse, mediatype.expected);
        final List<ConsumerRecord>
            readLimitResponseRecords =
            readLimitResponse.readEntity(new GenericType<List<ConsumerRecord>>() {
            });
        assertEquals(expectedReadLimit, readLimitResponseRecords);

        // Read without size limit
        Response readResponse = request(readUrl, mediatype.header).get();
        assertOKResponse(readResponse, mediatype.expected);
        final List<ConsumerRecord>
            readResponseRecords =
            readResponse.readEntity(new GenericType<List<ConsumerRecord>>() {
            });
        assertEquals(expectedReadNoLimit, readResponseRecords);

        Response commitResponse = request(instanceBasePath(createResponse), mediatype.header)
            .post(Entity.entity(null, requestMediatype));
        assertOKResponse(response, mediatype.expected);
        final List<TopicPartitionOffset>
            committedOffsets =
            commitResponse.readEntity(new GenericType<List<TopicPartitionOffset>>() {
            });
        assertEquals(expectedOffsets, committedOffsets);

        EasyMock.verify(consumerManager);
        EasyMock.reset(consumerManager);
      }
    }
  }

  @Test
  public void testDeleteInstance() {
    for (TestUtils.RequestMediaType mediatype : TestUtils.V1_ACCEPT_MEDIATYPES) {
      for (String requestMediatype : TestUtils.V1_REQUEST_ENTITY_TYPES) {
        expectCreateGroup(new ConsumerInstanceConfig());
        expectDeleteGroup(false);
        EasyMock.replay(consumerManager);

        Response response = request("/consumers/" + groupName, mediatype.header)
            .post(Entity.entity(null, requestMediatype));
        assertOKResponse(response, mediatype.expected);
        final CreateConsumerInstanceResponse
            createResponse =
            response.readEntity(CreateConsumerInstanceResponse.class);

        final Response
            deleteResponse =
            request(instanceBasePath(createResponse), mediatype.header).delete();
        assertErrorResponse(Response.Status.NO_CONTENT, deleteResponse,
                            0, null, mediatype.expected);

        EasyMock.verify(consumerManager);
        EasyMock.reset(consumerManager);
      }
    }
  }

  @Test
  public void testDeleteInvalidInstance() {
    for (TestUtils.RequestMediaType mediatype : TestUtils.V1_ACCEPT_MEDIATYPES) {
      expectDeleteGroup(true);
      EasyMock.replay(consumerManager);

      final Response
          deleteResponse =
          request("/consumers/" + groupName + "/instances/" + instanceId, mediatype.header)
              .delete();
      assertErrorResponse(Response.Status.NOT_FOUND, deleteResponse,
                          1000, not_found_message, mediatype.expected);

      EasyMock.verify(consumerManager);
      EasyMock.reset(consumerManager);
    }
  }


  private void expectCreateGroup(ConsumerInstanceConfig config) {
    EasyMock.expect(consumerManager.createConsumer(EasyMock.eq(groupName), EasyMock.eq(config)))
        .andReturn(instanceId);
  }

  private void expectReadTopic(String topicName, final List<ConsumerRecord> readResult,
                               final Exception readException) {
    expectReadTopic(topicName, Long.MAX_VALUE, readResult, readException);
  }

  private void expectReadTopic(String topicName, long maxBytes,
                               final List<ConsumerRecord> readResult,
                               final Exception readException) {
    final Capture<ConsumerManager.ReadCallback>
        readCallback =
        new Capture<ConsumerManager.ReadCallback>();
    consumerManager
        .readTopic(EasyMock.eq(groupName), EasyMock.eq(instanceId), EasyMock.eq(topicName),
                   EasyMock.eq(maxBytes), EasyMock.capture(readCallback));
    EasyMock.expectLastCall().andAnswer(new IAnswer<Object>() {
      @Override
      public Object answer() throws Throwable {
        readCallback.getValue().onCompletion(readResult, readException);
        return null;
      }
    });
  }

  private void expectCommit(final List<TopicPartitionOffset> commitResult,
                            final Exception commitException) {
    final Capture<ConsumerManager.CommitCallback>
        commitCallback =
        new Capture<ConsumerManager.CommitCallback>();
    consumerManager.commitOffsets(EasyMock.eq(groupName), EasyMock.eq(instanceId),
                                  EasyMock.capture(commitCallback));
    EasyMock.expectLastCall().andAnswer(new IAnswer<Object>() {
      @Override
      public Object answer() throws Throwable {
        commitCallback.getValue().onCompletion(commitResult, commitException);
        return null;
      }
    });
  }

  private String instanceBasePath(CreateConsumerInstanceResponse createResponse) {
    try {
      return new URI(createResponse.getBaseUri()).getPath();
    } catch (URISyntaxException e) {
      throw new RuntimeException(
          "Invalid URI in CreateConsumerInstanceResponse: \"" + createResponse.getBaseUri() + "\"");
    }
  }

  private void expectDeleteGroup(boolean invalid) {
    consumerManager.deleteConsumer(groupName, instanceId);
    IExpectationSetters expectation = EasyMock.expectLastCall();
    if (invalid) {
      expectation.andThrow(new RestNotFoundException(not_found_message, 1000));
    }
  }
}