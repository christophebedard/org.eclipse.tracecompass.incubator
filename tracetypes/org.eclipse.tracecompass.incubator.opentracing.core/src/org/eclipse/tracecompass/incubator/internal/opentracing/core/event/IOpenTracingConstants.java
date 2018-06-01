/*******************************************************************************
 * Copyright (c) 2018 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.opentracing.core.event;

/**
 * Constants for the open tracing format
 *
 * @author Katherine Nadeau
 */
public interface IOpenTracingConstants {

    /**
     * spanID field name
     */
    String SPAN_ID = "spanID"; //$NON-NLS-1$
    /**
     * flags field name (specific to jaeger tracer)
     */
    String FLAGS = "flags"; //$NON-NLS-1$
    /**
     * operationName field name
     */
    String OPERATION_NAME = "operationName"; //$NON-NLS-1$
    /**
     * references field name
     */
    String REFERENCES = "references"; //$NON-NLS-1$
    /**
     * startTime field name
     */
    String START_TIME = "startTime"; //$NON-NLS-1$
    /**
     * duration field name
     */
    String DURATION = "duration"; //$NON-NLS-1$
    /**
     * tags field name
     */
    String TAGS = "tags"; //$NON-NLS-1$
    /**
     * processID field name
     */
    String PROCESS_ID = "processID"; //$NON-NLS-1$

}
