/*
 * Copyright 2025, AutoMQ HK Limited.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kafka.autobalancer.metricsreporter.metric;

import kafka.autobalancer.common.types.MetricVersion;
import kafka.autobalancer.common.types.RawMetricTypes;

import org.apache.kafka.common.utils.Utils;
import org.apache.kafka.server.metrics.KafkaMetricsGroup;

import com.yammer.metrics.core.Metric;
import com.yammer.metrics.core.MetricName;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This class was modified based on Cruise Control: com.linkedin.kafka.cruisecontrol.metricsreporter.metric.MetricsUtils.
 */
public final class MetricsUtils {
    // Names
    public static final String BYTES_IN_PER_SEC = "BytesInPerSec";
    public static final String BYTES_OUT_PER_SEC = "BytesOutPerSec";
    /* Used to identify idle partitions  */
    public static final String SIZE = "Size";
    // Attribute
    static final String ATTRIBUTE_MEAN = "Mean";
    static final String ATTRIBUTE_MAX = "Max";
    static final String ATTRIBUTE_50TH_PERCENTILE = "50thPercentile";
    static final String ATTRIBUTE_999TH_PERCENTILE = "999thPercentile";
    // Groups
    private static final String KAFKA_SERVER = "kafka.server";
    private static final String KAFKA_LOG_PREFIX = "kafka.log";
    // Type Keys
    private static final String TOPIC_KEY = "topic";
    private static final String PARTITION_KEY = "partition";
    // Type
    private static final String LOG_GROUP = "Log";
    private static final String BROKER_TOPIC_PARTITION_METRICS_GROUP = "BrokerTopicPartitionMetrics";
    // Name Set.
    private static final Set<String> INTERESTED_TOPIC_PARTITION_METRIC_NAMES =
            Set.of(BYTES_IN_PER_SEC, BYTES_OUT_PER_SEC);
    private static final Set<String> INTERESTED_LOG_METRIC_NAMES = Set.of(SIZE);

    private MetricsUtils() {

    }

    /**
     * Get empty metric for specified metric name
     *
     * @param name metric name
     * @return empty metric implementation
     */
    public static Metric getEmptyMetricFor(String name) {
        switch (name) {
            case BYTES_IN_PER_SEC:
            case BYTES_OUT_PER_SEC:
                return new EmptyMeter();
            default:
                return null;
        }
    }

    public static MetricName buildTopicPartitionMetricName(String name, String topic, String partition) {
        Map<String, String> tags = Map.of("topic", topic, "partition", partition);
        String group = null;
        String type = null;
        if (BYTES_IN_PER_SEC.equals(name) || BYTES_OUT_PER_SEC.equals(name)) {
            group = KAFKA_SERVER;
            type = BROKER_TOPIC_PARTITION_METRICS_GROUP;
        }
        if (group == null) {
            return null;
        }
        return KafkaMetricsGroup.explicitMetricName(group, type, name, tags);
    }

    public static Set<String> getMetricNameMaybeMissing() {
        return new HashSet<>(INTERESTED_TOPIC_PARTITION_METRIC_NAMES);
    }

    /**
     * Create a Auto Balancer Metric.
     *
     * @param nowMs      The current time in milliseconds.
     * @param brokerId   Broker Id.
     * @param metricName Yammer metric name.
     * @param value      Metric value
     * @return A Yammer metric converted as a AutoBalancerMetric.
     */
    public static AutoBalancerMetrics toAutoBalancerMetric(long nowMs,
                                                           int brokerId,
                                                           String brokerRack,
                                                           com.yammer.metrics.core.MetricName metricName,
                                                           double value) {
        return toAutoBalancerMetric(nowMs, brokerId, brokerRack, metricName, value, null);
    }

    /**
     * Create a Auto Balancer Metric.
     *
     * @param nowMs      The current time in milliseconds.
     * @param brokerId   Broker Id.
     * @param brokerRack Broker rack.
     * @param metricName Yammer metric name.
     * @param value      Metric value
     * @param attribute  Metric attribute.
     * @return A Yammer metric converted as a AutoBalancerMetric.
     */
    public static AutoBalancerMetrics toAutoBalancerMetric(long nowMs,
                                                           int brokerId,
                                                           String brokerRack,
                                                           com.yammer.metrics.core.MetricName metricName,
                                                           double value,
                                                           String attribute) {
        Map<String, String> tags = mBeanNameToTags(metricName.getMBeanName());
        AutoBalancerMetrics ccm = tags == null ? null : toAutoBalancerMetric(nowMs, brokerId, brokerRack, metricName.getName(), tags, value, attribute);
        if (ccm == null) {
            throw new IllegalArgumentException(String.format("Cannot convert yammer metric %s to a Auto Balancer metric for "
                    + "broker %d at time %d for tag %s", metricName, brokerId, nowMs, attribute));
        }
        return ccm;
    }

