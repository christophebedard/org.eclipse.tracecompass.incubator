/*******************************************************************************
 * Copyright (c) 2018 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;

import org.eclipse.tracecompass.incubator.internal.dl4tc.dl4j.Dl4jUtils;
import org.eclipse.tracecompass.tmf.core.util.Pair;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;

/**
 * Structure that represents a sub-callstack with a specific root call and
 * absolute depth. It contains {@link CallEntry}s (offset and selftime) of its
 * children calls in an ordered way.
 * <p>
 * This can be represented by two 2D matrices with the same dimensions: one
 * matrix for offset and one for selftime. See {@link #toArray(Function)}.
 *
 * <pre>
 *                Address & index
 *
 *               A0      A1     B0
 *            _ _ _ _ _ _ _ _ _ _ _ _
 *         0 | entry | entry | entry
 *         1 | entry | entry | entry
 * Depth   2 | entry | entry | entry
 *         3 | entry | entry | entry
 *         4 | entry | entry | entry
 * </pre>
 *
 * @author Christophe Bedard
 */
public class StackModel {

    /**
     * The stack model data
     * <p>
     * Map&lt;depth, ListMultimap&lt;address, CallEntry&gt;&gt;
     */
    private Map<Integer, ListMultimap<Long, CallEntry>> fStackData;
    /** The address of the callstack root call */
    private long fAddress;
    /** The depth of the callstack root call */
    private int fDepth;
    /** The start timestamp of the callstack root call */
    private long fTimestamp;
    /** The duration of the callstack root call */
    private long fDuration;

    /**
     * Constructor
     *
     * @param address
     *            the address of the root call
     * @param depth
     *            the depth of the root call
     * @param timestamp
     *            the start of the root call
     * @param duration
     *            the duration of the root call
     */
    public StackModel(long address, int depth, long timestamp, long duration) {
        fStackData = new LinkedHashMap<>();
        fAddress = address;
        fDepth = depth;
        fTimestamp = timestamp;
        fDuration = duration;
    }

    /**
     * Constructor
     *
     * @param rootCall
     *            the callstack's root call
     */
    public StackModel(FunctionCall rootCall) {
        this(rootCall.getSymbol(), rootCall.getDepth(), rootCall.getStart(), rootCall.getDuration());
    }

    /**
     * @return the stack data (copy)
     */
    public Map<Integer, ListMultimap<Long, CallEntry>> getStackData() {
        return new LinkedHashMap<>(fStackData);
    }

    /**
     * @return the root call's address
     */
    public long getAddress() {
        return fAddress;
    }

    /**
     * @return the root call's depth
     */
    public int getDepth() {
        return fDepth;
    }

    /**
     * @return the root call's start timestamp
     */
    public long getTimestamp() {
        return fTimestamp;
    }

    /**
     * @return the root call's duration
     */
    public long getDuration() {
        return fDuration;
    }

    /**
     * Add an offset and selftime ({@link CallEntry}) given the corresponding
     * absolute depth and address
     *
     * @param depth
     *            the absolute depth of the call
     * @param address
     *            the call address
     * @param offset
     *            the offset with its root
     * @param selftime
     *            the selftime
     */
    public void addSelftime(int depth, long address, long offset, long selftime) {
        addSelftime(depth, address, new CallEntry(offset, selftime));
    }

    private void addSelftime(int depth, long address, CallEntry callEntry) {
        fStackData.computeIfAbsent(depth, whatever -> ArrayListMultimap.create()).get(address).add(callEntry);
    }

    /**
     * Format the {@link StackModel} into a 2D array using
     * {@link CallEntry#getOffset()}
     *
     * @return the array representing the offsets
     * @see #toArray(Function)
     */
    public double[][] toOffsetArray() {
        return toArray(CallEntry::getOffset);
    }

    /**
     * Format the {@link StackModel} into a 2D array using
     * {@link CallEntry#getSelftime()}
     *
     * @return the array representing the selftime
     * @see #toArray(Function)
     */
    public double[][] toSelftimeArray() {
        return toArray(CallEntry::getSelftime);
    }

