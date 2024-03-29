package org.apache.streamline.streams.catalog.storage;

import org.apache.streamline.common.test.HBaseIntegrationTest;
import org.apache.streamline.registries.parser.ParserInfo;
import org.apache.streamline.storage.StorableTest;
import org.apache.streamline.storage.exception.NonIncrementalColumnException;
import org.apache.streamline.storage.impl.jdbc.JdbcStorageManager;
import org.apache.streamline.storage.impl.jdbc.config.ExecutionConfig;
import org.apache.streamline.storage.impl.jdbc.phoenix.PhoenixStorageManagerNoCacheIntegrationTest;
import org.apache.streamline.storage.impl.jdbc.provider.phoenix.factory.PhoenixExecutor;
import org.apache.streamline.storage.impl.jdbc.provider.sql.factory.QueryExecutor;
import org.apache.streamline.streams.catalog.service.CatalogService;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Phoenix storage integration tests without using cache.
 *
 */
@Category(HBaseIntegrationTest.class)
public  class CorePhoenixStorageManagerNoCacheIntegrationTest extends PhoenixStorageManagerNoCacheIntegrationTest {


    @Override
    protected void setStorableTests() {
        storableTests = new CatalogTests().getAllTests();
    }

    @Test
    public void testNextId_AutoincrementColumn_IdPlusOne() throws Exception {
        final PhoenixExecutor phoenixExecutor = new PhoenixExecutor(new ExecutionConfig(-1), connectionBuilder);
        String[] nameSpaces = {ParserInfo.NAME_SPACE};
        for (String nameSpace : nameSpaces) {
            log.info("Generating sequence-ids for namespace: [{}]", nameSpace);
            for (int x = 0; x < 100; x++) {
                final Long nextId = phoenixExecutor.nextId(nameSpace);
                log.info("\t\t[{}]", nextId);
                Assert.assertTrue(nextId > 0);
            }
        }
    }

    public JdbcStorageManager createJdbcStorageManager(QueryExecutor queryExecutor) {
        JdbcStorageManager jdbcStorageManager = new JdbcStorageManager(queryExecutor);
        jdbcStorageManager.registerStorables(CatalogService.getStorableClasses());
        return jdbcStorageManager;
    }

    @Test(expected = NonIncrementalColumnException.class)
    public void testNextId_NoAutoincrementTable_NonIncrementalKeyException() throws Exception {
        for (StorableTest test : storableTests) {
            if (test instanceof CatalogTests.FilesTest) {
                getStorageManager().nextId(test.getNameSpace());    // should throw exception
            }
        }
    }

}