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
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.streamline.tag.service;


import com.google.common.cache.CacheBuilder;
import org.apache.streamline.cache.Cache;
import org.apache.streamline.registries.tag.service.CatalogTagService;
import org.apache.streamline.registries.tag.service.TagCatalogResource;
import org.apache.streamline.registries.tag.service.TagService;
import org.apache.streamline.storage.CacheBackedStorageManager;
import org.apache.streamline.storage.Storable;
import org.apache.streamline.storage.StorableKey;
import org.apache.streamline.storage.StorageManager;
import org.apache.streamline.storage.cache.impl.GuavaCache;
import org.apache.streamline.storage.cache.writer.StorageWriteThrough;
import org.apache.streamline.storage.cache.writer.StorageWriter;
import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.glassfish.jersey.media.multipart.MultiPartFeature;

public class TestApplication extends Application<TestConfiguration> {

    @Override
    public String getName() {
        return "Tag Web Service";
    }

    @Override
    public void initialize(Bootstrap<TestConfiguration> bootstrap) {
        super.initialize(bootstrap);
    }

    @Override
    public void run(TestConfiguration testConfiguration, Environment environment) throws Exception {
        StorageManager storageManager = getCacheBackedDao(testConfiguration);
        final TagService tagService = new CatalogTagService(storageManager);
        final TagCatalogResource tagCatalogResource = new TagCatalogResource(tagService);
        environment.jersey().register(tagCatalogResource);
        environment.jersey().register(MultiPartFeature.class);
    }

    private StorageManager getCacheBackedDao(TestConfiguration testConfiguration) {
        StorageProviderConfiguration storageProviderConfiguration = testConfiguration.getStorageProviderConfiguration();
        final StorageManager dao = getStorageManager(storageProviderConfiguration);
        final CacheBuilder cacheBuilder = getGuavaCacheBuilder();
        final Cache<StorableKey, Storable> cache = getCache(dao, cacheBuilder);
        final StorageWriter storageWriter = getStorageWriter(dao);

        return doGetCacheBackedDao(cache, storageWriter);
    }

    private StorageManager getStorageManager(StorageProviderConfiguration storageProviderConfiguration) {
        final String providerClass = storageProviderConfiguration.getProviderClass();
        StorageManager storageManager = null;
        try {
            storageManager = (StorageManager) Class.forName(providerClass).newInstance();
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        storageManager.init(storageProviderConfiguration.getProperties());

        return storageManager;
    }


    private CacheBuilder getGuavaCacheBuilder() {
        final long maxSize = 1000;
        return CacheBuilder.newBuilder().maximumSize(maxSize);
    }

    private Cache<StorableKey, Storable> getCache(StorageManager dao, CacheBuilder guavaCacheBuilder) {
        return new GuavaCache(dao, guavaCacheBuilder);
    }

    private StorageWriter getStorageWriter(StorageManager dao) {
        return new StorageWriteThrough(dao);
    }

    private StorageManager doGetCacheBackedDao(Cache<StorableKey, Storable> cache, StorageWriter writer) {
        return new CacheBackedStorageManager(cache, writer);
    }
}