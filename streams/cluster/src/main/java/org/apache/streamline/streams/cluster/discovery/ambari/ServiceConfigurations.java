package org.apache.streamline.streams.cluster.discovery.ambari;

/**
 * Defines mapping between Service and its configuration types. DO NOT Change the order of
 * these configuration mappings. If you need to add a new mapping, add it to the end.
 */
public enum ServiceConfigurations {
  ZOOKEEPER("zoo.cfg", "zookeeper-env"),
  STORM("storm-site", "storm-env"),
  KAFKA("kafka-broker", "kafka-env"),
  // excluded ssl configurations for security reason
  HDFS("core-site", "hadoop-env", "hadoop-policy", "hdfs-site"),
  HBASE("hbase-env", "hbase-policy", "hbase-site"),
  HIVE("hive-env", "hive-interactive-env", "hive-interactive-site",
      "hivemetastore-site", "hiveserver2-interactive-site",
      "hiveserver2-site","hive-site");

  private final String[] confNames;

  ServiceConfigurations(String... confNames) {
    this.confNames = confNames;
  }

  public String[] getConfNames() {
    return confNames;
  }
}