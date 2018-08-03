/*******************************************************************************
 * Copyright (c) 2018 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.detection;

/**
 * Interface for a callstack anomaly detector
 * <p>
 * Classes implementing this interface should read the callstack arrays one by
 * one and add their results to the state system.
 *
 * @author Christophe Bedard
 */
public interface ICallStackAnomalyDetector {
    /**
     * Apply anomaly detection
     */
    void apply();
}
