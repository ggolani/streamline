package org.apache.streamline.streams.catalog.service.metadata;

import com.google.common.collect.ImmutableList;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.streamline.streams.catalog.ServiceConfiguration;
import org.apache.streamline.streams.catalog.service.StreamCatalogService;
import org.apache.streamline.streams.catalog.service.metadata.common.Tables;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import mockit.Expectations;
import mockit.Mocked;
import mockit.integration.junit4.JMockit;

@Ignore
@RunWith(JMockit.class)
public class HBaseMetadataServiceTest {
    private static final List<String> HBASE_TEST_NAMESPACES = ImmutableList.copyOf(new String[]{"test_namespace_1", "test_namespace_2"});
    private static final List<String> HBASE_TEST_TABLES = ImmutableList.copyOf(new String[]{"test_table_1", "test_table_2"});
    private static final String HBASE_TEST_TABLE_FAMILY = "test_table_family";
    private static final String HBASE_SITE_CONFIG = "metadata/hbase-site.json";

    private HBaseMetadataService hbaseService;

    @Mocked
    private StreamCatalogService catalogService;
    @Mocked
    private ServiceConfiguration serviceConfiguration;

    private void setUp() throws Exception {
        new Expectations() {{
            serviceConfiguration.getConfigurationMap();
            result = getHBaseSiteConfig();
        }};

        hbaseService = HBaseMetadataService.newInstance(catalogService, 1L);

        for (String namespace : HBASE_TEST_NAMESPACES) {
            hbaseService.createNamespace(namespace);
            for (String table : HBASE_TEST_TABLES) {
                hbaseService.createTable(namespace, table, HBASE_TEST_TABLE_FAMILY);
            }
        }
    }

    private void tearDown() throws Exception {
        for (String namespace : HBASE_TEST_NAMESPACES) {
            for (String table : HBASE_TEST_TABLES) {
                hbaseService.disableTable(namespace, table);
                hbaseService.deleteTable(namespace, table);
            }
            hbaseService.deleteNamespace(namespace);
        }
        hbaseService.close();
    }

    /*
        Calling all the tests in one method because table creation during setup is quite expensive and needs to be done
        in the scope of the test because it depends on recorded expectations in order to abstract lots of initialization.
     */
    @Test
    public void test_getNamespace_getTables() throws Exception {
        setUp();
        try {
            test_getHBaseNamespaces();
            test_getHBaseTables();
            test_getHBaseTablesForNamespace();
        } finally {
            tearDown();
        }
    }

    private void test_getHBaseTables() throws Exception {
        final Tables hBaseTables = hbaseService.getHBaseTables();
        Assert.assertTrue(
                hBaseTables.getTables().stream().sorted(String::compareTo).collect(Collectors.toList())
                        .containsAll(HBASE_TEST_NAMESPACES.stream()
                                .flatMap(ns -> HBASE_TEST_TABLES.stream().map(st -> ns + ":" + st))
                                .collect(Collectors.toList())
                        )
        );
    }

    private void test_getHBaseTablesForNamespace() throws Exception {
        final Tables hBaseTables = hbaseService.getHBaseTables(HBASE_TEST_NAMESPACES.get(0));
        Assert.assertEquals(HBASE_TEST_TABLES.stream().map(p -> HBASE_TEST_NAMESPACES.get(0) + ":" + p).collect(Collectors.toList()),
                hBaseTables.getTables().stream().sorted(String::compareTo).collect(Collectors.toList()));
    }

    private void test_getHBaseNamespaces() throws Exception {
        final HBaseMetadataService.Namespaces hBaseNamespaces = hbaseService.getHBaseNamespaces();
        Assert.assertTrue(hBaseNamespaces.getNamespaces().containsAll(HBASE_TEST_NAMESPACES));
    }

    private Map<String, String> getHBaseSiteConfig() throws IOException {
        return new ObjectMapper().readValue(Thread.currentThread().getContextClassLoader()
                        .getResourceAsStream(HBASE_SITE_CONFIG),
                new TypeReference<Map<String, String>>() {
                });
    }
}