    /**
     * Format this {@link StackModel} into a 2D array, without any knowledge of
     * other {@link StackModel}s (thus the dimensions of the arrays returned by this
     * method will not be the same across all {@link StackModel}s).
     *
     * @param attributeFunction
     *            the function which will get desired attribute from
     *            {@link CallEntry}
     * @return an array representation
     * @see #createArray(Function, int, int, SortedMap)
     * @see #toOffsetArray()
     * @see #toSelftimeArray()
     */
    public double[][] toArray(Function<CallEntry, Long> attributeFunction) {
        // Figure out array size (depth=rows, address=cols)
        SortedMap<Long, Integer> maxNumberOfCalls = getMaxNumberOfCalls();
        Pair<Integer, Integer> arrayDimensions = getArrayDimensions(maxNumberOfCalls);
        int depthSize = arrayDimensions.getFirst();
        int addressSize = arrayDimensions.getSecond();

        // Fill array
        return createArray(attributeFunction, depthSize, addressSize, maxNumberOfCalls);
    }

    /**
     * Format the {@link StackModel} into a 2D array
     * <p>
     * The rows represent the absolute depths of the callstack, starting from 0
     * (global root). The columns represent the entries ordered by address and then
     * by timestamp.
     * <p>
     * The array is sparse, since there may be 10 calls of one address at depth and
     * 2 calls at another depth.
     * <p>
     * For example, let's say we have the following stack:
     *
     * <pre>
     *                 {1}
     *               0x000002
     *               ↓↑    ↓↑
     *              {2}    {3}
     *          0x000002  0x00006d
     *          ↓↑        ↓↑    ↓↑
     *         {4}       {5}    {6}
     *      0x000002  0x00002  0x00002
     * </pre>
     *
     * It will be represented as:
     *
     * <pre>
     * [[{1},   0,   0,   0],
     *  [{2},   0,   0, {3}],
     *  [{4}, {5}, {6},   0]]
     * </pre>
     *
     * with <code>{n}</code> representing the call's information (which the
     * {@link Function} parameter will get).
     * <p>
     * This method takes parameters that allow it to be used to create an array with
     * knowledge of all the other {@link StackModel}s (e.g. when we want all arrays
     * to have the same dimensions).
     *
     * @param attributeFunction
     *            the function which will get desired attribute from
     *            {@link CallEntry}
     * @param depthSize
     *            the size of the array, depth-wise (number of rows)
     * @param addressSize
     *            the size of the array, address-wise (number of columns)
     * @param maxNumberOfCalls
     *            the maximum number of calls for each address to consider
     * @return the array
     */
    public double[][] createArray(Function<CallEntry, Long> attributeFunction, int depthSize, int addressSize, SortedMap<Long, Integer> maxNumberOfCalls) {
        double[][] array = new double[depthSize][addressSize];
        // For every absolute depth of the callstack, starting from the root
        // (even if they weren't targeted, i.e. if it's all zeros)
        for (int depthIndex = 0; depthIndex < depthSize; depthIndex++) {
            // Actual depth values start at 1
            int depth = depthIndex + 1;
            ListMultimap<Long, CallEntry> addressesEntries = fStackData.get(depth);
            if (addressesEntries != null) {
                int addressIndex = 0;
                // For every address, in order
                for (long address : maxNumberOfCalls.keySet()) {
                    List<CallEntry> entries = addressesEntries.get(address);
                    Integer maximumNumberOfEntries = maxNumberOfCalls.get(address);
                    int maxNumEntries = maximumNumberOfEntries != null ? maximumNumberOfEntries.intValue() : 0;
                    // Add entries in order
                    // Assuming that the padding is done by initializing the array
                    for (int i = 0; i < entries.size(); i++) {
                        array[depthIndex][addressIndex + i] = attributeFunction.apply(entries.get(i));
                    }
                    addressIndex += maxNumEntries;
                }
                // At this point: addressIndex == addressSize
            }
        }
        return array;
    }

