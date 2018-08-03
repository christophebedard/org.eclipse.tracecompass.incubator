/*******************************************************************************
 * Copyright (c) 2016 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.model;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.SortedMap;

import org.eclipse.tracecompass.incubator.internal.dl4tc.dl4j.Dl4jUtils;
import org.eclipse.tracecompass.tmf.core.util.Pair;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

/**
 * Container for individual callstack array with pertinent metadata
 *
 * @author Christophe Bedard
 */
public class CallStackArray implements Serializable {

    private static final long serialVersionUID = 6532639713367196413L;

    private long fAddress;
    private int fDepth;
    private long fTimestamp;
    private long fDuration;

    private double[][] fPrimitiveOffsetArray;
    private double[][] fPrimitiveSelftimeArray;
    private INDArray fOffsetArray = null;
    private INDArray fSelftimeArray = null;

    private boolean fIsPrimitive;

    /*
     * These are used to compute other attributes, but they are kept for validation
     * purposes
     */
    private int fNumRows;
    private int fNumCols;

    /**
     * Constructor
     * <p>
     * This will use {@link INDArray}s by default.
     *
     * @param stackModel
     *            the stack model
     * @param maximums
     *            the maximum number of calls per address at any depth
     * @param numRows
     *            the total number of rows
     * @param numCols
     *            the total number of columns
     */
    public CallStackArray(StackModel stackModel, SortedMap<Long, Integer> maximums, int numRows, int numCols) {
        this(stackModel, maximums, numRows, numCols, false);
    }

    /**
     * Constructor
     *
     * @param stackModel
     *            the stack model
     * @param maximums
     *            the maximum number of calls per address at any depth
     * @param numRows
     *            the total number of rows
     * @param numCols
     *            the total number of columns
     * @param isPrimitive
     *            the choice to serialize as primitive arrays (true) or not (false)
     */
    public CallStackArray(StackModel stackModel, SortedMap<Long, Integer> maximums, int numRows, int numCols, boolean isPrimitive) {
        fIsPrimitive = isPrimitive;

        fAddress = stackModel.getAddress();
        fDepth = stackModel.getDepth();
        fTimestamp = stackModel.getTimestamp();
        fDuration = stackModel.getDuration();

        Pair<Integer, Integer> arraysDimensions = stackModel.getArraysDimensions();
        fNumRows = arraysDimensions.getFirst();
        fNumCols = arraysDimensions.getSecond();

        // Generate arrays
        fPrimitiveOffsetArray = stackModel.createArray(CallEntry::getOffset, numRows, numCols, maximums);
        fPrimitiveSelftimeArray = stackModel.createArray(CallEntry::getSelftime, numRows, numCols, maximums);
        if (!fIsPrimitive) {
            fOffsetArray = Dl4jUtils.arrayToNd4j(fPrimitiveOffsetArray);
            fSelftimeArray = Dl4jUtils.arrayToNd4j(fPrimitiveSelftimeArray);
        }
    }

    /**
     * @return the address
     */
    public long getAddress() {
        return fAddress;
    }

    /**
     * @return the depth
     */
    public int getDepth() {
        return fDepth;
    }

    /**
     * @return the timestamp
     */
    public long getTimestamp() {
        return fTimestamp;
    }

    /**
     * @return the duration
     */
    public long getDuration() {
        return fDuration;
    }

    /**
     * @return the number of rows for this individual array
     */
    public int getNumRows() {
        return fNumRows;
    }

    /**
     * @return the number of columns for this individual array
     */
    public int getNumCols() {
        return fNumCols;
    }

    /**
     * @return the offset array
     */
    public INDArray getOffsetArray() {
        return fOffsetArray;
    }

    /**
     * @return the selftime array
     */
    public INDArray getSelftimeArray() {
        return fSelftimeArray;
    }

    private void readObject(ObjectInputStream inputStream) throws IOException, ClassNotFoundException {
        fIsPrimitive = inputStream.readBoolean();

        fAddress = inputStream.readLong();
        fDepth = inputStream.readInt();
        fTimestamp = inputStream.readLong();
        fDuration = inputStream.readLong();

        fNumRows = inputStream.readInt();
        fNumCols = inputStream.readInt();

        if (fIsPrimitive) {
            // Read primitive arrays and immediately convert to Nd4j arrays
            fPrimitiveOffsetArray = (double[][]) inputStream.readObject();
            fPrimitiveSelftimeArray = (double[][]) inputStream.readObject();
            fOffsetArray = Dl4jUtils.arrayToNd4j(fPrimitiveOffsetArray);
            fSelftimeArray = Dl4jUtils.arrayToNd4j(fPrimitiveSelftimeArray);
        } else {
            // Read Nd4j arrays directly
            fOffsetArray = Nd4j.read(inputStream);
            fSelftimeArray = Nd4j.read(inputStream);
        }
    }

    private void writeObject(ObjectOutputStream outputStream) throws IOException {
        outputStream.writeBoolean(fIsPrimitive);

        outputStream.writeLong(fAddress);
        outputStream.writeInt(fDepth);
        outputStream.writeLong(fTimestamp);
        outputStream.writeLong(fDuration);

        outputStream.writeInt(fNumRows);
        outputStream.writeInt(fNumCols);

        if (fIsPrimitive) {
            outputStream.writeObject(fPrimitiveOffsetArray);
            outputStream.writeObject(fPrimitiveSelftimeArray);
        } else {
            Nd4j.write(fOffsetArray, new DataOutputStream(outputStream));
            Nd4j.write(fSelftimeArray, new DataOutputStream(outputStream));
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("\t\t" + this.getClass().getSimpleName() + "\n"); //$NON-NLS-1$ //$NON-NLS-2$
        builder.append("\t\t" + "offset" + "\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        builder.append(Dl4jUtils.nd4jArrayToString(fOffsetArray, 2) + "\n"); //$NON-NLS-1$
        builder.append("\t\t" + "selftime" + "\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        builder.append(Dl4jUtils.nd4jArrayToString(fSelftimeArray, 2) + "\n"); //$NON-NLS-1$
        return builder.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (fAddress ^ (fAddress >>> 32));
        result = prime * result + fDepth;
        result = prime * result + (int) (fDuration ^ (fDuration >>> 32));
        result = prime * result + fNumCols;
        result = prime * result + fNumRows;
        result = prime * result + (int) (fTimestamp ^ (fTimestamp >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof CallStackArray)) {
            return false;
        }
        CallStackArray other = (CallStackArray) obj;
        if (fAddress != other.fAddress) {
            return false;
        }
        if (fDepth != other.fDepth) {
            return false;
        }
        if (fDuration != other.fDuration) {
            return false;
        }
        if (fNumCols != other.fNumCols) {
            return false;
        }
        if (fNumRows != other.fNumRows) {
            return false;
        }
        if (fTimestamp != other.fTimestamp) {
            return false;
        }
        return true;
    }
}
