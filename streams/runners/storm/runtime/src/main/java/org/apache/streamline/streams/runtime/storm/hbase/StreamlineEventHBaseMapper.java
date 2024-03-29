package org.apache.streamline.streams.runtime.storm.hbase;

import com.google.common.base.Charsets;
import org.apache.streamline.streams.StreamlineEvent;
import org.apache.storm.hbase.bolt.mapper.HBaseMapper;
import org.apache.storm.hbase.common.ColumnList;
import org.apache.storm.tuple.Tuple;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.apache.storm.hbase.common.Utils.toBytes;

public class StreamlineEventHBaseMapper implements HBaseMapper {
    private final byte[] columnFamily;
    //TODO need to support counter fields
    private static final byte[] CF_DSRCID = "d".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CV_DEFAULT = "1".getBytes(StandardCharsets.UTF_8);

    public StreamlineEventHBaseMapper(String columnFamily) {
        this.columnFamily = columnFamily.getBytes(Charsets.UTF_8);
    }

    @Override
    public byte[] rowKey(Tuple tuple) {
        StreamlineEvent event = (StreamlineEvent) tuple.getValueByField(StreamlineEvent.STREAMLINE_EVENT);
        return toBytes(event.getId());
    }

    @Override
    public ColumnList columns(Tuple tuple) {
        StreamlineEvent event = (StreamlineEvent) tuple.getValueByField(StreamlineEvent.STREAMLINE_EVENT);

        ColumnList columnList = new ColumnList();
        for (String key : event.keySet()) {
            //Hbase bolt can not handle null values.
            if (event.get(key) != null) {
                columnList.addColumn(columnFamily, key.getBytes(Charsets.UTF_8), toBytes(event.get(key)));
            }
        }
        columnList.addColumn(CF_DSRCID, event.getDataSourceId().getBytes(StandardCharsets.UTF_8), CV_DEFAULT);
        return columnList;
    }
}
