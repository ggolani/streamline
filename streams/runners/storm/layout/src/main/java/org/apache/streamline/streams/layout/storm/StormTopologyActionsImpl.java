package org.apache.streamline.streams.layout.storm;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.streamline.common.Config;
import org.apache.streamline.streams.layout.TopologyLayoutConstants;
import org.apache.streamline.streams.layout.component.StatusImpl;
import org.apache.streamline.streams.layout.component.TopologyActions;
import org.apache.streamline.streams.layout.component.TopologyDag;
import org.apache.streamline.streams.layout.component.TopologyLayout;
import org.apache.streamline.streams.storm.common.StormRestAPIClient;
import org.apache.streamline.streams.storm.common.StormTopologyUtil;
import org.glassfish.jersey.client.ClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Storm implementation of the TopologyActions interface
 */
public class StormTopologyActionsImpl implements TopologyActions {
    private static final Logger LOG = LoggerFactory.getLogger(StormTopologyActionsImpl.class);
    public static final int DEFAULT_WAIT_TIME_SEC = 30;

    private String stormArtifactsLocation = "/tmp/storm-artifacts/";
    private String stormCliPath = "storm";
    private String stormJarLocation;
    private String catalogRootUrl;
    private String javaJarCommand;
    private StormRestAPIClient client;
    private Map<String, String> conf;

    public StormTopologyActionsImpl() {
    }

    @Override
    public void init (Map<String, String> conf) {
        this.conf = conf;
        if (conf != null) {
            if (conf.containsKey(StormTopologyLayoutConstants.STORM_ARTIFACTS_LOCATION_KEY)) {
                stormArtifactsLocation = conf.get(StormTopologyLayoutConstants.STORM_ARTIFACTS_LOCATION_KEY);
            }
            if (conf.containsKey(StormTopologyLayoutConstants.STORM_HOME_DIR)) {
                String stormHomeDir = conf.get(StormTopologyLayoutConstants.STORM_HOME_DIR);
                if (!stormHomeDir.endsWith(File.separator)) {
                    stormHomeDir += File.separator;
                }
                stormCliPath = stormHomeDir + "bin" + File.separator + "storm";
            }
            stormJarLocation = conf.get(StormTopologyLayoutConstants.STORM_JAR_LOCATION_KEY);
            catalogRootUrl = conf.get(StormTopologyLayoutConstants.YAML_KEY_CATALOG_ROOT_URL);
            String res;
            if ((res=conf.get(TopologyLayoutConstants.JAVA_JAR_COMMAND)) != null) {
                javaJarCommand = res;
            } else {
                javaJarCommand = "jar";
            }

            String stormApiRootUrl = null;
            if (conf != null) {
                stormApiRootUrl = conf.get(TopologyLayoutConstants.STORM_API_ROOT_URL_KEY);
            }
            Client restClient = ClientBuilder.newClient(new ClientConfig());
            this.client = new StormRestAPIClient(restClient, stormApiRootUrl);
        }
        File f = new File (stormArtifactsLocation);
        f.mkdirs();
    }


    @Override
    public void deploy (TopologyLayout topology) throws Exception {
        Path jarToDeploy = addArtifactsToJar(getArtifactsLocation(topology));
        String fileName = createYamlFile(topology);
        List<String> commands = new ArrayList<String>();
        commands.add(stormCliPath);
        commands.add("jar");
        commands.add(jarToDeploy.toString());
        commands.addAll(getExtraJarsArg(topology));
        commands.add("org.apache.storm.flux.Flux");
        commands.add("--remote");
        commands.add(fileName);
        int exitValue = executeShellProcess(commands).exitValue;
        if (exitValue != 0) {
            throw new Exception("Topology could not be deployed " +
                    "successfully.");
        }
    }

