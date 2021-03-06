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
package org.hawkular.apm.api.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.inject.Inject;

import org.hawkular.apm.api.model.Property;
import org.hawkular.apm.api.model.analytics.CommunicationSummaryStatistics;
import org.hawkular.apm.api.model.analytics.CommunicationSummaryStatistics.ConnectionStatistics;
import org.hawkular.apm.api.model.analytics.EndpointInfo;
import org.hawkular.apm.api.model.analytics.PropertyInfo;
import org.hawkular.apm.api.model.config.btxn.BusinessTxnConfig;
import org.hawkular.apm.api.model.trace.Consumer;
import org.hawkular.apm.api.model.trace.ContainerNode;
import org.hawkular.apm.api.model.trace.Node;
import org.hawkular.apm.api.model.trace.Producer;
import org.hawkular.apm.api.model.trace.Trace;
import org.hawkular.apm.api.services.internal.CommunicationSeverityAnalyser;
import org.hawkular.apm.api.services.internal.CommunicationSummaryTreeBuilder;
import org.hawkular.apm.api.utils.EndpointUtil;

/**
 * The abstract base class for implementations of the Analytics Service.
 *
 * @author gbrown
 */
public abstract class AbstractAnalyticsService implements AnalyticsService {

    private static final Logger log = Logger.getLogger(AbstractAnalyticsService.class.getName());

    @Inject
    private ConfigurationService configService;

    private CommunicationSeverityAnalyser communicationSeverityAnalyser = new CommunicationSeverityAnalyser();

    /**
     * This method gets the configuration service.
     *
     * @return The configuration service
     */
    public ConfigurationService getConfigurationService() {
        return this.configService;
    }

    /**
     * This method sets the configuration service.
     *
     * @param cs The configuration service
     */
    public void setConfigurationService(ConfigurationService cs) {
        this.configService = cs;
    }

    /**
     * This method returns the list of traces for the supplied criteria.
     *
     * @param tenantId The tenant
     * @param criteria The criteria
     * @return The list of fragments
     */
    protected abstract List<Trace> getFragments(String tenantId, Criteria criteria);

    /* (non-Javadoc)
     * @see org.hawkular.apm.api.services.AnalyticsService#getUnboundEndpoints(java.lang.String,
     *                                  long, long, boolean)
     */
    @Override
    public List<EndpointInfo> getUnboundEndpoints(String tenantId, long startTime, long endTime, boolean compress) {
        Criteria criteria = new Criteria();
        criteria.setStartTime(startTime).setEndTime(endTime);

        List<Trace> fragments = getFragments(tenantId, criteria);

        return (doGetUnboundEndpoints(tenantId, fragments, compress));
    }

    /* (non-Javadoc)
     * @see org.hawkular.apm.api.services.AnalyticsService#getBoundEndpoints(java.lang.String,
     *                               java.lang.String, long, long)
     */
    @Override
    public List<EndpointInfo> getBoundEndpoints(String tenantId, String businessTransaction, long startTime,
                                    long endTime) {
        List<EndpointInfo> ret = new ArrayList<EndpointInfo>();

        Criteria criteria = new Criteria();
        criteria.setBusinessTransaction(businessTransaction)
        .setStartTime(startTime)
        .setEndTime(endTime);

        List<Trace> fragments = getFragments(tenantId, criteria);

        for (int i = 0; i < fragments.size(); i++) {
            Trace trace = fragments.get(i);
            obtainEndpoints(trace.getNodes(), ret);
        }

        return ret;
    }

