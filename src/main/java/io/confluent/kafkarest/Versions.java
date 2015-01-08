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
package io.confluent.kafkarest;

import java.util.Arrays;
import java.util.List;

public class Versions {

  public static final String KAFKA_V1_JSON = "application/vnd.kafka.v1+json";
  public static final String KAFKA_V1_JSON_WEIGHTED = KAFKA_V1_JSON; // Default weight = 1

  // These are defaults that track the most recent API version. These should always be specified
  // anywhere the latest version is produced/consumed.
  public static final String KAFKA_MOST_SPECIFIC_DEFAULT = KAFKA_V1_JSON;
  public static final String KAFKA_DEFAULT_JSON = "application/vnd.kafka+json";
  public static final String KAFKA_DEFAULT_JSON_WEIGHTED = KAFKA_DEFAULT_JSON + "; qs=0.9";
  public static final String JSON = "application/json";
  public static final String JSON_WEIGHTED = JSON + "; qs=0.5";

  public static final List<String>
      PREFERRED_RESPONSE_TYPES =
      Arrays.asList(Versions.KAFKA_V1_JSON, Versions.KAFKA_DEFAULT_JSON, Versions.JSON);

  // This type is completely generic and carries no actual information about the type of data,
  // but it is the default for request entities if no content type is specified. Well behaving
  // users of the API will always specify the content type, but ad hoc use may omit it. We treat
  // this as JSON since that's all we currently support.
  public static final String GENERIC_REQUEST = "application/octet-stream";
}
