/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.apm.server.rest;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import org.hawkular.apm.api.model.config.CollectorConfiguration;
import org.hawkular.apm.api.model.config.btxn.BusinessTxnConfig;
import org.hawkular.apm.api.model.config.btxn.BusinessTxnSummary;
import org.hawkular.apm.api.model.config.btxn.ConfigMessage;
import org.hawkular.apm.api.services.ConfigurationService;
import org.hawkular.apm.server.api.security.SecurityProvider;
import org.jboss.logging.Logger;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * REST interface for administration capabilities.
 *
 * @author gbrown
 *
 */
@Path("config")
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Api(value = "config", description = "Configuration")
public class ConfigurationHandler {

    private static final Logger log = Logger.getLogger(ConfigurationHandler.class);

    @Inject
    SecurityProvider securityProvider;

    @Inject
    ConfigurationService configService;

    @GET
    @Path("collector")
    @Produces(APPLICATION_JSON)
    @ApiOperation(
            value = "Retrieve the collector configuration for the optionally specified host and server",
            response = CollectorConfiguration.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success"),
            @ApiResponse(code = 500, message = "Internal server error") })
    public void getCollectorConfiguration(
            @Context SecurityContext context, @HeaderParam("Hawkular-Tenant") String tenantId,
            @Suspended final AsyncResponse response,
            @ApiParam(required = false,
            value = "optional type") @QueryParam("type") String type,
            @ApiParam(required = false,
            value = "optional host name") @QueryParam("host") String host,
            @ApiParam(required = false,
            value = "optional server name") @QueryParam("server") String server) {

        try {
            log.tracef("Get collector configuration for type [%s] host [%s] server [%s]", type, host, server);

            CollectorConfiguration config = configService.getCollector(
                    securityProvider.validate(tenantId, context.getUserPrincipal().getName()), type, host, server);

            log.tracef("Got collector configuration for type [%s] host [%s] server [%s] config=[%s]",
                            type, host, server, config);

            response.resume(Response.status(Response.Status.OK).entity(config).type(APPLICATION_JSON_TYPE)
                    .build());

        } catch (Throwable e) {
            log.debug(e.getMessage(), e);
            Map<String, String> errors = new HashMap<String, String>();
            errors.put("errorMsg", "Internal Error: " + e.getMessage());
            response.resume(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(errors).type(APPLICATION_JSON_TYPE).build());
        }

    }

    @GET
    @Path("businesstxn/summary")
    @Produces(APPLICATION_JSON)
    @ApiOperation(
            value = "Retrieve the business transaction summaries",
            response = List.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success"),
            @ApiResponse(code = 500, message = "Internal server error") })
    public void getBusinessTxnConfigurationSummaries(
            @Context SecurityContext context, @HeaderParam("Hawkular-Tenant") String tenantId,
            @Suspended final AsyncResponse response) {

        try {
            log.tracef("Get business transaction summaries");

            List<BusinessTxnSummary> summaries = configService.getBusinessTransactionSummaries(
                    securityProvider.validate(tenantId, context.getUserPrincipal().getName()));

            // Sort the list
            Collections.sort(summaries, new Comparator<BusinessTxnSummary>() {
                @Override
                public int compare(BusinessTxnSummary arg0, BusinessTxnSummary arg1) {
                    if (arg0.getName() == null || arg1.getName() == null) {
                        return 0;
                    }
                    return arg0.getName().compareTo(arg1.getName());
                }
            });

            log.tracef("Got business transaction summaries=[%s]", summaries);

            response.resume(Response.status(Response.Status.OK).entity(summaries).type(APPLICATION_JSON_TYPE)
                    .build());

        } catch (Throwable e) {
            log.debug(e.getMessage(), e);
            Map<String, String> errors = new HashMap<String, String>();
            errors.put("errorMsg", "Internal Error: " + e.getMessage());
            response.resume(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(errors).type(APPLICATION_JSON_TYPE).build());
        }
    }

    @GET
    @Path("businesstxn/full")
    @Produces(APPLICATION_JSON)
    @ApiOperation(
            value = "Retrieve the business transaction configurations, changed since an optional specified time",
            response = Map.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success"),
            @ApiResponse(code = 500, message = "Internal server error") })
    public void getBusinessTxnConfigurations(
            @Context SecurityContext context, @HeaderParam("Hawkular-Tenant") String tenantId,
            @Suspended final AsyncResponse response,
            @ApiParam(required = false,
                    value = "updated since") @QueryParam("updated") @DefaultValue("0") long updated) {

        try {
            log.tracef("Get business transactions, updated = [%s]", updated);

            Map<String, BusinessTxnConfig> btxns = configService.getBusinessTransactions(
                    securityProvider.validate(tenantId, context.getUserPrincipal().getName()), updated);

            log.tracef("Got business transactions=[%s]", btxns);

            response.resume(Response.status(Response.Status.OK).entity(btxns).type(APPLICATION_JSON_TYPE)
                    .build());

        } catch (Throwable e) {
            log.debug(e.getMessage(), e);
            Map<String, String> errors = new HashMap<String, String>();
            errors.put("errorMsg", "Internal Error: " + e.getMessage());
            response.resume(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(errors).type(APPLICATION_JSON_TYPE).build());
        }
    }

    @GET
    @Path("businesstxn/full/{name}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(
            value = "Retrieve the business transaction configuration for the specified name",
            response = BusinessTxnConfig.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success"),
            @ApiResponse(code = 500, message = "Internal server error") })
    public void getBusinessTxnConfiguration(
            @Context SecurityContext context, @HeaderParam("Hawkular-Tenant") String tenantId,
            @Suspended final AsyncResponse response,
            @ApiParam(required = true,
            value = "business transaction name") @PathParam("name") String name) {

        try {
            log.tracef("Get business transaction configuration for name [%s]", name);

            BusinessTxnConfig config = configService.getBusinessTransaction(
                    securityProvider.validate(tenantId, context.getUserPrincipal().getName()), name);

            log.tracef("Got business transaction configuration for name [%s] config=[%s]", name, config);

            response.resume(Response.status(Response.Status.OK).entity(config).type(APPLICATION_JSON_TYPE)
                    .build());

        } catch (Throwable e) {
            log.debug(e.getMessage(), e);
            Map<String, String> errors = new HashMap<String, String>();
            errors.put("errorMsg", "Internal Error: " + e.getMessage());
            response.resume(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(errors).type(APPLICATION_JSON_TYPE).build());
        }
    }

    @PUT
    @Path("businesstxn/full/{name}")
    @Consumes(APPLICATION_JSON)
    @ApiOperation(
            value = "Add or update the business transaction configuration for the specified name",
            response = List.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success"),
            @ApiResponse(code = 500, message = "Internal server error") })
    public void setBusinessTxnConfiguration(
            @Context SecurityContext context, @HeaderParam("Hawkular-Tenant") String tenantId,
            @Suspended final AsyncResponse response,
            @ApiParam(required = true,
            value = "business transaction name") @PathParam("name") String name,
            BusinessTxnConfig config) {

        try {
            log.tracef("About to set business transaction configuration for name [%s] config=[%s]", name,
                    config);

            List<ConfigMessage> messages = configService.setBusinessTransaction(
                    securityProvider.validate(tenantId, context.getUserPrincipal().getName()), name, config);

            log.tracef("Updated business transaction configuration for name [%s] messages=[%s]", name, messages);

            response.resume(Response.status(Response.Status.OK).entity(messages)
                    .build());

        } catch (Throwable e) {
            log.debug(e.getMessage(), e);
            Map<String, String> errors = new HashMap<String, String>();
            errors.put("errorMsg", "Internal Error: " + e.getMessage());
            response.resume(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(errors).build());
        }
    }

    @POST
    @Path("businesstxn/full")
    @Consumes(APPLICATION_JSON)
    @ApiOperation(
            value = "Add or update the business transaction configurations",
            response = List.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success"),
            @ApiResponse(code = 500, message = "Internal server error") })
    public void setBusinessTxnConfigurations(
            @Context SecurityContext context, @HeaderParam("Hawkular-Tenant") String tenantId,
            @Suspended final AsyncResponse response,
            Map<String, BusinessTxnConfig> btxnConfigs) {

        try {
            log.tracef("About to set business transaction configurations=[%s]",
                    btxnConfigs);

            List<ConfigMessage> messages = configService.setBusinessTransactions(
                        securityProvider.validate(tenantId, context.getUserPrincipal().getName()), btxnConfigs);

            log.tracef("Updated business transaction configurations : messages=[%s]", messages);

            response.resume(Response.status(Response.Status.OK).entity(messages)
                    .build());

        } catch (Throwable e) {
            log.debug(e.getMessage(), e);
            Map<String, String> errors = new HashMap<String, String>();
            errors.put("errorMsg", "Internal Error: " + e.getMessage());
            response.resume(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(errors).build());
        }
    }

    @DELETE
    @Path("businesstxn/full/{name}")
    @ApiOperation(
            value = "Remove the business transaction configuration with the specified name")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success"),
            @ApiResponse(code = 500, message = "Internal server error") })
    public void removeBusinessTxnConfiguration(
            @Context SecurityContext context, @HeaderParam("Hawkular-Tenant") String tenantId,
            @Suspended final AsyncResponse response,
            @ApiParam(required = true,
            value = "business transaction name") @PathParam("name") String name) {

        try {
            log.tracef("About to remove business transaction configuration for name [%s]", name);

            configService.removeBusinessTransaction(
                    securityProvider.validate(tenantId, context.getUserPrincipal().getName()), name);

            response.resume(Response.status(Response.Status.OK)
                    .build());

        } catch (Throwable e) {
            log.debug(e.getMessage(), e);
            Map<String, String> errors = new HashMap<String, String>();
            errors.put("errorMsg", "Internal Error: " + e.getMessage());
            response.resume(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(errors).build());
        }
    }

    @POST
    @Path("businesstxn/validate")
    @Consumes(APPLICATION_JSON)
    @ApiOperation(
            value = "Validate the business transaction configuration",
            response = List.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success"),
            @ApiResponse(code = 500, message = "Internal server error") })
    public void validateBusinessTxnConfiguration(
            @Context SecurityContext context, @HeaderParam("Hawkular-Tenant") String tenantId,
            @Suspended final AsyncResponse response,
            BusinessTxnConfig config) {

        try {
            log.tracef("Validate business transaction configuration=[%s]", config);

            List<ConfigMessage> messages = configService.validateBusinessTransaction(config);

            log.tracef("Validated business transaction configuration: messages=[%s]", messages);

            response.resume(Response.status(Response.Status.OK).entity(messages)
                    .build());

        } catch (Throwable e) {
            log.debug(e.getMessage(), e);
            Map<String, String> errors = new HashMap<String, String>();
            errors.put("errorMsg", "Internal Error: " + e.getMessage());
            response.resume(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(errors).build());
        }
    }

    @DELETE
    @Path("/")
    @Produces(APPLICATION_JSON)
    public void clear(
            @Context SecurityContext context, @HeaderParam("Hawkular-Tenant") String tenantId,
            @Suspended final AsyncResponse response) {

        try {
            if (System.getProperties().containsKey("hawkular-apm.testmode")) {
                configService.clear(securityProvider.validate(tenantId, context.getUserPrincipal().getName()));

                response.resume(Response.status(Response.Status.OK).type(APPLICATION_JSON_TYPE)
                        .build());
            } else {
                response.resume(Response.status(Response.Status.FORBIDDEN).type(APPLICATION_JSON_TYPE)
                        .build());
            }

        } catch (Throwable e) {
            log.debug(e.getMessage(), e);
            Map<String, String> errors = new HashMap<String, String>();
            errors.put("errorMsg", "Internal Error: " + e.getMessage());
            response.resume(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(errors).type(APPLICATION_JSON_TYPE).build());
        }

    }

}
