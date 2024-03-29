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
package org.apache.streamline.webservice;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.streamline.common.TimeSeriesDBConfiguration;
import io.dropwizard.Configuration;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.NotNull;
import java.util.List;

public class StreamlineConfiguration extends Configuration {

    @NotEmpty
    private List<ModuleConfiguration> modules;

    @NotEmpty
    private String catalogRootUrl;

    @NotNull
    private FileStorageConfiguration fileStorageConfiguration;

    @NotNull
    private StorageProviderConfiguration storageProviderConfiguration;

    private TimeSeriesDBConfiguration timeSeriesDBConfiguration;

    @JsonProperty
    public StorageProviderConfiguration getStorageProviderConfiguration() {
        return storageProviderConfiguration;
    }

    @JsonProperty
    public void setStorageProviderConfiguration(StorageProviderConfiguration storageProviderConfiguration) {
        this.storageProviderConfiguration = storageProviderConfiguration;
    }


    public String getCatalogRootUrl () {
        return catalogRootUrl;
    }

    public void setCatalogRootUrl (String catalogRootUrl) {
        this.catalogRootUrl = catalogRootUrl;
    }

    public FileStorageConfiguration getFileStorageConfiguration() {
        return this.fileStorageConfiguration;
    }

    public void setFileStorageConfiguration(FileStorageConfiguration configuration) {
        this.fileStorageConfiguration = configuration;
    }

    public TimeSeriesDBConfiguration getTimeSeriesDBConfiguration() {
        return timeSeriesDBConfiguration;
    }

    public void setTimeSeriesDBConfiguration(TimeSeriesDBConfiguration timeSeriesDBConfiguration) {
        this.timeSeriesDBConfiguration = timeSeriesDBConfiguration;
    }

    public List<ModuleConfiguration> getModules() {
        return modules;
    }

    public void setModules(List<ModuleConfiguration> modules) {
        this.modules = modules;
    }
}
