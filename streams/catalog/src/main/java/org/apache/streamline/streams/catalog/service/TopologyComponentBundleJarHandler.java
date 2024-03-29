package org.apache.streamline.streams.catalog.service;

import org.apache.streamline.streams.catalog.topology.TopologyComponentBundle;
import org.apache.streamline.streams.layout.component.Edge;
import org.apache.streamline.streams.layout.component.StreamlineComponent;
import org.apache.streamline.streams.layout.component.StreamlineProcessor;
import org.apache.streamline.streams.layout.component.StreamlineSink;
import org.apache.streamline.streams.layout.component.StreamlineSource;
import org.apache.streamline.streams.layout.component.TopologyDagVisitor;
import org.apache.streamline.streams.layout.component.impl.RulesProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;


public class TopologyComponentBundleJarHandler extends TopologyDagVisitor {

    private static final Logger LOG = LoggerFactory.getLogger(TopologyComponentBundleJarHandler.class);
    private final StreamCatalogService streamCatalogService;
    private Set<TopologyComponentBundle> topologyComponentBundleSet = new HashSet<>();
    TopologyComponentBundleJarHandler(StreamCatalogService streamCatalogService) {
        this.streamCatalogService = streamCatalogService;
    }

    @Override
    public void visit (RulesProcessor rulesProcessor) {
        handleIotasComponentBundle(rulesProcessor);
    }

    @Override
    public void visit (StreamlineSource streamlineSource) {
        handleIotasComponentBundle(streamlineSource);
    }

    @Override
    public void visit (StreamlineSink streamlineSink) {
        handleIotasComponentBundle(streamlineSink);
    }

    @Override
    public void visit (StreamlineProcessor streamlineProcessor) {
        handleIotasComponentBundle(streamlineProcessor);
    }

    @Override
    public void visit (Edge edge) {

    }

    private void handleIotasComponentBundle (StreamlineComponent streamlineComponent) {
        TopologyComponentBundle topologyComponentBundle = streamCatalogService.getTopologyComponentBundle(Long.parseLong(streamlineComponent.
                getTopologyComponentBundleId()));
        if (topologyComponentBundle == null) {
            throw new RuntimeException("Likely to run in to issues while deployging topology since TopologyComponentBundle not found for id " +
                    streamlineComponent.getTopologyComponentBundleId());
        }
        if (!topologyComponentBundle.getBuiltin()) {
            topologyComponentBundleSet.add(topologyComponentBundle);
        } else {
            LOG.debug("No need to copy any jar for {} since its a builtin bundle", streamlineComponent);
        }
    }

    public Set<TopologyComponentBundle>  getTopologyComponentBundleSet () {
        return topologyComponentBundleSet;
    }

}
