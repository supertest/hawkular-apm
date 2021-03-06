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
package org.hawkular.apm.api.internal.actions;

import java.util.ArrayList;
import java.util.Map;

import org.hawkular.apm.api.logging.Logger;
import org.hawkular.apm.api.model.Severity;
import org.hawkular.apm.api.model.config.Direction;
import org.hawkular.apm.api.model.config.btxn.AddContentAction;
import org.hawkular.apm.api.model.config.btxn.Processor;
import org.hawkular.apm.api.model.config.btxn.ProcessorAction;
import org.hawkular.apm.api.model.trace.InteractionNode;
import org.hawkular.apm.api.model.trace.Issue;
import org.hawkular.apm.api.model.trace.Message;
import org.hawkular.apm.api.model.trace.Node;
import org.hawkular.apm.api.model.trace.ProcessorIssue;
import org.hawkular.apm.api.model.trace.Trace;

/**
 * This handler is associated with the AddContent action.
 *
 * @author gbrown
 */
public class AddContentActionHandler extends ExpressionBasedActionHandler {

    private static final Logger log = Logger.getLogger(AddContentActionHandler.class.getName());

    /**  */
    private static final String NAME_MUST_BE_SPECIFIED = "Name must be specified";

    /**
     * This constructor initialises the action.
     *
     * @param action The action
     */
    public AddContentActionHandler(ProcessorAction action) {
        super(action);
    }

    /**
     * This method initialises the process action handler.
     *
     * @param processor The processor
     */
    @Override
    public void init(Processor processor) {
        super.init(processor);

        AddContentAction action = (AddContentAction) getAction();

        if (action.getName() == null || action.getName().trim().length() == 0) {
            ProcessorIssue pi = new ProcessorIssue();
            pi.setProcessor(processor.getDescription());
            pi.setAction(getAction().getDescription());
            pi.setField("name");
            pi.setSeverity(Severity.Error);
            pi.setDescription(NAME_MUST_BE_SPECIFIED);

            if (getIssues() == null) {
                setIssues(new ArrayList<Issue>());
            }
            getIssues().add(0, pi);
        }
    }

    /* (non-Javadoc)
     * @see org.hawkular.apm.client.collector.internal.actions.ProcessorActionHandler#process(
     *      org.hawkular.apm.api.model.trace.Trace, org.hawkular.apm.api.model.trace.Node,
     *      org.hawkular.apm.api.model.config.Direction, java.util.Map, java.lang.Object[])
     */
    @Override
    public boolean process(Trace trace, Node node, Direction direction, Map<String, ?> headers,
            Object[] values) {
        if (super.process(trace, node, direction, headers, values)) {
            String value = getValue(trace, node, direction, headers, values);
            if (value != null) {
                if (node.interactionNode()) {
                    if (direction == Direction.In) {
                        if (((InteractionNode) node).getIn() == null) {
                            ((InteractionNode) node).setIn(new Message());
                        }
                        ((InteractionNode) node).getIn().addContent(((AddContentAction) getAction()).getName(),
                                ((AddContentAction) getAction()).getType(), value);
                    } else {
                        if (((InteractionNode) node).getOut() == null) {
                            ((InteractionNode) node).setOut(new Message());
                        }
                        ((InteractionNode) node).getOut().addContent(
                                ((AddContentAction) getAction()).getName(),
                                ((AddContentAction) getAction()).getType(), value);
                    }
                    return true;
                } else {
                    log.warning("Attempt to add content to a non-interaction based node type '"
                            + node.getType() + "'");
                }
            }
        }
        return false;
    }

}
