/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.streamline.streams.service;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.streamline.common.QueryParam;
import org.apache.streamline.common.util.WSUtils;
import org.apache.streamline.streams.catalog.processor.CustomProcessorInfo;
import org.apache.streamline.streams.catalog.service.StreamCatalogService;
import org.apache.streamline.streams.catalog.topology.TopologyComponentBundle;
import org.apache.commons.lang3.StringUtils;
import org.apache.streamline.streams.layout.exception.ComponentConfigException;
import org.apache.streamline.streams.service.exception.request.BadRequestException;
import org.apache.streamline.streams.service.exception.request.CustomProcessorOnlyException;
import org.apache.streamline.streams.service.exception.request.EntityNotFoundException;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.apache.streamline.common.catalog.CatalogResponse.ResponseMessage.BAD_REQUEST_PARAM_MISSING;
import static org.apache.streamline.common.catalog.CatalogResponse.ResponseMessage.ENTITY_NOT_FOUND;
import static org.apache.streamline.common.catalog.CatalogResponse.ResponseMessage.ENTITY_NOT_FOUND_FOR_FILTER;
import static org.apache.streamline.common.catalog.CatalogResponse.ResponseMessage.EXCEPTION;
import static org.apache.streamline.common.catalog.CatalogResponse.ResponseMessage.SUCCESS;
import static org.apache.streamline.common.catalog.CatalogResponse.ResponseMessage.CUSTOM_PROCESSOR_ONLY;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.OK;

@Path("/v1/catalog/streams")
@Produces(MediaType.APPLICATION_JSON)
public class TopologyComponentBundleResource {
    private static final Logger LOG = LoggerFactory.getLogger(TopologyComponentBundleResource.class);
    public static final String JAR_FILE_PARAM_NAME = "jarFile";
    public static final String CP_INFO_PARAM_NAME = "customProcessorInfo";
    public static final String BUNDLE_JAR_FILE_PARAM_NAME = "bundleJar";
    public static final String TOPOLOGY_COMPONENT_BUNDLE_PARAM_NAME = "topologyComponentBundle";
    private StreamCatalogService catalogService;