    private List<String> getExtraJarsArg(TopologyLayout topology) {
        List<String> args = new ArrayList<>();
        List<String> jars = new ArrayList<>();
        Path extraJarsPath = getExtraJarsLocation(topology);
        if (extraJarsPath.toFile().isDirectory()) {
            File[] jarFiles = extraJarsPath.toFile().listFiles();
            if (jarFiles != null && jarFiles.length > 0) {
                for (File jarFile : jarFiles) {
                    jars.add(jarFile.getAbsolutePath());
                }
            }
        } else {
            LOG.debug("Extra jars directory {} does not exist, not adding any extra jars", extraJarsPath);
        }
        if (!jars.isEmpty()) {
            args.add("--jars");
            args.add(Joiner.on(",").join(jars));
        }
        return args;
    }

    private Path addArtifactsToJar(Path artifactsLocation) throws Exception {
        Path jarFile = Paths.get(stormJarLocation);
        if (artifactsLocation.toFile().isDirectory()) {
            File[] artifacts = artifactsLocation.toFile().listFiles();
            if (artifacts != null && artifacts.length > 0) {
                Path newJar = Files.copy(jarFile, artifactsLocation.resolve(jarFile.getFileName()));
                for (File artifact : artifacts) {
                    if (artifact.isFile()) {
                        executeShellProcess(
                                ImmutableList.of(javaJarCommand, "uf", newJar.toString(),
                                        "-C", artifactsLocation.toString(), artifact.getName()));
                        LOG.debug("Added file {} to jar {}", artifact, jarFile);
                    }
                }
                return newJar;
            }
        } else {
            LOG.debug("Artifacts directory {} does not exist, not adding any artifacts to jar", artifactsLocation);
        }
        return jarFile;
    }

    @Override
    public void kill (TopologyLayout topology) throws Exception {
        String stormTopologyId = StormTopologyUtil.findStormTopologyId(client, topology.getId());

        boolean killed = client.killTopology(stormTopologyId, DEFAULT_WAIT_TIME_SEC);
        if (!killed) {
            throw new Exception("Topology could not be killed " +
                    "successfully.");
        }
        File artifactsDir = getArtifactsLocation(topology).toFile();
        if (artifactsDir.exists() && artifactsDir.isDirectory()) {
            LOG.debug("Cleaning up {}", artifactsDir);
            FileUtils.cleanDirectory(artifactsDir);
        }
    }

    @Override
    public void validate (TopologyLayout topology) throws Exception {
        Map<String, Object> topologyConfig = topology.getConfig().getProperties();
        if (!topologyConfig.isEmpty()) {
            StormTopologyValidator validator = new StormTopologyValidator(topologyConfig, this.catalogRootUrl);
            validator.validate();
        }
    }

    @Override
    public void suspend (TopologyLayout topology) throws Exception {
        String stormTopologyId = StormTopologyUtil.findStormTopologyId(client, topology.getId());

        boolean suspended = client.deactivateTopology(stormTopologyId);
        if (!suspended) {
            throw new Exception("Topology could not be suspended " +
                    "successfully.");
        }
    }

    @Override
    public void resume (TopologyLayout topology) throws Exception {
        String stormTopologyId = StormTopologyUtil.findStormTopologyId(client, topology.getId());

        boolean resumed = client.activateTopology(stormTopologyId);
        if (!resumed) {
            throw new Exception("Topology could not be resumed " +
                    "successfully.");
        }
    }

    @Override
    public Status status(TopologyLayout topology) throws Exception {
        String stormTopologyId = StormTopologyUtil.findStormTopologyId(client, topology.getId());

        Map topologyStatus = client.getTopology(stormTopologyId);

        StatusImpl status = new StatusImpl();
        status.setStatus((String) topologyStatus.get("status"));
        status.putExtra("Num_tasks", String.valueOf(topologyStatus.get("workersTotal")));
        status.putExtra("Num_workers", String.valueOf(topologyStatus.get("tasksTotal")));
        status.putExtra("Uptime_secs", String.valueOf(topologyStatus.get("uptimeSeconds")));
        return status;
    }

    /**
     * the Path where any topology specific artifacts are kept
     */
    @Override
    public Path getArtifactsLocation(TopologyLayout topology) {
        return Paths.get(stormArtifactsLocation, generateStormTopologyName(topology), "artifacts");
    }

