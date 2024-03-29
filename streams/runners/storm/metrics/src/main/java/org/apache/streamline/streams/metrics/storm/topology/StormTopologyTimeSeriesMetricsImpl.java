package org.apache.streamline.streams.metrics.storm.topology;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.streamline.streams.layout.TopologyLayoutConstants;
import org.apache.streamline.streams.layout.component.Component;
import org.apache.streamline.streams.layout.component.TopologyLayout;
import org.apache.streamline.streams.metrics.TimeSeriesQuerier;
import org.apache.streamline.streams.metrics.topology.TopologyTimeSeriesMetrics;
import org.apache.streamline.streams.storm.common.StormRestAPIClient;
import org.apache.streamline.streams.storm.common.StormTopologyUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Storm implementation of the TopologyTimeSeriesMetrics interface
 */
public class StormTopologyTimeSeriesMetricsImpl implements TopologyTimeSeriesMetrics {
    private final StormRestAPIClient client;
    private TimeSeriesQuerier timeSeriesQuerier;
    private final ObjectMapper mapper = new ObjectMapper();

    public StormTopologyTimeSeriesMetricsImpl(StormRestAPIClient client) {
        this.client = client;
    }

    @Override
    public void setTimeSeriesQuerier(TimeSeriesQuerier timeSeriesQuerier) {
        this.timeSeriesQuerier = timeSeriesQuerier;
    }

    @Override
    public TimeSeriesQuerier getTimeSeriesQuerier() {
        return timeSeriesQuerier;
    }

    @Override
    public Map<Long, Double> getCompleteLatency(TopologyLayout topology, Component component, long from, long to) {
        assertTimeSeriesQuerierIsSet();

        String stormTopologyName = StormTopologyUtil.findOrGenerateTopologyName(client, topology.getId(), topology.getName());
        String stormComponentName = getComponentName(component);

        return queryMetrics(stormTopologyName, stormComponentName, StormMappedMetric.completeLatency, from, to);
    }

    @Override
    public Map<String, Map<Long, Double>> getkafkaTopicOffsets(TopologyLayout topology, Component component, long from, long to) {
        assertTimeSeriesQuerierIsSet();

        String stormTopologyName = StormTopologyUtil.findOrGenerateTopologyName(client, topology.getId(), topology.getName());
        String stormComponentName = getComponentName(component);

        String topicName = findKafkaTopicName(topology, component);
        if (topicName == null) {
            throw new IllegalStateException("Cannot find Kafka topic name from source config - topology name: " +
                    topology.getName() + " / source : " + component.getName());
        }

        StormMappedMetric[] metrics = { StormMappedMetric.logsize, StormMappedMetric.offset, StormMappedMetric.lag };

        Map<String, Map<Long, Double>> kafkaOffsets = new HashMap<>();
        for (StormMappedMetric metric : metrics) {
            kafkaOffsets.put(metric.name(), queryKafkaMetrics(stormTopologyName, stormComponentName, metric, topicName, from, to));
        }

        return kafkaOffsets;
    }

    @Override
    public TimeSeriesComponentMetric getComponentStats(TopologyLayout topology, Component component, long from, long to) {
        assertTimeSeriesQuerierIsSet();

        String stormTopologyName = StormTopologyUtil.findOrGenerateTopologyName(client, topology.getId(), topology.getName());
        String stormComponentName = getComponentName(component);

        StormMappedMetric[] metrics = {
                StormMappedMetric.inputRecords, StormMappedMetric.outputRecords, StormMappedMetric.ackedRecords,
                StormMappedMetric.failedRecords, StormMappedMetric.processedTime, StormMappedMetric.recordsInWaitQueue
        };

        Map<String, Map<Long, Double>> componentStats = new HashMap<>();
        for (StormMappedMetric metric : metrics) {
            componentStats.put(metric.name(), queryMetrics(stormTopologyName, stormComponentName, metric, from, to));
        }

        Map<String, Map<Long, Double>> misc = new HashMap<>();
        misc.put(StormMappedMetric.ackedRecords.name(), componentStats.get(StormMappedMetric.ackedRecords.name()));

        TimeSeriesComponentMetric metric = new TimeSeriesComponentMetric(component.getName(),
                componentStats.get(StormMappedMetric.inputRecords.name()),
                componentStats.get(StormMappedMetric.outputRecords.name()),
                componentStats.get(StormMappedMetric.failedRecords.name()),
                componentStats.get(StormMappedMetric.processedTime.name()),
                componentStats.get(StormMappedMetric.recordsInWaitQueue.name()),
                misc);

        return metric;
    }

    private void assertTimeSeriesQuerierIsSet() {
        if (timeSeriesQuerier == null) {
            throw new IllegalStateException("Time series querier is not set!");
        }
    }

    private String getComponentName(Component component) {
        return component.getId() + "-" + component.getName();
    }

    private String findKafkaTopicName(TopologyLayout topology, Component component) {
        String kafkaTopicName = null;
        try {
            Map<String, Object> topologyConfig = topology.getConfig().getProperties();
            List<Map<String, Object>> dataSources = (List<Map<String, Object>>) topologyConfig.get(TopologyLayoutConstants.JSON_KEY_DATA_SOURCES);

            for (Map<String, Object> dataSource : dataSources) {
                // UINAME and TYPE are mandatory fields for dataSource, so skip checking null
                String uiName = (String) dataSource.get(TopologyLayoutConstants.JSON_KEY_UINAME);
                String type = (String) dataSource.get(TopologyLayoutConstants.JSON_KEY_TYPE);

                if (!uiName.equals(component.getName())) {
                    continue;
                }

                if (!type.equalsIgnoreCase("KAFKA")) {
                    throw new IllegalStateException("Type of datasource should be KAFKA");
                }

                // config is a mandatory field for dataSource, so skip checking null
                Map<String, Object> dataSourceConfig = (Map<String, Object>) dataSource.get(TopologyLayoutConstants.JSON_KEY_CONFIG);
                kafkaTopicName = (String) dataSourceConfig.get(TopologyLayoutConstants.JSON_KEY_TOPIC);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse topology configuration.");
        }

        return kafkaTopicName;
    }

    private Map<Long, Double> queryMetrics(String stormTopologyName, String sourceId, StormMappedMetric mappedMetric, long from, long to) {
        Map<Long, Double> metrics = timeSeriesQuerier.getMetrics(stormTopologyName, sourceId, mappedMetric.getStormMetricName(),
                mappedMetric.getAggregateFunction(), from, to);
        return new TreeMap<>(metrics);
    }


    private Map<Long, Double> queryKafkaMetrics(String stormTopologyName, String sourceId, StormMappedMetric mappedMetric,
                                                  String kafkaTopic, long from, long to) {
        Map<Long, Double> metrics = timeSeriesQuerier.getMetrics(stormTopologyName, sourceId, String.format(mappedMetric.getStormMetricName(), kafkaTopic),
                mappedMetric.getAggregateFunction(), from, to);
        return new TreeMap<>(metrics);
    }

}