    /**
     * Build a AutoBalancerMetric object.
     *
     * @param nowMs      The current time in milliseconds.
     * @param brokerId   Broker Id.
     * @param brokerRack Broker rack.
     * @param name       Name of the metric.
     * @param tags       Tags of the metric.
     * @param value      Metric value.
     * @param attribute  Metric attribute -- can be {@code null}.
     * @return A {@link AutoBalancerMetrics} object with the given properties.
     */
    private static AutoBalancerMetrics toAutoBalancerMetric(long nowMs,
                                                            int brokerId,
                                                            String brokerRack,
                                                            String name,
                                                            Map<String, String> tags,
                                                            double value,
                                                            String attribute) {
        String topic = tags.get(TOPIC_KEY);
        String partitionStr = tags.get(PARTITION_KEY);
        int partition = -1;
        if (partitionStr != null) {
            try {
                partition = Integer.parseInt(partitionStr);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        switch (name) {
            case BYTES_IN_PER_SEC:
                return bytesInToMetric(topic, partition, nowMs, brokerId, brokerRack, value);
            case BYTES_OUT_PER_SEC:
                return bytesOutToMetric(topic, partition, nowMs, brokerId, brokerRack, value);
            case SIZE:
                if (partition == -1) {
                    return null;
                }
                return new TopicPartitionMetrics(nowMs, brokerId, brokerRack, topic, partition).put(RawMetricTypes.PARTITION_SIZE, value);
            default:
                return null;
        }
    }

    /**
     * Check whether the yammer metric name is an interested metric.
     *
     * @param metricName Yammer metric name.
     * @return {@code true} if the yammer metric name is an interested metric, {@code false} otherwise.
     */
    public static boolean isInterested(com.yammer.metrics.core.MetricName metricName) {
        Map<String, String> tags = yammerMetricScopeToTags(metricName.getScope());
        return tags != null && isInterested(metricName.getGroup(), metricName.getName(), metricName.getType(), tags);
    }

    /**
     * Check if a metric is an interested metric.
     *
     * @param group Group of the metric.
     * @param name  Name of the metric.
     * @param type  Type of the metric.
     * @param tags  Tags of the metric.
     * @return {@code true} for a metric of interest, {@code false} otherwise.
     */
    private static boolean isInterested(String group, String name, String type, Map<String, String> tags) {
        if (group.equals(KAFKA_SERVER)) {
            if (BROKER_TOPIC_PARTITION_METRICS_GROUP.equals(type)) {
                return INTERESTED_TOPIC_PARTITION_METRIC_NAMES.contains(name) && sanityCheckTopicPartitionTags(tags);
            }
        } else if (group.startsWith(KAFKA_LOG_PREFIX) && INTERESTED_LOG_METRIC_NAMES.contains(name)) {
            return LOG_GROUP.equals(type);
        }
        return false;
    }

    /**
     * Convert a yammer metrics scope to a tags map.
     *
     * @param scope Scope of the Yammer metric.
     * @return Empty map for {@code null} scope, {@code null} for scope with keys without a matching value (i.e. unacceptable
     * scope) (see <a href="https://github.com/linkedin/cruise-control/issues/1296">...</a>), parsed tags otherwise.
     */
    public static Map<String, String> yammerMetricScopeToTags(String scope) {
        if (scope != null) {
            String[] kv = scope.split("\\.");
            if (kv.length % 2 != 0) {
                return null;
            }
            Map<String, String> tags = new HashMap<>();
            for (int i = 0; i < kv.length; i += 2) {
                tags.put(kv[i], kv[i + 1]);
            }
            return tags;
        } else {
            return Collections.emptyMap();
        }
    }

    /**
     * Convert a mbean name to tags.
     * This conversion is based on the assumption that the mbean name is in the format of "group:type=ClassName,name=MetricName,tag1=value1,tag2=value2,..."
     * any string that follows the "name=MetricName" will be considered as tags. This is assured in {@code org/apache/kafka/server/metrics/KafkaMetricsGroup.java:53}
     *
     * @param mbeanName MBean name for the Yammer metric.
     * @return Empty map for {@code null} mBeanName, {@code null} for mBeanName without a legal format, parsed tags otherwise.
     */
    public static Map<String, String> mBeanNameToTags(String mbeanName) {
        if (!Utils.isBlank(mbeanName)) {
            String[] kv = mbeanName.split(",");
            Map<String, String> tags = new HashMap<>();
            boolean markTagStart = false;
            for (String tag : kv) {
                String[] pair = tag.split("=");
                if (pair.length != 2) {
                    return null;
                }
                if (!markTagStart) {
                    if (pair[0].equals("name")) {
                        markTagStart = true;
                    }
                    continue;
                }
                tags.put(pair[0], pair[1]);
            }
            return tags;
        } else {
            return Collections.emptyMap();
        }
    }

    /**
     * Check if tags are valid for a topic-partition metric
     *
     * @param tags metric tags
     * @return true if valid, false otherwise
     */
    public static boolean sanityCheckTopicPartitionTags(Map<String, String> tags) {
        return tags.containsKey("topic") && tags.containsKey("partition");
    }

    private static AutoBalancerMetrics bytesInToMetric(String topic, int partition, long nowMs,
                                                       int brokerId, String brokerRack, double value) {

        if (topic != null && partition != -1) {
            return new TopicPartitionMetrics(nowMs, brokerId, brokerRack, topic, partition).put(RawMetricTypes.PARTITION_BYTES_IN, value);
        }
        return null;
    }

    private static AutoBalancerMetrics bytesOutToMetric(String topic, int partition, long nowMs,
                                                        int brokerId, String brokerRack, double value) {

        if (topic != null && partition != -1) {
            return new TopicPartitionMetrics(nowMs, brokerId, brokerRack, topic, partition).put(RawMetricTypes.PARTITION_BYTES_OUT, value);
        }
        return null;
    }

    public static boolean sanityCheckTopicPartitionMetricsCompleteness(AutoBalancerMetrics metrics) {
        return metrics.getMetricValueMap().keySet().containsAll(MetricVersion.LATEST_VERSION.requiredPartitionMetrics());
    }

    public static boolean sanityCheckBrokerMetricsCompleteness(AutoBalancerMetrics metrics) {
        return metrics.getMetricValueMap().keySet().containsAll(MetricVersion.LATEST_VERSION.requiredBrokerMetrics());
    }

    public static String topicPartitionKey(String topic, int partition) {
        return topic + "-" + partition;
    }
}
