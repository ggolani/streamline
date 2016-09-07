package com.hortonworks.iotas.notification.notifiers.store.hbase.mappers;

import com.hortonworks.iotas.notification.common.Notification;
import com.hortonworks.iotas.notification.store.hbase.mappers.NotificationStatusIndexMapper;

import java.util.Arrays;
import java.util.List;

/**
 * Secondary index mapping for notifier name + status to device notification
 */
public class NotifierStatusDeviceNotificationMapper extends NotificationStatusIndexMapper {
    /**
     * The HBase index table
     */
    private static final String TABLE_NAME = "Notifier_Status_Device_Notification";

    /**
     * The device notification field that is indexed
     */
    private static final List<String> INDEX_FIELD_NAMES = Arrays.asList("notifierName", "status");

    @Override
    protected List<byte[]> getRowKeys(Notification notification) {
        return Arrays.asList(new StringBuilder(notification.getNotifierName())
                .append(ROWKEY_SEP)
                .append(getIndexSuffix(notification))
                .toString().getBytes(CHARSET));
    }

    @Override
    public String getTableName() {
        return TABLE_NAME;
    }

    @Override
    public List<String> getIndexedFieldNames() {
        return INDEX_FIELD_NAMES;
    }
}