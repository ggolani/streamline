/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.streamline.streams.layout.storm;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class OpenTsdbBoltFluxComponent extends AbstractFluxComponent {

    @Override
    protected void generateComponent() {

        String boltId = "openTsdbBolt" + UUID_FOR_COMPONENTS;
        String boltClassName = "org.apache.storm.hdfs.bolt.OpenTsdbBolt";

        List<Object> boltConstructorArgs = new ArrayList<>();
        boltConstructorArgs.add(getRefYaml(addOpenTsdbClientBuilderComponent()));
        boltConstructorArgs.add(getRefYaml(addOpenTsdbDataPointMapperComponent()));

        List<String> configMethodNames = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        addConfigsWithNoArgs("withFlushInterval", configMethodNames, values);
        addConfigs("withBatchSize", configMethodNames, values);
        addConfigsWithNoArgs("failTupleForFailedMetrics", configMethodNames, values);

        List configMethods = getConfigMethodsYaml(configMethodNames.toArray(new String[0]), values.toArray());

        component = createComponent(boltId, boltClassName, null, boltConstructorArgs, configMethods);

        addParallelismToComponent();
    }

    private void addConfigsWithNoArgs(String configKey, List<String> configMethodNames, List<Object> values) {
        if (conf.get(configKey) != null && ((Boolean)(conf.get(configKey)))) {
            configMethodNames.add(configKey);
            values.add(ArgsType.NONE);
        }
    }

    private void addConfigs(String configKey, List<String> configMethodNames, List<Object> values) {
        if (conf.get(configKey) != null ) {
            configMethodNames.add(configKey);
            values.add(conf.get(configKey));
        }
    }

    private String addOpenTsdbDataPointMapperComponent() {
        String mapperComponentId = "openTsdbDataPointMapper" + UUID_FOR_COMPONENTS;

        String mapperClassName = "org.apache.storm.opentsdb.bolt.TupleOpenTsdbDatapointMapper";

        //constructor args
        String[] constructorArgNames = {"metricField", "timestampField", "tagsField", "valueField"};
        List mapperConstructorArgs = getConstructorArgsYaml(constructorArgNames);

        addToComponents(createComponent(mapperComponentId, mapperClassName, null, mapperConstructorArgs, null));

        return mapperComponentId;
    }

    private String addOpenTsdbClientBuilderComponent() {
        String clientBuilderId = "openTsdbClientBuilder" + UUID_FOR_COMPONENTS;
        String clientBuilderClassName = "org.apache.storm.opentsdb.client.OpenTsdbClient.Builder";

        //constructor args
        String[] constructorArgNames = {"url"};
        List builderConstructorArgs = getConstructorArgsYaml(constructorArgNames);

        List<String> configMethodNames = new ArrayList<>();
        List<Object> values = new ArrayList<>();
        addConfigsWithNoArgs("sync", configMethodNames, values);
        addConfigsWithNoArgs("returnSummary", configMethodNames, values);
        addConfigsWithNoArgs("returnDetails", configMethodNames, values);
        addConfigsWithNoArgs("enableChunkedEncoding", configMethodNames, values);

        List configMethods = getConfigMethodsYaml(configMethodNames.toArray(new String[0]), values.toArray());

        addToComponents(createComponent(clientBuilderId, clientBuilderClassName, null, builderConstructorArgs, configMethods));

        return clientBuilderId;
    }
}
