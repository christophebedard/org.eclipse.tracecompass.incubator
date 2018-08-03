/*******************************************************************************
 * Copyright (c) 2018 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.detection;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.common.core.log.TraceCompassLog;
import org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.CallStackAnomalyAnalysis;
import org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.CallStackAnomalyAnalysisProvider;
import org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.model.CallStackArraysSerialUtil;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;

/**
 * Base abstract class for callstack anomaly detection. Contains common methods.
 *
 * @author Christophe Bedard
 */
public abstract class CallStackAnomalyDetector implements ICallStackAnomalyDetector {

    private static final @NonNull Logger LOGGER = TraceCompassLog.getLogger(CallStackAnomalyDetector.class);

    /** The callstack arrays container */
    protected CallStackArraysSerialUtil fArrays;

    private ITmfStateSystemBuilder ss;
    private final int fResultsQuark;

    /**
     * Constructor
     *
     * @param arrays
     *            the callstack arrays container
     * @param ssb
     *            the state system builder to use for results
     */
    public CallStackAnomalyDetector(CallStackArraysSerialUtil arrays, ITmfStateSystemBuilder ssb) {
        fArrays = arrays;
        ss = ssb;
        fResultsQuark = ss.getQuarkAbsoluteAndAdd(CallStackAnomalyAnalysisProvider.ATTRIBUTE_RESULTS);
    }

    @Override
    public void apply() {
        long checkpoint = System.currentTimeMillis();
        try {
            detectAnomalies();
        } catch (IOException | ClassNotFoundException e) {
            LOGGER.log(Level.WARNING, CallStackAnomalyDetector.class.getSimpleName() + " failed: " + e.getMessage()); //$NON-NLS-1$
        } finally {
            fArrays.closeRead();
        }
        CallStackAnomalyAnalysis.debug.println("total eval=" + (System.currentTimeMillis() - checkpoint)); //$NON-NLS-1$
        CallStackAnomalyAnalysis.debug.println("end time=" + new SimpleDateFormat(CallStackAnomalyAnalysis.DATE_FORMAT).format(new Date())); //$NON-NLS-1$
    }

    /**
     * Detect anomalies and send results one by one to state system
     *
     * @throws IOException
     *             model file import/reading may fail
     * @throws ClassNotFoundException
     *             reading and casting CallStackArray
     */
    protected abstract void detectAnomalies() throws IOException, ClassNotFoundException;

    /**
     * Add a result to the state system
     * <p>
     * Should be called for each score individually by classes extending this
     *
     * @param timestamp
     *            the timestamp
     * @param duration
     *            the duration
     * @param depth
     *            the depth
     * @param score
     *            the resulting score
     */
    protected void addResultToStateSystem(long timestamp, long duration, int depth, double score) {
        int depthResultsQuark = ss.getQuarkRelativeAndAdd(fResultsQuark, Integer.toString(depth));
        ss.modifyAttribute(timestamp, score, depthResultsQuark);
        ss.modifyAttribute(timestamp + duration, (Object) null, depthResultsQuark);
    }

    /**
     * Add info about results to state system (min, max, threshold)
     *
     * @param min
     *            the minimum score
     * @param max
     *            the maximum score
     * @param threshold
     *            the minimum threshold for displaying a result
     */
    protected void addInfoToStateSystem(double min, double max, double threshold) {
        int infoQuark = ss.getQuarkAbsoluteAndAdd(CallStackAnomalyAnalysisProvider.ATTRIBUTE_INFO);
        int minQuark = ss.getQuarkRelativeAndAdd(infoQuark, CallStackAnomalyAnalysisProvider.ATTRIBUTE_MIN);
        int maxQuark = ss.getQuarkRelativeAndAdd(infoQuark, CallStackAnomalyAnalysisProvider.ATTRIBUTE_MAX);
        int thresholdQuark = ss.getQuarkRelativeAndAdd(infoQuark, CallStackAnomalyAnalysisProvider.ATTRIBUTE_THRESHOLD);
        ss.updateOngoingState(min, minQuark);
        ss.updateOngoingState(max, maxQuark);
        ss.updateOngoingState(threshold, thresholdQuark);
    }
}
