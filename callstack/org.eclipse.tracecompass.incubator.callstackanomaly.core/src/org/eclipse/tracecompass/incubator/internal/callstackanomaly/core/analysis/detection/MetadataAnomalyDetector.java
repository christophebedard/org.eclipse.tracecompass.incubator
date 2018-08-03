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

import org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.model.CallStackArray;
import org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.model.CallStackArraysSerialUtil;
import org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.model.CallStackMetadata;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;

/**
 * Identify anomalies using metadata and arrays.
 * <p>
 * Possible reasons for incompatibility:
 * <ul>
 * <li>Depth of a (sub-)callstack greater than specified in the model's
 * metadata</li>
 * <li>Maximum number of calls for one address in a (sub-)callstack greater than
 * the maximum specified in the model's metadata</li>
 * <li>Any other (sub-)callstack which could not be represented correctly</li>
 * </ul>
 * Currently, this only checks if the array dimensions match to figure out if a
 * certain neural network model can be applied. However, arrays dimensions could
 * match even if the addresses are completely different.
 *
 * @author Christophe Bedard
 */
public class MetadataAnomalyDetector extends CallStackAnomalyDetector {

    private final CallStackMetadata fModelMetadata;

    /**
     * Constructor
     *
     * @param arrays
     *            the callstack arrays container
     * @param ssb
     *            the state system builder to use for results
     * @param modelMetadata
     *            the model metadata to use for anomaly detection
     */
    public MetadataAnomalyDetector(CallStackArraysSerialUtil arrays, ITmfStateSystemBuilder ssb, CallStackMetadata modelMetadata) {
        super(arrays, ssb);
        fModelMetadata = modelMetadata;
    }

    @Override
    protected void detectAnomalies() throws IOException, ClassNotFoundException {
        fArrays.initRead();
        int modelNumCols = fModelMetadata.getAddressSize();
        int modelNumRows = fModelMetadata.getDepthSize();

        // For each array
        while (fArrays.hasNextArray()) {
            // Get from file
            CallStackArray array = fArrays.read();

            // Check to see if there is an anomaly
            boolean isValid = isMetadataValid(array.getNumRows(), array.getNumCols(), modelNumCols, modelNumRows);

            // Add this score to state system
            double anomalyScore = isValid ? 0.0 : 1.0;
            addResultToStateSystem(array.getTimestamp(), array.getDuration(), array.getDepth(), anomalyScore);
        }

        // Add min/max scores to state system
        addInfoToStateSystem(0.0, 1.0, 1.0);
    }

    /**
     * Check if a single callstack from a trace is valid with regards to a trained
     * model
     *
     * @param traceStackModel
     *            the individual stack model from the trace
     * @param modelNumRows
     *            the number of rows from the model metadata
     * @param modelNumCols
     *            the number of columns from the model metadata
     * @return the validation result: true if valid, false otherwise
     */
    private static boolean isMetadataValid(int arrayNumRows, int arrayNumCols, int modelNumRows, int modelNumCols) {
        /*
         * Compute the array dimensions of this individual stack model. If it fits (i.e.
         * not bigger) the model's dimensions from the metadata, it is deemed
         * compatible.
         *
         * FIXME even if they do fit, the addresses could be different
         */
        return arrayNumRows <= modelNumRows && arrayNumCols <= modelNumCols;
    }

    /**
     * Check if metadata from the current trace is compatible with the metadata from
     * an imported model
     *
     * @param traceNumRows
     *            the number of rows from the trace metadata
     * @param traceNumCols
     *            the number of columns from the trace metadata
     * @param modelNumRows
     *            the number of rows from the model metadata
     * @param modelNumCols
     *            the number of columns from the model metadata
     * @return the compatibility result: true if compatible, false otherwise
     */
    public static boolean isMetadataCompatible(int traceNumRows, int traceNumCols, int modelNumRows, int modelNumCols) {
        return traceNumRows == modelNumRows && traceNumCols == modelNumCols;
    }
}
