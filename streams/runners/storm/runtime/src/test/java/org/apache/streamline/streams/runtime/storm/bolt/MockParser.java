package org.apache.streamline.streams.runtime.storm.bolt;

import com.google.common.collect.Lists;
import org.apache.streamline.common.Schema;
import org.apache.streamline.common.exception.ParserException;
import org.apache.streamline.registries.parser.BaseParser;
import org.apache.streamline.registries.parser.exception.DataValidationException;
import org.apache.streamline.streams.StreamlineEvent;
import org.apache.streamline.streams.common.StreamlineEventImpl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * We can not use a mocking framework as these classes are loaded dynamically as part of parser bolts
 * using reflection. So we have to create mock class implementation.
 */
public class MockParser extends BaseParser {

    public static final Map<String, Object> PARSER_OUTPUT = new HashMap<String, Object>() {{
        put("a","b");
    }};

    public static final StreamlineEvent STREAMLINE_EVENT = new StreamlineEventImpl(PARSER_OUTPUT, "dsrcid");

    @Override
    public String version() {
        return null;
    }

    @Override
    public Schema schema() {
        return null;
    }

    @Override
    public Map<String, Object> parse(byte[] data) throws ParserException {
        return PARSER_OUTPUT;
    }

    @Override
    public Map<String, Object> parse(String data) throws ParserException {
        return PARSER_OUTPUT;
    }

    @Override
    public List<?> parseFields(byte[] data) throws ParserException {
        return Lists.newArrayList(PARSER_OUTPUT.values());
    }

    @Override
    public List<?> parseFields(String data) throws ParserException {
        return Lists.newArrayList(PARSER_OUTPUT.values());
    }

    public static class ValidRawDataInvalidParsedDataParser extends MockParser {
        @Override
        public void validate(byte[] rawData) throws DataValidationException {
        }

        @Override
        public void validate(Map<String, Object> parsedData) throws DataValidationException {
            throw new DataValidationException(parsedData);
        }
    }

    public static class ValidDataParser extends MockParser {
        @Override
        public void validate(byte[] rawData) throws DataValidationException {
        }

        @Override
        public void validate(Map<String, Object> parsedData) throws DataValidationException {
        }
    }
}
