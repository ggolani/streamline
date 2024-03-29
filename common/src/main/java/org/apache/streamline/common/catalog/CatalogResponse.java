package org.apache.streamline.common.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collection;


/**
 * <p>
 * A wrapper entity for passing entities and status back to the client.
 * </p>
 *
 * FIXME: This is used only from parser-registry and should be removed after STREAMLINE-435 is merged to master
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CatalogResponse {

    /**
     * ResponseMessage args if any should always be string to keep it simple.
     */
    public enum ResponseMessage {
        /* 1000 to 1100 reserved for success status messages */
        SUCCESS(1000, "Success", 0),
        /* 1101 onwards for error messages */
        ENTITY_NOT_FOUND(1101, "Entity with id [%s] not found.", 1),
        EXCEPTION(1102, "An exception with message [%s] was thrown while processing request.", 1),
        BAD_REQUEST_PARAM_MISSING(1103, "Bad request. Param [%s] is missing or empty.", 1),
        ENTITY_NOT_FOUND_FOR_FILTER(1105, "Entity not found for query params [%s].", 1),
        PARSER_SCHEMA_FOR_ENTITY_NOT_FOUND(1106, "Parser schema not found for entity with id [%s].", 1),
        CUSTOM_PROCESSOR_ONLY(1108, "Custom endpoint supported only for processors.", 0),
        UNSUPPORTED_MEDIA_TYPE(1109, "Unsupported Media Type", 0),
        BAD_REQUEST(1110, "Bad Request", 0),
        IMPORT_ALREADY_IN_PROGRESS(1111, "Cluster [%s] is already in progress of import.", 1),
        ENTITY_BY_NAME_NOT_FOUND(1112, "Entity with name [%s] not found.", 1),
        ENTITY_VERSION_NOT_FOUND(1113, "Entity with id [%s] and version [%s] not found.", 2),
        ENTITY_BY_NAME_ALREADY_EXIST(1114, "Entity with name [%s] already exists.", 1);

        private final int code;
        private final String msg;
        private final int nargs;

        ResponseMessage(int code, String msg, int nargs) {
            this.code = code;
            this.msg = msg;
            this.nargs = nargs;
        }

        /*
         * whether an error message or just a status.
         */
        private boolean isError() {
            return code > 1100;
        }

        public int getCode() {
            return code;
        }

        public static String format(ResponseMessage responseMessage, String... args) {
            //TODO: validate number of args
            return String.format(responseMessage.msg, args);
        }

    }

    /**
     * Response code.
     */
    private int responseCode;
    /**
     * Response message.
     */
    private String responseMessage;
    /**
     * For response that returns a single entity.
     */
    private Object entity;
    /**
     * For response that returns a collection of entities.
     */
    private Collection<?> entities;

    private CatalogResponse() {}

    public static class Builder {
        private final ResponseMessage responseMessage;
        private Object entity;
        private Collection<?> entities;
        private final String DOC_LINK_MESSAGE = " Please check webservice/ErrorCodes.md for more details.";

        public Builder(ResponseMessage responseMessage) {
            this.responseMessage = responseMessage;
        }

        public Builder entity(Object entity) {
            this.entity = entity;
            return this;
        }

        public Builder entities(Collection<?> entities) {
            this.entities = entities;
            return this;
        }

        public CatalogResponse format(String... args) {
            CatalogResponse response = new CatalogResponse();
            response.responseCode = responseMessage.code;
            StringBuilder msg = new StringBuilder(ResponseMessage.format(responseMessage, args));
            if(responseMessage.isError()) {
                msg.append(DOC_LINK_MESSAGE);
            }
            response.responseMessage = msg.toString();
            response.entity = entity;
            response.entities = entities;
            return response;
        }
    }

    public static Builder newResponse(ResponseMessage msg) {
        return new Builder(msg);
    }

    public String getResponseMessage() {
        return responseMessage;
    }

    public int getResponseCode() {
        return responseCode;
    }

    public Object getEntity() {
        return entity;
    }


    public Collection<?> getEntities() {
        return entities;
    }


}
