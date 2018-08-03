/*******************************************************************************
 * Copyright (c) 2018 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.tracecompass.common.core.log.TraceCompassLog;
import org.eclipse.tracecompass.tmf.core.analysis.TmfAnalysisManager;
import org.eclipse.tracecompass.tmf.core.request.ITmfEventRequest;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;
import org.eclipse.tracecompass.tmf.core.statesystem.TmfStateSystemAnalysisModule;
import org.eclipse.tracecompass.tmf.core.timestamp.TmfTimeRange;

/**
 * Analysis module for callstack anomaly detection
 *
 * @author Christophe Bedard
 */
public class CallStackAnomalyAnalysis extends TmfStateSystemAnalysisModule {

    /**
     * Different analyses that can be performed
     *
     * @author Christophe Bedard
     */
    public enum AnalysisType {
        /**
         * Apply statistical anomaly detection
         */
        STATISTICAL,
        /**
         * Perform NN training
         */
        NN_TRAIN,
        /**
         * Apply NN detection
         */
        NN_APPLY
    }

    /** Logger */
    public static final @NonNull Logger LOGGER = TraceCompassLog.getLogger(CallStackAnomalyAnalysisProvider.class);

    /** Date format for logging */
    public static final String DATE_FORMAT = "yyyy-MM-dd_HH:mm:ss"; //$NON-NLS-1$
    /** File logger */
    public static PrintWriter debug;
    static {
        try {
            File f = File.createTempFile("tc_ca_output", ".txt");//$NON-NLS-1$ //$NON-NLS-2$
            debug = new PrintWriter(new BufferedWriter(new FileWriter(f, true)));
            debug.println(new SimpleDateFormat(DATE_FORMAT).format(new Date()));
        } catch (IOException e) {
            System.out.println("ERROR:" + e.getMessage()); //$NON-NLS-1$
        }
    }

    /** The ID of this analysis module */
    public static final @NonNull String ID = "org.eclipse.tracecompass.incubator.callstackanomaly.core.analysis"; //$NON-NLS-1$
    /** Analysis description for registration and progress */
    public static final String ANALYSIS_DESCRIPTION = "callstack anomaly analysis"; //$NON-NLS-1$

    /** Analysis parameters */
    private final CallStackAnomalyAnalysisParameters fParameters;

    private @NonNull IProgressMonitor fMonitor;

    /**
     * Constructor
     *
     * @param parameters
     *            the analysis parameters
     * @param monitor
     *            the progress monitor
     */
    public CallStackAnomalyAnalysis(CallStackAnomalyAnalysisParameters parameters, @NonNull IProgressMonitor monitor) {
        manualRegistration();
        fParameters = parameters;
        fMonitor = monitor;
    }

    @Override
    protected @NonNull ITmfStateProvider createStateProvider() {
        return new CallStackAnomalyAnalysisProvider(checkNotNull(getTrace()), fParameters, fMonitor, getSsFile());
    }

    @Override
    protected @NonNull ITmfEventRequest createEventRequest(@NonNull ITmfStateProvider stateProvider, @NonNull TmfTimeRange timeRange, int nbRead) {
        return new StateSystemEventRequest(stateProvider, timeRange, nbRead, 1);
    }

    /**
     * Manual registration tasks
     */
    private void manualRegistration() {
        init(ANALYSIS_DESCRIPTION);
        TmfAnalysisManager.analysisModuleCreated(this);
    }
}