    /* (non-Javadoc)
     * @see org.hawkular.apm.api.services.AnalyticsService#getPropertyInfo(java.lang.String,
     *                  org.hawkular.apm.api.services.Criteria)
     */
    @Override
    public List<PropertyInfo> getPropertyInfo(String tenantId, Criteria criteria) {
        List<PropertyInfo> ret = new ArrayList<PropertyInfo>();
        List<String> propertyNames = new ArrayList<String>();

        List<Trace> fragments = getFragments(tenantId, criteria);

        // Process the fragments to identify which URIs are no used in any trace
        for (int i = 0; i < fragments.size(); i++) {
            Trace trace = fragments.get(i);

            for (Property property : trace.getProperties()) {
                if (!propertyNames.contains(property.getName())) {
                    propertyNames.add(property.getName());
                    PropertyInfo pi = new PropertyInfo();
                    pi.setName(property.getName());
                    ret.add(pi);
                }
            }
        }

        Collections.sort(ret, new Comparator<PropertyInfo>() {
            @Override
            public int compare(PropertyInfo arg0, PropertyInfo arg1) {
                return arg0.getName().compareTo(arg1.getName());
            }
        });

        return ret;
    }

    // Don't collapse if a part node has a certain level of count, as may indicate a common component
    // Other indicate to collapse may be if multiple parts share the same child name? - but may only be
    // obvious after collapse - but could do a test collapse, and then check the result before making
    // permanent.
    // May need to experiment with different rules

    // Initial rule - collapse if a node contains more than a threshold of children

    /**
     * This method compresses the list of endpoints to identify
     * common patterns.
     *
     * @param endpoints The endpoints
     * @return The compressed list of endpoints
     */
    protected static List<EndpointInfo> compressEndpointInfo(List<EndpointInfo> endpoints) {
        List<EndpointInfo> others = new ArrayList<EndpointInfo>();

        EndpointPart rootPart = new EndpointPart();

        for (int i = 0; i < endpoints.size(); i++) {
            EndpointInfo endpoint = endpoints.get(i);

            if (endpoint.getEndpoint() != null
                    && endpoint.getEndpoint().length() > 1
                    && endpoint.getEndpoint().charAt(0) == '/') {
                String[] parts = endpoint.getEndpoint().split("/");

                buildTree(rootPart, parts, 1, endpoint.getType());

            } else {
                others.add(new EndpointInfo(endpoint));
            }
        }

        // Construct new list
        List<EndpointInfo> info = null;

        if (endpoints.size() != others.size()) {
            rootPart.collapse();

            info = extractEndpointInfo(rootPart);

            info.addAll(others);
        } else {
            info = others;
        }

        // Initialise the endpoint info
        initEndpointInfo(info);

        return info;
    }

    /**
     * This method builds a tree.
     *
     * @param parent The current parent node
     * @param parts The parts of the URI being processed
     * @param index The current index into the parts array
     * @param endpointType The endpoint type
     */
    protected static void buildTree(EndpointPart parent, String[] parts, int index, String endpointType) {
        // Check if operation qualifier is part of last element
        String name = parts[index];
        String qualifier = null;

        if (index == parts.length - 1) {
            qualifier = EndpointUtil.decodeEndpointOperation(parts[index], false);
            name = EndpointUtil.decodeEndpointURI(parts[index]);
        }

        // Check if part is defined in the parent
        EndpointPart child = parent.addChild(name);

        if (index < parts.length - 1) {
            buildTree(child, parts, index + 1, endpointType);
        } else {
            // Check if part has an operation qualifier
            if (qualifier != null) {
                child = child.addChild(qualifier);
                child.setQualifier(true);
            }
            child.setEndpointType(endpointType);
        }
    }

    /**
     * This method expands a tree into the collapsed set of endpoints.
     *
     * @param root The tree
     * @return The list of endpoints
     */
    protected static List<EndpointInfo> extractEndpointInfo(EndpointPart root) {
        List<EndpointInfo> endpoints = new ArrayList<EndpointInfo>();

        root.extractEndpointInfo(endpoints, "");

        return endpoints;
    }

    /**
     * This method initialises the list of endpoint information.
     *
     * @param endpoints The endpoint information
     */
    protected static void initEndpointInfo(List<EndpointInfo> endpoints) {
        for (int i = 0; i < endpoints.size(); i++) {
            initEndpointInfo(endpoints.get(i));
        }
    }

