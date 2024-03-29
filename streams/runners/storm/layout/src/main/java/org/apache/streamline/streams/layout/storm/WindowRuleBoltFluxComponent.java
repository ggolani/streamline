package org.apache.streamline.streams.layout.storm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import org.apache.streamline.streams.layout.component.impl.RulesProcessor;
import org.apache.streamline.streams.layout.component.rule.Rule;
import org.apache.streamline.streams.layout.component.rule.expression.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handle rules with windowing
 */
public class WindowRuleBoltFluxComponent extends RuleBoltFluxComponent {
    private final Logger log = LoggerFactory.getLogger(WindowRuleBoltFluxComponent.class);

    public WindowRuleBoltFluxComponent() {
    }

    @Override
    protected void generateComponent() {
        rulesProcessor = (RulesProcessor) conf.get(StormTopologyLayoutConstants.STREAMLINE_COMPONENT_CONF_KEY);
        String boltId = "windowruleBolt" + UUID_FOR_COMPONENTS;
        String boltClassName = "org.apache.streamline.streams.runtime.storm.bolt.rules.WindowRulesBolt";
        List boltConstructorArgs = new ArrayList();
        ObjectMapper mapper = new ObjectMapper();
        String rulesProcessorJson = null;
        try {
            rulesProcessorJson = mapper.writeValueAsString(rulesProcessor);
        } catch (JsonProcessingException e) {
            log.error("Error creating json config string for RulesProcessor",
                    e);
        }
        boltConstructorArgs.add(rulesProcessorJson);
        // hardcode script type enum for now.
        boltConstructorArgs.add("SQL");
        String[] configMethodNames = {"withWindowConfig"};
        Object[] configKeys = {getRefYaml(addWindowConfig())};
        List configMethods = getConfigMethodsYaml(configMethodNames, configKeys);
        component = createComponent(boltId, boltClassName, null, boltConstructorArgs, configMethods);
        addParallelismToComponent();
    }

    private String addWindowConfig() {
        String windowId = "window" + UUID_FOR_COMPONENTS;
        String windowClassName = "org.apache.streamline.streams.layout.component.rule.expression.Window";
        ObjectMapper mapper = new ObjectMapper();
        String windowJson = null;
        try {
            Set<Window> windows = new HashSet<>(Collections2.transform(rulesProcessor.getRules(), new Function<Rule, Window>() {
                @Override
                public Window apply(Rule input) {
                    return input.getWindow();
                }
            }));
            if (windows.size() != 1) {
                throw new IllegalArgumentException("All the rules in a windowed rule bolt should have the same window config.");
            }
            windowJson = mapper.writeValueAsString(windows.iterator().next());
        } catch (JsonProcessingException e) {
            log.error("Error creating json config string for RulesProcessor", e);
        }
        List constructorArgs = new ArrayList();
        constructorArgs.add(windowJson);
        this.addToComponents(this.createComponent(windowId, windowClassName, null, constructorArgs, null));
        return windowId;
    }
}