    public TopologyComponentBundleResource(StreamCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /**
     * List all component bundle types supported by streams builder
     * <p>
     * GET api/v1/catalog/streams/componentbundles
     * </p>
     * <pre>
     *{"responseCode":1000,"responseMessage":"Success","entities":["SOURCE","PROCESSOR","LINK","SINK","ACTION","TRANSFORM"]}
     * </pre>
     */
    @GET
    @Path("/componentbundles")
    @Timed
    public Response listTopologyComponentBundleTypes () {
        Collection<TopologyComponentBundle.TopologyComponentType>
                topologyComponents = catalogService.listTopologyComponentBundleTypes();
        if (topologyComponents != null) {
            return WSUtils.respondEntities(topologyComponents, OK);
        }

        throw EntityNotFoundException.byFilter("");
    }

    /**
     * List all component bundles registered for a type(SOURCE, SINK, etc) or only the ones that match query params
     * <p>
     * GET api/v1/catalog/streams/componentbundles/SOURCE?name=kafkaSpoutComponent
     * </p>
     */
    @GET
    @Path("/componentbundles/{component}")
    @Timed
    public Response listTopologyComponentBundlesForTypeWithFilter (@PathParam ("component") TopologyComponentBundle.TopologyComponentType componentType,
                                                                   @Context UriInfo uriInfo) {
        List<QueryParam> queryParams;
        MultivaluedMap<String, String> params = uriInfo.getQueryParameters();
        queryParams = WSUtils.buildQueryParameters(params);
        Collection<TopologyComponentBundle> topologyComponentBundles = catalogService
                .listTopologyComponentBundlesForTypeWithFilter(componentType, queryParams);
        if (topologyComponentBundles != null) {
            return WSUtils.respondEntities(topologyComponentBundles, OK);
        }

        throw EntityNotFoundException.byFilter(queryParams.toString());
    }

    /**
     * Get component bundle registered for a type(SOURCE, SINK, etc) matching the id
     * <p>
     * GET api/v1/catalog/streams/componentbundles/SOURCE/5
     * </p>
     */
    @GET
    @Path("/componentbundles/{component}/{id}")
    @Timed
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTopologyComponentBundleById (@PathParam("component") TopologyComponentBundle.TopologyComponentType componentType, @PathParam ("id")
    Long id) {
        TopologyComponentBundle result = catalogService.getTopologyComponentBundle(id);
        if (result != null) {
            return WSUtils.respondEntity(result, OK);
        }

        throw EntityNotFoundException.byId(id.toString());
    }

    /**
     * Add a new topology component bundle.
     * <p>
     * curl -sS -X POST -i -F topologyComponentBundle=@kafka-topology-bundle -F bundleJar=@/Users/pshah/dev/IoTaS/streams/runners/storm/layout/target/streams-layout-storm-0.1.0-SNAPSHOT.jar  http://localhost:8080/api/v1/catalog/streams/componentbundles/SOURCE/
     * </p>
     */
    @POST
    @Path("/componentbundles/{component}")
    @Timed
    public Response addTopologyComponentBundle (@PathParam("component") TopologyComponentBundle.TopologyComponentType componentType, FormDataMultiPart form) throws IOException, ComponentConfigException {
        InputStream bundleJar = null;
        try {
            String bundleJsonString = this.getFormDataFromMultiPartRequestAs(String.class, form,
                    TOPOLOGY_COMPONENT_BUNDLE_PARAM_NAME);
            TopologyComponentBundle topologyComponentBundle = new ObjectMapper().readValue(bundleJsonString, TopologyComponentBundle.class);
            if (topologyComponentBundle == null) {
                LOG.debug(TOPOLOGY_COMPONENT_BUNDLE_PARAM_NAME + " is missing or invalid");
                throw BadRequestException.missingParameter(TOPOLOGY_COMPONENT_BUNDLE_PARAM_NAME);
            }
            if (!topologyComponentBundle.getBuiltin()) {
                bundleJar = this.getFormDataFromMultiPartRequestAs(InputStream.class, form, BUNDLE_JAR_FILE_PARAM_NAME);
                if (bundleJar == null) {
                    LOG.debug(BUNDLE_JAR_FILE_PARAM_NAME + " is missing or invalid");
                    throw BadRequestException.missingParameter(BUNDLE_JAR_FILE_PARAM_NAME);
                }
            }
            validateTopologyBundle(topologyComponentBundle);
            topologyComponentBundle.setType(componentType);
            TopologyComponentBundle createdBundle = catalogService.addTopologyComponentBundle(topologyComponentBundle, bundleJar);
            return WSUtils.respondEntity(createdBundle, CREATED);
        } catch (Exception e) {
            LOG.debug("Error occured while adding topology component bundle", e);
            throw e;
        } finally {
            try {
                if (bundleJar != null) {
                    bundleJar.close();
                }
            } catch (IOException e) {
                LOG.debug("Error while closing jar file stream", e);
            }
        }
    }

    /**
     * Update a topology component bundle.
     * <p>
     * curl -sS -X PUT -i -F topologyComponentBundle=@kafka-topology-bundle -F bundleJar=@/Users/pshah/dev/IoTaS/streams/runners/storm/layout/target/streams-layout-storm-0.1.0-SNAPSHOT.jar  http://localhost:8080/api/v1/catalog/streams/componentbundles/SOURCE/
     * </p>
     */
    @PUT
    @Path("/componentbundles/{component}/{id}")
    @Timed
    public Response addOrUpdateTopologyComponentBundle (@PathParam("component")
                                                        TopologyComponentBundle.TopologyComponentType componentType, @PathParam("id") Long id,
                                                        FormDataMultiPart form) throws IOException, ComponentConfigException {
        InputStream bundleJar = null;
        try {
            String bundleJsonString = this.getFormDataFromMultiPartRequestAs(String.class, form, TOPOLOGY_COMPONENT_BUNDLE_PARAM_NAME);
            TopologyComponentBundle topologyComponentBundle = new ObjectMapper().readValue(bundleJsonString, TopologyComponentBundle.class);
            if (topologyComponentBundle == null) {
                LOG.debug(TOPOLOGY_COMPONENT_BUNDLE_PARAM_NAME + " is missing or invalid");
                throw BadRequestException.missingParameter(TOPOLOGY_COMPONENT_BUNDLE_PARAM_NAME);
            }
            if (!topologyComponentBundle.getBuiltin()) {
                bundleJar = this.getFormDataFromMultiPartRequestAs(InputStream.class, form, BUNDLE_JAR_FILE_PARAM_NAME);
                if (bundleJar == null) {
                    LOG.debug(BUNDLE_JAR_FILE_PARAM_NAME + " is missing or invalid");
                    throw BadRequestException.missingParameter(BUNDLE_JAR_FILE_PARAM_NAME);
                }
            }
            validateTopologyBundle(topologyComponentBundle);
            topologyComponentBundle.setType(componentType);
            TopologyComponentBundle updatedBundle = catalogService.addOrUpdateTopologyComponentBundle(id, topologyComponentBundle, bundleJar);
            return WSUtils.respondEntity(updatedBundle, OK);
        } catch (Exception e) {
            LOG.debug("Error occured while updating topology component bundle", e);
            throw e;
        } finally {
            try {
                if (bundleJar != null) {
                    bundleJar.close();
                }
            } catch (IOException e) {
                LOG.debug("Error while closing jar file stream", e);
            }
        }
    }
    /**
     * Delete a topology component bundle.
     * <p>
     * curl -sS -X DELETE -i   http://localhost:8080/api/v1/catalog/streams/componentbundles/SOURCE/3
     * </p>
     */
    @DELETE
    @Path("/componentbundles/{component}/{id}")
    @Timed
    public Response removeTopologyComponentBundle (@PathParam("component") TopologyComponentBundle.TopologyComponentType componentType, @PathParam ("id")
    Long id) throws IOException {
        TopologyComponentBundle removedTopologyComponentBundle = catalogService.removeTopologyComponentBundle(id);
        if (removedTopologyComponentBundle != null) {
            return WSUtils.respondEntity(removedTopologyComponentBundle, OK);
        }

        throw EntityNotFoundException.byId(id.toString());
    }

    @Timed
    @GET
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Path("/componentbundles/{processor}/custom/{fileName}")
    public Response downloadCustomProcessorFile (@PathParam("processor") TopologyComponentBundle.TopologyComponentType componentType, @PathParam
            ("fileName") String fileName) throws IOException {
        if (!TopologyComponentBundle.TopologyComponentType.PROCESSOR.equals(componentType)) {
            throw new CustomProcessorOnlyException();
        }
        final InputStream inputStream = catalogService.getFileFromJarStorage(fileName);
        if (inputStream != null) {
            StreamingOutput streamOutput = WSUtils.wrapWithStreamingOutput(inputStream);
            return Response.ok(streamOutput).build();
        }

        throw EntityNotFoundException.byId(fileName);
    }

    /**
     * List custom processors matching specific query parameter filters.
     */
    @GET
    @Path("/componentbundles/{processor}/custom")
    @Timed
    public Response listCustomProcessorsWithFilters (@PathParam("processor") TopologyComponentBundle.TopologyComponentType componentType, @Context UriInfo uriInfo) throws IOException {
        if (!TopologyComponentBundle.TopologyComponentType.PROCESSOR.equals(componentType)) {
            throw new CustomProcessorOnlyException();
        }
        List<QueryParam> queryParams;
        MultivaluedMap<String, String> params = uriInfo.getQueryParameters();
        queryParams = WSUtils.buildQueryParameters(params);
        Collection<CustomProcessorInfo> customProcessorInfos = catalogService.listCustomProcessorsFromBundleWithFilter(queryParams);
        if (customProcessorInfos != null) {
            return WSUtils.respondEntities(customProcessorInfos, OK);
        }

        throw EntityNotFoundException.byFilter(queryParams.toString());
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Path("/componentbundles/{processor}/custom")
    @Timed
    public Response addCustomProcessor (@PathParam("processor") TopologyComponentBundle.TopologyComponentType componentType, FormDataMultiPart form) throws IOException, ComponentConfigException {
        if (!TopologyComponentBundle.TopologyComponentType.PROCESSOR.equals(componentType)) {
            throw new CustomProcessorOnlyException();
        }
        InputStream jarFile = null;
        try {
            jarFile = this.getFormDataFromMultiPartRequestAs(InputStream.class, form, JAR_FILE_PARAM_NAME);
            String customProcessorInfoStr = this.getFormDataFromMultiPartRequestAs(String.class, form, CP_INFO_PARAM_NAME);
            String missingParam = (jarFile == null ? JAR_FILE_PARAM_NAME : (customProcessorInfoStr == null ? CP_INFO_PARAM_NAME : null));
            if (missingParam != null) {
                LOG.debug(missingParam + " is missing or invalid while adding custom processor");
                throw BadRequestException.missingParameter(missingParam);
            }
            CustomProcessorInfo customProcessorInfo = new ObjectMapper().readValue(customProcessorInfoStr, CustomProcessorInfo.class);
            CustomProcessorInfo createdCustomProcessor = catalogService.addCustomProcessorInfoAsBundle(customProcessorInfo, jarFile);
            return WSUtils.respondEntity(createdCustomProcessor, CREATED);
        } catch (Exception e) {
            LOG.debug("Exception thrown while trying to add a custom processor", e);
            throw e;
        } finally {
            try {
                jarFile.close();
            } catch (IOException e) {
                LOG.debug("Error while closing jar file stream", e);
            }
        }
    }

    @PUT
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Path("/componentbundles/{processor}/custom")
    @Timed
    public Response updateCustomProcessor (@PathParam("processor") TopologyComponentBundle.TopologyComponentType componentType, FormDataMultiPart form)
            throws IOException, ComponentConfigException {
        if (!TopologyComponentBundle.TopologyComponentType.PROCESSOR.equals(componentType)) {
            throw new CustomProcessorOnlyException();
        }
        InputStream jarFile = null;
        try {
            jarFile = this.getFormDataFromMultiPartRequestAs(InputStream.class, form, JAR_FILE_PARAM_NAME);
            String customProcessorInfoStr = this.getFormDataFromMultiPartRequestAs(String.class, form, CP_INFO_PARAM_NAME);
            String missingParam = (jarFile == null ? JAR_FILE_PARAM_NAME : (customProcessorInfoStr == null ? CP_INFO_PARAM_NAME : null));
            if (missingParam != null) {
                LOG.debug(missingParam + " is missing or invalid while adding/updating custom processor");
                throw BadRequestException.missingParameter(missingParam);
            }
            CustomProcessorInfo customProcessorInfo = new ObjectMapper().readValue(customProcessorInfoStr, CustomProcessorInfo.class);
            CustomProcessorInfo updatedCustomProcessor = catalogService.updateCustomProcessorInfoAsBundle(customProcessorInfo, jarFile);
            return WSUtils.respondEntity(updatedCustomProcessor, OK);
        } catch (Exception e) {
            LOG.debug("Exception thrown while trying to add/update a custom processor", e);
            throw e;
        } finally {
            try {
                jarFile.close();
            } catch (IOException e) {
                LOG.debug("Error while closing jar file stream", e);
            }
        }
    }

    @DELETE
    @Path("/componentbundles/{processor}/custom/{name}")
    @Timed
    public Response removeCustomProcessorInfo (@PathParam("processor") TopologyComponentBundle.TopologyComponentType componentType, @PathParam ("name") String
            name) throws IOException {
        if (!TopologyComponentBundle.TopologyComponentType.PROCESSOR.equals(componentType)) {
            throw new CustomProcessorOnlyException();
        }
        CustomProcessorInfo removedCustomProcessorInfo = catalogService.removeCustomProcessorInfoAsBundle(name);
        if (removedCustomProcessorInfo != null) {
            return WSUtils.respondEntity(removedCustomProcessorInfo, OK);
        }

        throw EntityNotFoundException.byName(name);
    }

    private void validateTopologyBundle (TopologyComponentBundle topologyComponentBundle) {
        Optional<String> missingParam = Optional.empty();
        if (StringUtils.isEmpty(topologyComponentBundle.getName())) {
            missingParam = Optional.of(TopologyComponentBundle.NAME);
        }
        if (StringUtils.isEmpty(topologyComponentBundle.getStreamingEngine())) {
            missingParam = Optional.of(TopologyComponentBundle.STREAMING_ENGINE);
        }
        if (StringUtils.isEmpty(topologyComponentBundle.getSubType())) {
            missingParam = Optional.of(TopologyComponentBundle.SUB_TYPE);
        }
        if (StringUtils.isEmpty(topologyComponentBundle.getTransformationClass())) {
            missingParam = Optional.of(TopologyComponentBundle.TRANSFORMATION_CLASS);
        }
        if (topologyComponentBundle.getTopologyComponentUISpecification() == null) {
            missingParam = Optional.of(TopologyComponentBundle.UI_SPECIFICATION);
        }

        if (missingParam.isPresent()) {
            throw BadRequestException.missingParameter(missingParam.get());
        }
    }

    private <T> T getFormDataFromMultiPartRequestAs (Class<T> clazz, FormDataMultiPart form, String paramName) {
        T result = null;
        try {
            FormDataBodyPart part = form.getField(paramName);
            if (part != null) {
                result = part.getValueAs(clazz);
            }
        } catch (Exception e) {
            LOG.debug("Cannot get param " + paramName + " as" + clazz + " from multipart form" );
        }
        return result;
    }
}