    /**
     * This method initialises the endpoint information.
     *
     * @param endpoint The endpoint information
     */
    protected static void initEndpointInfo(EndpointInfo endpoint) {
        endpoint.setRegex(createRegex(endpoint.getEndpoint(), endpoint.metaURI()));
        endpoint.setUriRegex(createRegex(EndpointUtil.decodeEndpointURI(endpoint.getEndpoint()), endpoint.metaURI()));

        if (endpoint.metaURI()) {
            StringBuilder template = new StringBuilder();

            String uri = EndpointUtil.decodeEndpointURI(endpoint.getEndpoint());

            String[] parts = uri.split("/");

            String part = null;
            int paramNo = 1;

            for (int j = 1; j < parts.length; j++) {
                template.append("/");

                if (parts[j].equals("*")) {
                    if (part == null) {
                        template.append("{");
                        template.append("param");
                        template.append(paramNo++);
                        template.append("}");
                    } else {
                        // Check if plural
                        if (part.length() > 1 && part.charAt(part.length() - 1) == 's') {
                            part = part.substring(0, part.length() - 1);
                        }
                        template.append("{");
                        template.append(part);
                        template.append("Id}");
                    }
                    part = null;
                } else {
                    part = parts[j];
                    template.append(part);
                }
            }

            endpoint.setUriTemplate(template.toString());
        }
    }

    /* (non-Javadoc)
     * @see org.hawkular.apm.api.services.AnalyticsService#getCommunicationSummaryStatistics(java.lang.String,
     *                          org.hawkular.apm.api.services.Criteria, boolean)
     */
    @Override
    public Collection<CommunicationSummaryStatistics> getCommunicationSummaryStatistics(String tenantId,
            Criteria criteria, boolean asTree) {
        Collection<CommunicationSummaryStatistics> ret = doGetCommunicationSummaryStatistics(tenantId,
                                        criteria);

        communicationSeverityAnalyser.evaluateCommunicationSummarySeverity(ret);

        if (asTree) {
            ret = CommunicationSummaryTreeBuilder.buildCommunicationSummaryTree(ret);

            if (!criteria.transactionWide()) {
                // Scan the trees to see whether node specific queries are relevant
                Iterator<CommunicationSummaryStatistics> iter=ret.iterator();
                while (iter.hasNext()) {
                    CommunicationSummaryStatistics css=iter.next();
                    if (!hasMetrics(css)) {
                        iter.remove();
                    }
                }
            }
        }

        return ret;
    }

