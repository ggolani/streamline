/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.streamline.streams.layout.storm;

import com.metamx.common.Granularity;
import com.metamx.tranquility.beam.Beam;
import com.metamx.tranquility.beam.ClusteredBeamTuning;
import com.metamx.tranquility.druid.DruidBeamConfig;
import com.metamx.tranquility.druid.DruidBeams;
import com.metamx.tranquility.druid.DruidDimensions;
import com.metamx.tranquility.druid.DruidLocation;
import com.metamx.tranquility.druid.DruidRollup;
import com.metamx.tranquility.typeclass.Timestamper;
import io.druid.data.input.impl.TimestampSpec;
import io.druid.granularity.QueryGranularities;
import io.druid.granularity.QueryGranularity;
import io.druid.query.aggregation.AggregatorFactory;
import io.druid.query.aggregation.CountAggregatorFactory;
import io.druid.query.aggregation.DoubleMaxAggregatorFactory;
import io.druid.query.aggregation.DoubleMinAggregatorFactory;
import io.druid.query.aggregation.DoubleSumAggregatorFactory;
import io.druid.query.aggregation.LongMaxAggregatorFactory;
import io.druid.query.aggregation.LongMinAggregatorFactory;
import io.druid.query.aggregation.LongSumAggregatorFactory;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.storm.druid.bolt.DruidBeamFactory;
import org.apache.storm.task.IMetricsContext;
import org.joda.time.DateTime;
import org.joda.time.Period;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Druid bolt must be supplied with a BeamFactory. You can implement one of these using the
 * [DruidBeams builder's] (https://github.com/druid-io/tranquility/blob/master/core/src/main/scala/com/metamx/tranquility/druid/DruidBeams.scala)
 * "buildBeam()" method. See the [Configuration documentation] (https://github.com/druid-io/tranquility/blob/master/docs/configuration.md) for details.
 * For more details refer [Tranquility library] (https://github.com/druid-io/tranquility) docs.
 */
public class DruidBeamFactoryImpl implements DruidBeamFactory<Map<String, Object>> {

    private String indexService = "druid/overlord"; // Your overlord's druid.service;
    private String discoveryPath = "/druid/discovery"; // Your overlord's druid.discovery.curator.path;
    private String dataSource = "test";
    private String tranquilityZKconnect = "";
    private List<String> dimensions = new LinkedList<>();
    private String timestampField  = "timestamp";
    private int clusterPartitions = 1;
    private int clusterReplication = 1 ;
    private String windowPeriod = "PT10M";
    private String indexRetryPeriod = "PT10M";
    private String segmentGranularity = "HOUR";
    private String queryGranularity = "MINUTE";
    private Map<String, Object> aggregatorInfo = new HashMap<>();


    @Override
    public Beam<Map<String, Object>> makeBeam(Map<?, ?> conf, IMetricsContext metrics) {

        List<AggregatorFactory> aggregator = getAggregatorList();

        // Tranquility needs to be able to extract timestamps from your object type (in this case, Map<String, Object>).
        final Timestamper<Map<String, Object>> timestamper = new Timestamper<Map<String, Object>>()
        {
            @Override
            public DateTime timestamp(Map<String, Object> theMap)
            {
                return new DateTime(theMap.get(timestampField));
            }
        };

        // Tranquility uses ZooKeeper (through Curator) for coordination.
        final CuratorFramework curator = CuratorFrameworkFactory
                .builder()
                .connectString(tranquilityZKconnect) // we can use Storm conf to get config values
                .retryPolicy(new ExponentialBackoffRetry(1000, 20, 30000))
                .build();
        curator.start();

        // The JSON serialization of your object must have a timestamp field in a format that Druid understands. By default,
        // Druid expects the field to be called "timestamp" and to be an ISO8601 timestamp.
        final TimestampSpec timestampSpec = new TimestampSpec(timestampField, "auto", null);

        // Tranquility needs to be able to serialize your object type to JSON for transmission to Druid. By default this is
        // done with Jackson. If you want to provide an alternate serializer, you can provide your own via ```.objectWriter(...)```.
        // In this case, we won't provide one, so we're just using Jackson.
        final Beam<Map<String, Object>> beam = DruidBeams
                .builder(timestamper)
                .curator(curator)
                .discoveryPath(discoveryPath)
                .location(DruidLocation.create(indexService, dataSource))
                .timestampSpec(timestampSpec)
                .rollup(DruidRollup.create(DruidDimensions.specific(dimensions), aggregator, getQueryGranularity()))
                .tuning(
                        ClusteredBeamTuning
                                .builder()
                                .segmentGranularity(getSegmentGranularity())
                                .windowPeriod(new Period(windowPeriod))
                                .partitions(clusterPartitions)
                                .replicants(clusterReplication)
                                .build()
                )
                .druidBeamConfig(
                        DruidBeamConfig
                                .builder()
                                .indexRetryPeriod(new Period(indexRetryPeriod))
                                .build())
                .buildBeam();

        return beam;
    }

    public String getIndexService() {
        return indexService;
    }

    public void setIndexService(String indexService) {
        this.indexService = indexService;
    }

    public String getDiscoveryPath() {
        return discoveryPath;
    }

    public void setDiscoveryPath(String discoveryPath) {
        this.discoveryPath = discoveryPath;
    }

    public String getDataSource() {
        return dataSource;
    }

    public void setDataSource(String dataSource) {
        this.dataSource = dataSource;
    }

    public String getTranquilityZKconnect() {
        return tranquilityZKconnect;
    }

    public void setTranquilityZKconnect(String tranquilityZKconnect) {
        this.tranquilityZKconnect = tranquilityZKconnect;
    }

    public List<String> getDimensions() {
        return dimensions;
    }

    public void setDimensions(List<String> dimensions) {
        this.dimensions = dimensions;
    }

    public String getTimestampField() {
        return timestampField;
    }

    public void setTimestampField(String timestampField) {
        this.timestampField = timestampField;
    }

    public int getClusterReplication() {
        return clusterReplication;
    }

    public void setClusterReplication(int clusterReplication) {
        this.clusterReplication = clusterReplication;
    }

    public int getClusterPartitions() {
        return clusterPartitions;
    }

    public void setClusterPartitions(int clusterPartitions) {
        this.clusterPartitions = clusterPartitions;
    }

    public String getWindowPeriod() {
        return windowPeriod;
    }

    public void setWindowPeriod(String windowPeriod) {
        this.windowPeriod = windowPeriod;
    }

    public String getIndexRetryPeriod() {
        return indexRetryPeriod;
    }

    public void setIndexRetryPeriod(String indexRetryPeriod) {
        this.indexRetryPeriod = indexRetryPeriod;
    }

    public void setSegmentGranularity(String segmentGranularity) {
        this.segmentGranularity = segmentGranularity;
    }

    public void setQueryGranularity(String queryGranularity) {
        this.queryGranularity = queryGranularity;
    }

    public Map<String, Object> getAggregatorInfo() {
        return aggregatorInfo;
    }

    public void setAggregatorInfo(Map<String, Object> aggregatorInfo) {
        this.aggregatorInfo = aggregatorInfo;
    }

    private List<AggregatorFactory> getAggregatorList() {
        List<AggregatorFactory> aggregatorList = new LinkedList<>();

        aggregatorList.addAll(getCountAggregators());
        aggregatorList.addAll(getDoubleAggregators());
        aggregatorList.addAll(getLongAggregators());

        return aggregatorList;
    }


    private Collection<? extends AggregatorFactory> getCountAggregators() {
        List<AggregatorFactory> aggregatorList = new LinkedList<>();
        List<Map<String, Object>> list = (List<Map<String, Object>>) aggregatorInfo.get("count");

        for (Map<String, Object> aggregrator: list ) {
            aggregatorList.add(new CountAggregatorFactory((String)aggregrator.get("name")));
        }

        return aggregatorList;
    }

    private Collection<? extends AggregatorFactory> getDoubleAggregators() {
        List<AggregatorFactory> aggregatorList = new LinkedList<>();
        List<Map<String, Object>> list = (List<Map<String, Object>>) aggregatorInfo.get("doublesum");

        for (Map<String, Object> aggregrator: list ) {
            aggregatorList.add(new DoubleSumAggregatorFactory((String)aggregrator.get("name"), (String)aggregrator.get("fieldName")));
        }

        list = (List<Map<String, Object>>) aggregatorInfo.get("doublemax");

        for (Map<String, Object> aggregrator: list ) {
            aggregatorList.add(new DoubleMaxAggregatorFactory((String)aggregrator.get("name"), (String)aggregrator.get("fieldName")));
        }

        list = (List<Map<String, Object>>) aggregatorInfo.get("doublemin");

        for (Map<String, Object> aggregrator: list ) {
            aggregatorList.add(new DoubleMinAggregatorFactory((String)aggregrator.get("name"), (String)aggregrator.get("fieldName")));
        }

        return aggregatorList;
    }

    private Collection<? extends AggregatorFactory> getLongAggregators() {
        List<AggregatorFactory> aggregatorList = new LinkedList<>();
        List<Map<String, Object>> list = (List<Map<String, Object>>) aggregatorInfo.get("longsum");

        for (Map<String, Object> aggregrator: list ) {
            aggregatorList.add(new LongSumAggregatorFactory((String)aggregrator.get("name"), (String)aggregrator.get("fieldName")));
        }

        list = (List<Map<String, Object>>) aggregatorInfo.get("longmax");

        for (Map<String, Object> aggregrator: list ) {
            aggregatorList.add(new LongMaxAggregatorFactory((String)aggregrator.get("name"), (String)aggregrator.get("fieldName")));
        }

        list = (List<Map<String, Object>>) aggregatorInfo.get("longmin");

        for (Map<String, Object> aggregrator: list ) {
            aggregatorList.add(new LongMinAggregatorFactory((String)aggregrator.get("name"), (String)aggregrator.get("fieldName")));
        }

        return aggregatorList;
    }

    private QueryGranularity getQueryGranularity() {
        if ("NONE".equals(queryGranularity))
            return QueryGranularities.NONE;
        else if ("ALL".equals(queryGranularity))
            return QueryGranularities.ALL;
        else
            return QueryGranularity.fromString(queryGranularity);
    }

    private Granularity getSegmentGranularity() {
        Granularity granularity = Granularity.HOUR;

        switch (segmentGranularity) {
            case "SECOND":
                granularity = Granularity.SECOND;
                break;
            case "MINUTE":
                granularity = Granularity.MINUTE;
                break;
            case "FIVE_MINUTE":
                granularity = Granularity.FIVE_MINUTE;
                break;
            case "TEN_MINUTE":
                granularity = Granularity.TEN_MINUTE;
                break;
            case "FIFTEEN_MINUTE":
                granularity = Granularity.FIFTEEN_MINUTE;
                break;
            case "HOUR":
                granularity = Granularity.HOUR;
                break;
            case "SIX_HOUR":
                granularity = Granularity.SIX_HOUR;
                break;
            case "DAY":
                granularity = Granularity.DAY;
                break;
            case "WEEK":
                granularity = Granularity.WEEK;
                break;
            case "MONTH":
                granularity = Granularity.MONTH;
                break;
            case "YEAR":
                granularity = Granularity.YEAR;
                break;
            default:
                break;
        }
        return granularity;
    }
}