    @Override
    public Path getExtraJarsLocation(TopologyLayout topology) {
        return Paths.get(stormArtifactsLocation, generateStormTopologyName(topology), "jars");
    }

    private String createYamlFile (TopologyLayout topology) throws Exception {
        Map<String, Object> yamlMap;
        File f;
        FileWriter fileWriter = null;
        try {
            f = new File(this.getFilePath(topology));
            if (f.exists()) {
                if (!f.delete()) {
                    throw new Exception("Unable to delete old storm " +
                            "artifact for topology id " + topology.getId());
                }
            }
            yamlMap = new LinkedHashMap<>();
            yamlMap.put(StormTopologyLayoutConstants.YAML_KEY_NAME, generateStormTopologyName(topology));
            TopologyDag topologyDag = topology.getTopologyDag();
            LOG.debug("Initial Topology config {}", topology.getConfig());
            StormTopologyFluxGenerator fluxGenerator = new StormTopologyFluxGenerator(topology, conf, getExtraJarsLocation(topology));
            topologyDag.traverse(fluxGenerator);
            for (Map.Entry<String, Map<String, Object>> entry: fluxGenerator.getYamlKeysAndComponents()) {
                addComponentToCollection(yamlMap, entry.getValue(), entry.getKey());
            }
            Config topologyConfig = fluxGenerator.getTopologyConfig();
            LOG.debug("Final Topology config {}", topologyConfig);
            addTopologyConfig(yamlMap, topologyConfig.getProperties());
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setSplitLines(false);
            //options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
            Yaml yaml = new Yaml (options);
            fileWriter = new FileWriter(f);
            yaml.dump(yamlMap, fileWriter);
            return f.getAbsolutePath();
        } finally {
            if (fileWriter != null) {
                fileWriter.close();
            }
        }
    }

    private String generateStormTopologyName(TopologyLayout topology) {
        return StormTopologyUtil.generateStormTopologyName(topology.getId(), topology.getName());
    }

    private String getFilePath (TopologyLayout topology) {
        return this.stormArtifactsLocation + generateStormTopologyName(topology) + ".yaml";
    }

    // Add topology level configs. catalogRootUrl, hbaseConf, hdfsConf,
    // numWorkers, etc.
    private void addTopologyConfig (Map<String, Object> yamlMap, Map<String, Object> topologyConfig) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put(StormTopologyLayoutConstants.YAML_KEY_CATALOG_ROOT_URL, catalogRootUrl);
        if (topologyConfig != null) {
            config.putAll(topologyConfig);
        }
        yamlMap.put(StormTopologyLayoutConstants.YAML_KEY_CONFIG, config);
    }

    private void addComponentToCollection (Map<String, Object> yamlMap, Map<String, Object> yamlComponent, String collectionKey) {
        if (yamlComponent == null ) {
            return;
        }

        List<Map<String, Object>> components = (List) yamlMap.get(collectionKey);
        if (components == null) {
            components = new ArrayList<>();
            yamlMap.put(collectionKey, components);
        }
        components.add(yamlComponent);
    }


    private ShellProcessResult executeShellProcess (List<String> commands) throws  Exception {
        LOG.debug("Executing command: {}", Joiner.on(" ").join(commands));
        ProcessBuilder processBuilder = new ProcessBuilder(commands);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        StringWriter sw = new StringWriter();
        IOUtils.copy(process.getInputStream(), sw, Charset.defaultCharset());
        String stdout = sw.toString();
        process.waitFor();
        int exitValue = process.exitValue();
        LOG.debug("Command output: {}", stdout);
        LOG.debug("Command exit status: {}", exitValue);
        return new ShellProcessResult(exitValue, stdout);
    }

    private static class ShellProcessResult {
        private final int exitValue;
        private final String stdout;
        ShellProcessResult(int exitValue, String stdout) {
            this.exitValue = exitValue;
            this.stdout = stdout;
        }
    }
}