    /**
     * This method determines whether the communication summary statistics have defined
     * metrics.
     *
     * @param css The communication summary structure
     * @return Whether they include metrics
     */
    protected static boolean hasMetrics(CommunicationSummaryStatistics css) {
        if (css.getCount() > 0) {
            return true;
        }
        for (ConnectionStatistics cs : css.getOutbound().values()) {
            if (cs.getCount() > 0) {
                return true;
            }
            if (cs.getNode() != null) {
                if (hasMetrics(cs.getNode())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * This method returns the flat list of communication summary stats.
     *
     * @param tenantId The tenant id
     * @param criteria The criteria
     * @return The list of communication summary nodes
     */
    protected abstract Collection<CommunicationSummaryStatistics> doGetCommunicationSummaryStatistics(String tenantId,
            Criteria criteria);

    /**
     * This method obtains the unbound endpoints from a list of business
     * transaction fragments.
     *
     * @param tenantId The tenant
     * @param fragments The list of business txn fragments
     * @param compress Whether the list should be compressed (i.e. to identify patterns)
     * @return The list of unbound endpoints
     */
    protected List<EndpointInfo> doGetUnboundEndpoints(String tenantId,
            List<Trace> fragments, boolean compress) {
        List<EndpointInfo> ret = new ArrayList<EndpointInfo>();
        Map<String, EndpointInfo> map = new HashMap<String, EndpointInfo>();

        // Process the fragments to identify which endpoints are not used in any trace
        for (int i = 0; i < fragments.size(); i++) {
            Trace trace = fragments.get(i);

            if (trace.initialFragment() && !trace.getNodes().isEmpty() && trace.getBusinessTransaction() == null) {

                // Check if top level node is Consumer
                if (trace.getNodes().get(0) instanceof Consumer) {
                    Consumer consumer = (Consumer) trace.getNodes().get(0);
                    String endpoint = EndpointUtil.encodeEndpoint(consumer.getUri(),
                                        consumer.getOperation());

                    // Check whether endpoint already known, and that it did not result
                    // in a fault (e.g. want to ignore spurious URIs that are not
                    // associated with a valid transaction)
                    if (!map.containsKey(endpoint) && consumer.getFault() == null) {
                        EndpointInfo info = new EndpointInfo();
                        info.setEndpoint(endpoint);
                        info.setType(consumer.getEndpointType());
                        ret.add(info);
                        map.put(endpoint, info);
                    }
                } else {
                    obtainProducerEndpoints(trace.getNodes(), ret, map);
                }
            }
        }

        // Check whether any of the top level endpoints are already associated with
        // a business txn config
        if (configService != null) {
            Map<String, BusinessTxnConfig> configs = configService.getBusinessTransactions(tenantId, 0);
            for (BusinessTxnConfig config : configs.values()) {
                if (config.getFilter() != null && config.getFilter().getInclusions() != null) {
                    if (log.isLoggable(Level.FINEST)) {
                        log.finest("Remove unbound URIs associated with btxn config=" + config);
                    }
                    for (String filter : config.getFilter().getInclusions()) {

                        if (filter != null && filter.trim().length() > 0) {
                            Iterator<EndpointInfo> iter = ret.iterator();
                            while (iter.hasNext()) {
                                EndpointInfo info = iter.next();
                                if (Pattern.matches(filter, info.getEndpoint())) {
                                    iter.remove();
                                }
                            }
                        }
                    }
                }
            }
        }

        // Check if the endpoints should be compressed to identify common patterns
        if (compress) {
            ret = compressEndpointInfo(ret);
        }

        Collections.sort(ret, new Comparator<EndpointInfo>() {
            @Override
            public int compare(EndpointInfo arg0, EndpointInfo arg1) {
                return arg0.getEndpoint().compareTo(arg1.getEndpoint());
            }
        });

        return ret;
    }

    /**
     * This method collects the information regarding endpoints.
     *
     * @param nodes The nodes
     * @param endpoints The list of endpoints
     */
    protected void obtainEndpoints(List<Node> nodes, List<EndpointInfo> endpoints) {
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);

            if (node.getUri() != null) {
                EndpointInfo ei=new EndpointInfo();
                ei.setEndpoint(EndpointUtil.encodeEndpoint(node.getUri(), node.getOperation()));

                if (!endpoints.contains(ei)) {
                    initEndpointInfo(ei);
                    endpoints.add(ei);
                }
            }

            if (node instanceof ContainerNode) {
                obtainEndpoints(((ContainerNode) node).getNodes(), endpoints);
            }
        }
    }

    /**
     * This method collects the information regarding endpoints for
     * contained producers.
     *
     * @param nodes The nodes
     * @param endpoints The list of endpoint info
     * @param map The map of endpoints to info
     */
    protected void obtainProducerEndpoints(List<Node> nodes, List<EndpointInfo> endpoints,
                                Map<String, EndpointInfo> map) {
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);

            if (node instanceof Producer) {
                String endpoint = EndpointUtil.encodeEndpoint(node.getUri(), node.getOperation());

                if (!map.containsKey(endpoint)) {
                    EndpointInfo info = new EndpointInfo();
                    info.setEndpoint(endpoint);
                    info.setType(((Producer) node).getEndpointType());
                    endpoints.add(info);
                    map.put(endpoint, info);
                }
            }

            if (node instanceof ContainerNode) {
                obtainProducerEndpoints(((ContainerNode) node).getNodes(), endpoints, map);
            }
        }
    }

    /**
     * This method derives the regular expression from the supplied
     * URI.
     *
     * @param endpoint The endpoint
     * @param meta Whether this is a meta endpoint
     * @return The regular expression
     */
    protected static String createRegex(String endpoint, boolean meta) {
        StringBuffer regex = new StringBuffer();

        regex.append('^');

        for (int i=0; i < endpoint.length(); i++) {
            char ch=endpoint.charAt(i);
            if ("*".indexOf(ch) != -1) {
                regex.append('.');
            } else if ("\\.^$|?+[]{}()".indexOf(ch) != -1) {
                regex.append('\\');
            }
            regex.append(ch);
        }

        regex.append('$');

        return regex.toString();
    }

