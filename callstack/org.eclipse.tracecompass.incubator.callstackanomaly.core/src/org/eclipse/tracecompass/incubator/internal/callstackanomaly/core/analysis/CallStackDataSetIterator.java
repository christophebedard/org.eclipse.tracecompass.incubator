/*******************************************************************************
 * Copyright (c) 2018 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.model.CallStackArray;
import org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.model.CallStackArraysSerialUtil;
import org.eclipse.tracecompass.tmf.core.util.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.DataSetPreProcessor;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;

/**
 * {@link DataSetIterator} for callstack data
 * <p>
 * This uses {@link CallStackArraysSerialUtil} to read arrays from the supplementary file.
 *
 * @author Christophe Bedard
 */
public class CallStackDataSetIterator implements DataSetIterator {

    private static final long serialVersionUID = 1981146707825853794L;

    /**
     * The batch size (number of entires in DataSet for each {@link #next()})
     */
    private int fBatchSize;

    private CallStackArraysSerialUtil fArrays;

    /**
     * Constructor
     * <p>
     * This constructor will use the array as both the feature and the label.
     *
     * @param arrays
     *            the callstack arrays container
     * @param batchSize
     *            the batch size
     */
    public CallStackDataSetIterator(CallStackArraysSerialUtil arrays, int batchSize) {
        fArrays = arrays;
        fBatchSize = batchSize;
        fArrays.initRead();
    }

    @Override
    public boolean hasNext() {
        return fArrays.hasNextArray();
    }

    @Override
    public DataSet next() {
        return next(fBatchSize);
    }

    @Override
    public int batch() {
        return fBatchSize;
    }

    @Override
    public DataSet next(int batchSize) {
        if (!hasNext()) {
            return null;
        }
        try {
            List<DataSet> dataSets = new ArrayList<>();
            for (int i = 0; i < batchSize; ++i) {
                if (hasNext()) {
                    CallStackArray callstackArray = fArrays.read();
                    INDArray concat = Nd4j.concat(0, callstackArray.getOffsetArray(), callstackArray.getSelftimeArray());
                    INDArray concatFlat = concat.reshape(1, concat.length());
                    dataSets.add(new DataSet(concatFlat, concatFlat));
                }
            }
            return DataSet.merge(dataSets);
        } catch (ClassNotFoundException | IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Get next array with its information
     *
     * @return the next pair of {@link DataSet} and {@link CallStackArray}
     */
    public Pair<DataSet, CallStackArray> nextWithInfo() {
        if (!hasNext()) {
            return null;
        }
        try {
            CallStackArray callstackArray = fArrays.read();
            INDArray concat = Nd4j.concat(0, callstackArray.getOffsetArray(), callstackArray.getSelftimeArray());
            INDArray concatFlat = concat.reshape(1, concat.length());
            DataSet dataSet = new DataSet(concatFlat, concatFlat);
            return new Pair<>(dataSet, callstackArray);
        } catch (ClassNotFoundException | IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void reset() {
        fArrays.reset();
    }

    /**
     * Dispose of this iterator by properly closing its underlying containers.
     * <p>
     * Should be called once the iterator is no longer needed.
     */
    public void dispose() {
        fArrays.closeRead();
    }

    @Override
    public boolean resetSupported() {
        return true;
    }

    @Override
    public boolean asyncSupported() {
        return false;
    }

    @Override
    public List<String> getLabels() {
        return null;
    }

    @Override
    public DataSetPreProcessor getPreProcessor() {
        return null;
    }

    @Override
    public int inputColumns() {
        return 0;
    }

    @Override
    public void setPreProcessor(DataSetPreProcessor arg0) {
        // TODO?
    }

    @Override
    public int totalOutcomes() {
        return (int) fArrays.getArraysCount();
    }

    /**
     * Debug
     */
    public void print() {
        reset();
        while (hasNext()) {
            System.out.println(next());
        }
        reset();
    }
}
