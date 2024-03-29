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

import org.apache.streamline.streams.layout.component.impl.splitjoin.JoinAction;
import org.apache.streamline.streams.layout.component.impl.splitjoin.SplitAction;
import org.apache.streamline.streams.layout.component.impl.splitjoin.StageAction;
import org.apache.streamline.streams.layout.component.rule.action.Action;
import org.apache.streamline.streams.layout.component.rule.action.NotifierAction;
import org.apache.streamline.streams.layout.component.rule.action.TransformAction;
import org.apache.streamline.streams.runtime.RuntimeService;
import org.apache.streamline.streams.runtime.TransformActionRuntime;
import org.apache.streamline.streams.runtime.rule.action.ActionRuntime;
import org.apache.streamline.streams.runtime.rule.action.NotifierActionRuntime;
import org.apache.streamline.streams.runtime.splitjoin.JoinActionRuntime;
import org.apache.streamline.streams.runtime.splitjoin.SplitActionRuntime;
import org.apache.streamline.streams.runtime.splitjoin.StageActionRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to create {@link ActionRuntime} instances of a given {@link Action} by using respective factory
 */
public class ActionRuntimeService extends RuntimeService<ActionRuntime, Action> {
    private static final Logger log = LoggerFactory.getLogger(ActionRuntimeService.class);

    private static final Map<Class<? extends Action>, Factory<ActionRuntime, Action>> actionRuntimeFactories = new ConcurrentHashMap<>();
    static {
        // register factories
        // todo this can be moved to startup listener to add all supported ActionRuntimes.
        // factories instance can be taken as an argument
        actionRuntimeFactories.put(SplitAction.class, new SplitActionRuntime.Factory());
        actionRuntimeFactories.put(JoinAction.class, new JoinActionRuntime.Factory());
        actionRuntimeFactories.put(StageAction.class, new StageActionRuntime.Factory());
        actionRuntimeFactories.put(TransformAction.class, new TransformActionRuntime.Factory());
        actionRuntimeFactories.put(NotifierAction.class, new NotifierActionRuntime.Factory());

        log.debug("Registered factories : [{}]", actionRuntimeFactories);
    }

    private static final ActionRuntimeService instance = new ActionRuntimeService();

    private ActionRuntimeService() {
        super(actionRuntimeFactories);
    }

    public static ActionRuntimeService get() {
        return instance;
    }

}
