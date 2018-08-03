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

import org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.CallStackAnomalyAnalysisParameters;
import org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.model.CallStackArray;
import org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.model.CallStackArraysSerialUtil;
import org.eclipse.tracecompass.incubator.internal.dl4tc.dl4j.Dl4jUtils;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.tmf.core.util.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

/**
 * Identify anomalies using a statistical method.
 * <p>
 * A call is an anomaly if either its offset or its selftime is abnormal. An
 * offset or a selftime is abnormal if, compared to the other similar calls from
 * similar callstacks (same root call address at the same depth), it is too far
 * from the mean (i.e. if <code>selftime_i - mean > n * stdev</code>).
 * <p>
 * A callstack with at least one abnormal call is considered an anomaly.
 *
 * @author Christophe Bedard
 */
public class StatisticalAnomalyDetector extends CallStackAnomalyDetector {

    /**
     * N value
     */
    private final int fNValue;

    /**
     * Constructor
     *
     * @param arrays
     *            the callstack arrays container
     * @param ssb
     *            the state system builder to use for results
     * @param params
     *            the parameters
     */
    public StatisticalAnomalyDetector(CallStackArraysSerialUtil arrays, ITmfStateSystemBuilder ssb, CallStackAnomalyAnalysisParameters params) {
        super(arrays, ssb);
        fNValue = params.getNValue();
    }

    @Override
    protected void detectAnomalies() throws ClassNotFoundException, IOException {
        // Concatenate arrays of similar callstacks
        Table<Long, Integer, Pair<INDArray, INDArray>> arrays = HashBasedTable.create();
        fArrays.initRead();
        while (fArrays.hasNextArray()) {
            CallStackArray array = fArrays.read();
            INDArray offsetArray = array.getOffsetArray();
            INDArray selftimeArray = array.getSelftimeArray();
            final long address = array.getAddress();
            final int depth = array.getDepth();

            final long[] shape = offsetArray.shape();

            // Concatenate
            Pair<INDArray, INDArray> concatPair = arrays.get(address, depth);
            if (concatPair == null) {
                concatPair = new Pair<>(offsetArray.reshape(1, shape[0], shape[1]),
                        selftimeArray.reshape(1, shape[0], shape[1]));
            } else {
                INDArray concatOffset = Nd4j.concat(0, concatPair.getFirst(), offsetArray.reshape(1, shape[0], shape[1]));
                INDArray concatSelftime = Nd4j.concat(0, concatPair.getSecond(), selftimeArray.reshape(1, shape[0], shape[1]));
                concatPair = new Pair<>(concatOffset, concatSelftime);
            }
            arrays.put(address, depth, concatPair);
        }
        fArrays.closeRead();

        // Get statistics
        // Pair<Pair<offsetMeans, offsetStds>, Pair<selftimeMeans, selftimeStds>>
        Table<Long, Integer, Pair<Pair<INDArray, INDArray>, Pair<INDArray, INDArray>>> stats = HashBasedTable.create();
        for (long address : arrays.rowKeySet()) {
            for (int depth : arrays.row(address).keySet()) {
                Pair<INDArray, INDArray> concatPair = arrays.get(address, depth);
                if (concatPair != null) {
                    INDArray offsetMeans = concatPair.getFirst().mean(0);
                    INDArray offsetStds = concatPair.getFirst().std(0);
                    INDArray selftimeMeans = concatPair.getSecond().mean(0);
                    INDArray selftimeStds = concatPair.getSecond().std(0);
                    stats.put(address, depth, new Pair<>(new Pair<>(offsetMeans, offsetStds),
                            new Pair<>(selftimeMeans, selftimeStds)));
                }
            }
        }

        // Compute results
        fArrays.initRead();
        while (fArrays.hasNextArray()) {
            CallStackArray array = fArrays.read();
            INDArray offsetArray = array.getOffsetArray();
            INDArray selftimeArray = array.getSelftimeArray();
            final long address = array.getAddress();
            final int depth = array.getDepth();
            final long timestamp = array.getTimestamp();
            final long duration = array.getDuration();

            // Get statistics
            Pair<Pair<INDArray, INDArray>, Pair<INDArray, INDArray>> statsPair = stats.get(address, depth);
            if (statsPair != null) {
                // Compare with statistics
                INDArray offsetResult = Dl4jUtils.leq(offsetArray.sub(statsPair.getFirst().getFirst()), statsPair.getFirst().getSecond().mul(fNValue));
                INDArray selftimeResult = Dl4jUtils.leq(selftimeArray.sub(statsPair.getSecond().getFirst()), statsPair.getSecond().getSecond().mul(fNValue));

                // Figure out if it's an anomaly: if at least one call is abnormal
                // (i.e. if there is a 0)
                boolean isOffsetAnomaly = offsetResult.minNumber().doubleValue() == 0.0;
                boolean isSelftimeAnomaly = selftimeResult.minNumber().doubleValue() == 0.0;
                // Consider it an anomaly if either offset or selftime is abnormal
                double score = (isOffsetAnomaly || isSelftimeAnomaly) ? 1.0 : 0.0;

                addResultToStateSystem(timestamp, duration, depth, score);
            }
        }
        fArrays.closeRead();

        addInfoToStateSystem(0.0, 1.0, 0.0);
    }
}
