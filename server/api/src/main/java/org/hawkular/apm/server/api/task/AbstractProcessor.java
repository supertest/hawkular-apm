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
package org.hawkular.apm.server.api.task;

import java.util.List;

/**
 * This abstract class represents the base class for processors.
 *
 * @author gbrown
 */
public abstract class AbstractProcessor<T, R> implements Processor<T,R> {

    /**  */
    private static final int DEFAULT_RETRY_DELAY = 1000;

    /* (non-Javadoc)
     * @see org.hawkular.apm.server.api.task.Processor#initialise(java.lang.String,java.util.List)
     */
    @Override
    public void initialise(String tenantId, List<T> items) {
    }

    /* (non-Javadoc)
     * @see org.hawkular.apm.server.api.task.Processor#getDeliveryDelay(java.util.List)
     */
    @Override
    public long getDeliveryDelay(List<R> results) {
        return 0;
    }

    /* (non-Javadoc)
     * @see org.hawkular.apm.server.api.task.Processor#getRetryDelay(java.util.List)
     */
    @Override
    public long getRetryDelay(List<T> items) {
        return DEFAULT_RETRY_DELAY;
    }

    /* (non-Javadoc)
     * @see org.hawkular.apm.server.api.task.Processor#cleanup(java.lang.String,java.util.List)
     */
    @Override
    public void cleanup(String tenantId, List<T> items) {
    }

}
