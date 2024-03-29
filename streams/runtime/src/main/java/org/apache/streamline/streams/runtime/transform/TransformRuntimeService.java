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
package org.apache.streamline.streams.runtime.transform;

import org.apache.streamline.streams.layout.Transform;
import org.apache.streamline.streams.layout.component.rule.action.transform.AddHeaderTransform;
import org.apache.streamline.streams.layout.component.rule.action.transform.EnrichmentTransform;
import org.apache.streamline.streams.layout.component.rule.action.transform.MergeTransform;
import org.apache.streamline.streams.layout.component.rule.action.transform.ProjectionTransform;
import org.apache.streamline.streams.layout.component.rule.action.transform.SubstituteTransform;
import org.apache.streamline.streams.runtime.RuntimeService;
import org.apache.streamline.streams.runtime.TransformRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to create {@link org.apache.streamline.streams.runtime.TransformRuntime} instances of a given
 * {@link Transform} by using respective factory
 */
public class TransformRuntimeService extends RuntimeService<TransformRuntime, Transform> {
    private static final Logger log = LoggerFactory.getLogger(TransformRuntimeService.class);

    private static final Map<Class<? extends Transform>, RuntimeService.Factory<TransformRuntime, Transform>> transformFactories = new ConcurrentHashMap<>();

    static {
        // register factories
        // todo this can be moved to startup listener to add all supported Transforms.
        // factories instance can be taken as an argument
        transformFactories.put(EnrichmentTransform.class, new EnrichmentTransformRuntime.Factory());
        transformFactories.put(ProjectionTransform.class, new ProjectionTransformRuntime.Factory());
        transformFactories.put(AddHeaderTransform.class, new AddHeaderTransformRuntime.Factory());
        transformFactories.put(SubstituteTransform.class, new SubstituteTransformRuntime.Factory());
        transformFactories.put(MergeTransform.class, new MergeTransformRuntime.Factory());

        log.info("Registered factories : [{}]", transformFactories);
    }

    private static final TransformRuntimeService instance = new TransformRuntimeService();

    private TransformRuntimeService() {
        super(transformFactories);
    }

    public static TransformRuntimeService get() {
        return instance;
    }

}
