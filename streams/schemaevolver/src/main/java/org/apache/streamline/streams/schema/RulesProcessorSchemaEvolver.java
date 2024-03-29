package org.apache.streamline.streams.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import org.apache.streamline.common.Schema;
import org.apache.streamline.common.exception.ParserException;
import org.apache.streamline.common.util.Utils;
import org.apache.streamline.streams.layout.TopologyLayoutConstants;
import org.apache.streamline.streams.layout.component.Stream;
import org.apache.streamline.streams.layout.component.impl.RulesProcessor;
import org.apache.streamline.streams.layout.component.rule.Rule;
import org.apache.streamline.streams.layout.component.rule.action.Action;
import org.apache.streamline.streams.layout.component.rule.action.NotifierAction;
import org.apache.streamline.streams.schema.exception.BadComponentConfigException;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of EvolvingSchema which supports 'Rules Processor' component.
 */
public class RulesProcessorSchemaEvolver implements EvolvingSchema {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Set<Stream> apply(String config, Stream inputStream) throws BadComponentConfigException {
        try {
            Map<String, Object> componentConfig = objectMapper.readValue(config, Map.class);
            Map<String, Object> rulesConfig = (Map<String, Object>) componentConfig.get(
                    TopologyLayoutConstants.JSON_KEY_CONFIG);
            Map<String, Object> rulesProcessorConfig = (Map<String, Object>) rulesConfig.get(
                    TopologyLayoutConstants.JSON_KEY_RULES_PROCESSOR_CONFIG);

            RulesProcessor rulesProcessor = buildRulesProcessor(rulesProcessorConfig);
            Set<Stream> streams = Sets.newHashSet();
            for (Rule rule : rulesProcessor.getRules()) {
                streams.addAll(extractStreamsFromRule(inputStream, rule));
            }

            return streams;
        } catch (Exception e) {
            throw new BadComponentConfigException("Exception while simulating evolution of rules schema", e);
        }
    }

    private RulesProcessor buildRulesProcessor(Map<String, Object> rulesProcessorConfig) throws JsonProcessingException {
        String rulesProcessorConfigJson = objectMapper.writeValueAsString(rulesProcessorConfig);
        return Utils.createObjectFromJson(rulesProcessorConfigJson, RulesProcessor.class);
    }

    private Set<Stream> extractStreamsFromRule(Stream inputStream, Rule rule) throws ParserException {
        Set<Stream> streamSet = Sets.newHashSet();

        // TODO: do we evaluate all rules per each input stream? if not, how we connect (input stream, rule) and how we know it?
        for (Action action : rule.getActions()) {
            streamSet.add(extractSchemaFromAction(inputStream, rule, action));
        }

        return streamSet;
    }

    private Stream extractSchemaFromAction(Stream inputStream, Rule rule, Action action) throws ParserException {
        String streamId = rule.getOutputStreamNameForAction(action);

        Map<String, Object> outputFieldsAndDefaults = null ;
        if(action instanceof NotifierAction) {
            outputFieldsAndDefaults = ((NotifierAction) action).getOutputFieldsAndDefaults();
        }

        if (outputFieldsAndDefaults != null && !outputFieldsAndDefaults.isEmpty()) {
            Schema schema = simulateFieldsProjection(inputStream.getSchema(), outputFieldsAndDefaults);
            return new Stream(streamId, schema);
        } else {
            // no projection
            return new Stream(streamId, inputStream.getSchema());
        }
    }

    private Schema simulateFieldsProjection(Schema inputSchema, Map<String, Object> outputFieldsAndDefaults) throws ParserException {
        Schema.SchemaBuilder schemaBuilder = new Schema.SchemaBuilder();

        // projection
        for (Map.Entry<String, Object> fieldAndDefault : outputFieldsAndDefaults.entrySet()) {
            String fieldName = fieldAndDefault.getKey();
            Object defaultValue = fieldAndDefault.getValue();
            Schema.Field fieldFromDefault = Schema.Field.of(fieldName, Schema.fromJavaType(defaultValue));

            Schema.Field foundField = findFieldByName(inputSchema.getFields(), fieldFromDefault.getName());
            if (null != foundField) {
                schemaBuilder.field(foundField);
            } else {
                schemaBuilder.field(fieldFromDefault);
            }
        }

        return schemaBuilder.build();
    }

    private Schema.Field findFieldByName(List<Schema.Field> fields, String fieldName) {
        for (Schema.Field field : fields) {
            if (field.getName().equals(fieldName)) {
                return field;
            }
        }

        return null;
    }
}