    /**
     * This class represents a node in a tree of endpoint (e.g. URI) parts.
     *
     * @author gbrown
     */
    public static class EndpointPart {

        /**  */
        private static final int CHILD_THRESHOLD = 10;
        private int count = 1;
        private Map<String, EndpointPart> children;
        private String endpointType;
        private boolean qualifier=false;

        /**
         * @return the count
         */
        public int getCount() {
            return count;
        }

        /**
         * @param count the count to set
         */
        public void setCount(int count) {
            this.count = count;
        }

        /**
         * @return the children
         */
        public Map<String, EndpointPart> getChildren() {
            return children;
        }

        /**
         * @param children the children to set
         */
        public void setChildren(Map<String, EndpointPart> children) {
            this.children = children;
        }

        /**
         * This method adds a child with the supplied name,
         * if does not exist, or increments its count.
         *
         * @param name The name
         * @return The added/existing child
         */
        public EndpointPart addChild(String name) {
            EndpointPart child = null;

            if (children == null) {
                children = new HashMap<String, EndpointPart>();
            }

            if (!children.containsKey(name)) {
                child = new EndpointPart();
                children.put(name, child);
            } else {
                child = children.get(name);
                child.setCount(child.getCount() + 1);
            }

            return child;
        }

        /**
         * This method will apply rules to collapse the tree.
         */
        public void collapse() {
            if (children != null && !children.isEmpty()) {
                if (children.size() >= CHILD_THRESHOLD) {
                    EndpointPart merged = new EndpointPart();
                    for (EndpointPart cur : children.values()) {
                        merged.merge(cur);
                    }
                    children.clear();
                    children.put("*", merged);

                    merged.collapse();
                } else {
                    // Recursively perform on children
                    for (EndpointPart part : children.values()) {
                        part.collapse();
                    }
                }
            }
        }

        /**
         * This method merges the supplied endpoint part into
         * the current part.
         *
         * @param toMerge
         */
        public void merge(EndpointPart toMerge) {
            if (endpointType == null) {
                endpointType = toMerge.getEndpointType();
            }

            count += toMerge.getCount();

            // Process the supplied part's child nodes
            if (toMerge.getChildren() != null) {
                if (children == null) {
                    children = new HashMap<String, EndpointPart>();
                }
                for (String child : toMerge.getChildren().keySet()) {
                    if (getChildren().containsKey(child)) {
                        // Recursively merge
                        getChildren().get(child).merge(toMerge.getChildren().get(child));
                    } else {
                        // Move child to the merged tree
                        getChildren().put(child, toMerge.getChildren().get(child));
                    }
                }
            }
        }

        /**
         * This method expands the EndpointInfo from the tree.
         *
         * @param endpoints The list of endpoints
         * @param endpoint The endpoint string
         */
        public void extractEndpointInfo(List<EndpointInfo> endpoints, String endpoint) {

            if (endpointType != null) {
                EndpointInfo info = new EndpointInfo();
                info.setEndpoint(endpoint);
                info.setType(endpointType);
                endpoints.add(info);
            }

            if (getChildren() != null) {
                for (String child : getChildren().keySet()) {
                    EndpointPart part = getChildren().get(child);

                    part.extractEndpointInfo(endpoints, endpoint + (part.isQualifier() ? "" : "/") + child);
                }
            }
        }

        /**
         * @return the endpointType
         */
        public String getEndpointType() {
            return endpointType;
        }

        /**
         * @param endpointType the endpointType to set
         */
        public void setEndpointType(String endpointType) {
            this.endpointType = endpointType;
        }

        /**
         * @return the qualifier
         */
        public boolean isQualifier() {
            return qualifier;
        }

        /**
         * @param qualifier the qualifier to set
         */
        public void setQualifier(boolean qualifier) {
            this.qualifier = qualifier;
        }
    }
}