    /**
     * Compute dimensions of the array representing this stack model
     *
     * @return the dimensions of the corresponding array (first=depth=rows,
     *         second=address=cols)
     */
    public Pair<Integer, Integer> getArraysDimensions() {
        return getArrayDimensions(getMaxNumberOfCalls());
    }

    /**
     * Compute dimensions of the array representing this stack model
     *
     * @param maxNumberOfCalls
     *            the maximum number of calls per address
     * @return the dimensions of the corresponding array (first=depth=rows,
     *         second=address=cols)
     */
    public Pair<Integer, Integer> getArrayDimensions(Map<Long, Integer> maxNumberOfCalls) {
        return new Pair<>(Collections.max(fStackData.keySet()),
                maxNumberOfCalls.values().stream().mapToInt(Integer::intValue).sum());
    }

    /**
     * For each address, find the max number of calls in every depth.
     * <p>
     * This is only for this specific stack model
     *
     * @return a map with the max number of calls per address
     */
    public SortedMap<Long, Integer> getMaxNumberOfCalls() {
        SortedMap<Long, Integer> maxNumberOfCalls = new TreeMap<>();
        for (int depth : fStackData.keySet()) {
            ListMultimap<Long, CallEntry> addressesEntries = fStackData.get(depth);
            if (addressesEntries != null) {
                for (long address : addressesEntries.keySet()) {
                    int size = addressesEntries.get(address).size();
                    Integer currentMax = maxNumberOfCalls.get(address);
                    if (currentMax == null || size > currentMax) {
                        maxNumberOfCalls.put(address, size);
                    }
                }
            }
        }
        return maxNumberOfCalls;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("\t\t" + this.getClass().getSimpleName() + "\n"); //$NON-NLS-1$ //$NON-NLS-2$
        builder.append("\t\t" + "offset" + "\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        builder.append(Dl4jUtils.nd4jArrayToString(Dl4jUtils.arrayToNd4j(toOffsetArray()), 2) + "\n"); //$NON-NLS-1$
        builder.append("\t\t" + "selftime" + "\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        builder.append(Dl4jUtils.nd4jArrayToString(Dl4jUtils.arrayToNd4j(toSelftimeArray()), 2) + "\n"); //$NON-NLS-1$
        builder.append("\t\t" + "timestamp = " + fTimestamp + "\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        for (int depth : fStackData.keySet()) {
            builder.append("\t\t" + "depth = " + depth + "\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            Multimap<Long, CallEntry> multimap = fStackData.get(depth);
            if (multimap != null) {
                for (long address : multimap.keySet()) {
                    builder.append("\t\t\t" + "address = 0x" + Long.toHexString(address) + "\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    builder.append("\t\t\t\t" + "Ordered Collection<CallEntry>" + "\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    for (CallEntry callEntry : multimap.get(address)) {
                        builder.append("\t\t\t\t" + callEntry + "\n"); //$NON-NLS-1$ //$NON-NLS-2$
                    }
                }
            }
        }
        return builder.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (fAddress ^ (fAddress >>> 32));
        result = prime * result + fDepth;
        result = prime * result + (int) (fDuration ^ (fDuration >>> 32));
        result = prime * result + ((fStackData == null) ? 0 : fStackData.hashCode());
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
        if (!(obj instanceof StackModel)) {
            return false;
        }
        StackModel other = (StackModel) obj;
        if (fAddress != other.fAddress) {
            return false;
        }
        if (fDepth != other.fDepth) {
            return false;
        }
        if (fDuration != other.fDuration) {
            return false;
        }
        if (fStackData == null) {
            if (other.fStackData != null) {
                return false;
            }
        } else if (!fStackData.equals(other.fStackData)) {
            return false;
        }
        if (fTimestamp != other.fTimestamp) {
            return false;
        }
        return true;
    }
}
