package org.apache.streamline.storage.impl.jdbc.phoenix;

import com.google.common.cache.CacheBuilder;
import org.apache.streamline.common.test.HBaseIntegrationTest;
import org.apache.streamline.storage.impl.jdbc.config.ExecutionConfig;
import org.apache.streamline.storage.impl.jdbc.provider.phoenix.factory.PhoenixExecutor;
import org.junit.experimental.categories.Category;

/**
 *
 */

@Category(HBaseIntegrationTest.class)
public abstract  class PhoenixStorageManagerWithCacheIntegrationTest extends PhoenixStorageManagerNoCacheIntegrationTest {

    public PhoenixStorageManagerWithCacheIntegrationTest() {
        setConnectionBuilder();
        CacheBuilder  cacheBuilder = CacheBuilder.newBuilder().maximumSize(3);
        jdbcStorageManager = createJdbcStorageManager(new PhoenixExecutor(new ExecutionConfig(-1), connectionBuilder, cacheBuilder));

    }
}