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

import com.google.common.cache.CacheBuilder;
import io.dropwizard.server.AbstractServerFactory;
import org.apache.streamline.cache.Cache;
import org.apache.streamline.common.Constants;
import org.apache.streamline.common.ModuleRegistration;
import org.apache.streamline.common.util.FileStorage;
import org.apache.streamline.common.util.ReflectionHelper;
import org.apache.streamline.storage.CacheBackedStorageManager;
import org.apache.streamline.storage.Storable;
import org.apache.streamline.storage.StorableKey;
import org.apache.streamline.storage.StorageManager;
import org.apache.streamline.storage.StorageManagerAware;
import org.apache.streamline.storage.cache.impl.GuavaCache;
import org.apache.streamline.storage.cache.writer.StorageWriteThrough;
import org.apache.streamline.storage.cache.writer.StorageWriter;
import org.apache.streamline.streams.exception.ConfigException;
import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.server.DefaultServerFactory;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.apache.streamline.streams.service.GenericExceptionMapper;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class StreamlineApplication extends Application<StreamlineConfiguration> {
    private static final Logger LOG = LoggerFactory.getLogger(StreamlineApplication.class);

    public static void main(String[] args) throws Exception {
        new StreamlineApplication().run(args);
    }

    @Override
    public String getName() {
        return "Streamline Web Service";
    }

    @Override
    public void initialize(Bootstrap<StreamlineConfiguration> bootstrap) {
        bootstrap.addBundle(new AssetsBundle("/assets", "/", "index.html", "static"));
        super.initialize(bootstrap);
    }

    @Override
    public void run(StreamlineConfiguration configuration, Environment environment) throws Exception {
        AbstractServerFactory sf = (AbstractServerFactory) configuration.getServerFactory();
        // disable all default exception mappers
        sf.setRegisterDefaultExceptionMappers(false);

        environment.jersey().register(GenericExceptionMapper.class);

        registerResources(configuration, environment);
    }

    private StorageManager getCacheBackedDao(StreamlineConfiguration configuration) {
        StorageProviderConfiguration storageProviderConfiguration = configuration.getStorageProviderConfiguration();
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

    private StorageWriter getStorageWriter(StorageManager dao) {
        return new StorageWriteThrough(dao);
    }

    private StorageManager doGetCacheBackedDao(Cache<StorableKey, Storable> cache, StorageWriter writer) {
        return new CacheBackedStorageManager(cache, writer);
    }

    private Cache<StorableKey, Storable> getCache(StorageManager dao, CacheBuilder guavaCacheBuilder) {
        return new GuavaCache(dao, guavaCacheBuilder);
    }

    private CacheBuilder getGuavaCacheBuilder() {
        final long maxSize = 1000;
        return CacheBuilder.newBuilder().maximumSize(maxSize);
    }

    private FileStorage getJarStorage (StreamlineConfiguration configuration) {
        FileStorage fileStorage = null;
        try {
            fileStorage = ReflectionHelper.newInstance(configuration.getFileStorageConfiguration().getClassName());
            fileStorage.init(configuration.getFileStorageConfiguration().getProperties());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return fileStorage;
    }

    private void registerResources(StreamlineConfiguration configuration, Environment environment) throws ConfigException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        StorageManager storageManager = getCacheBackedDao(configuration);
        FileStorage fileStorage = this.getJarStorage(configuration);
        int appPort = ((HttpConnectorFactory) ((DefaultServerFactory) configuration.getServerFactory()).getApplicationConnectors().get(0)).getPort();
        String catalogRootUrl = configuration.getCatalogRootUrl().replaceFirst("8080", appPort +"");
        List<ModuleConfiguration> modules = configuration.getModules();
        List<Object> resourcesToRegister = new ArrayList<>();
        for (ModuleConfiguration moduleConfiguration: modules) {
            String moduleName = moduleConfiguration.getName();
            String moduleClassName = moduleConfiguration.getClassName();
            LOG.info("Registering module [{}] with class [{}]", moduleName, moduleClassName);
            ModuleRegistration moduleRegistration = (ModuleRegistration) Class.forName(moduleClassName).newInstance();
            if (moduleConfiguration.getConfig() == null) {
                moduleConfiguration.setConfig(new HashMap<String, Object>());
            }
            moduleConfiguration.getConfig().put(Constants.CONFIG_TIME_SERIES_DB, configuration.getTimeSeriesDBConfiguration());
            moduleConfiguration.getConfig().put(Constants.CONFIG_CATALOG_ROOT_URL, catalogRootUrl);
            moduleRegistration.init(moduleConfiguration.getConfig(), fileStorage);
            if (moduleRegistration instanceof StorageManagerAware) {
                LOG.info("Module [{}] is StorageManagerAware and setting StorageManager.", moduleName);
                StorageManagerAware storageManagerAware = (StorageManagerAware) moduleRegistration;
                storageManagerAware.setStorageManager(storageManager);
            }
            resourcesToRegister.addAll(moduleRegistration.getResources());
        }

        LOG.info("Registering resources to Jersey environment: [{}]", resourcesToRegister);
        for(Object resource : resourcesToRegister) {
            environment.jersey().register(resource);
        }
        environment.jersey().register(MultiPartFeature.class);
    }
}
