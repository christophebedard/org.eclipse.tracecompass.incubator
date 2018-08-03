/*******************************************************************************
 * Copyright (c) 2018 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.common.core.NonNullUtils;
import org.eclipse.tracecompass.incubator.internal.callstack.core.instrumented.callgraph.CalledFunction;
import org.eclipse.tracecompass.segmentstore.core.ISegment;

import com.google.common.collect.Iterables;

/**
 * (Ugly) workaround for building the parent-child callstack hierarchy.
 *
 * @author Christophe Bedard
 */
public class FunctionCall {

    private final long fStart;
    private final long fDuration;
    private final int fDepth;
    private final Long fSymbol;
    private long fSelftime;

    private @Nullable FunctionCall fParent = null;
    private final List<FunctionCall> fChildren = new ArrayList<>();

    /**
     * Constructor
     *
     * @param start
     *            the start timestamp of the call
     * @param duration
     *            the duration of the call
     * @param depth
     *            the depth of the call (>= 1)
     * @param symbol
     *            the function address as a symbol
     */
    public FunctionCall(long start, long duration, int depth, long symbol) {
        fStart = start;
        fDuration = duration;
        fDepth = depth;
        fSymbol = symbol;

        // selftime = duration (will stay like that until children are added)
        fSelftime = fDuration;
    }

    private void setParent(FunctionCall parent) {
        fParent = parent;
        parent.addChild(this);
    }

    private void addChild(FunctionCall child) {
        if (child.getParent() != this) {
            throw new IllegalArgumentException("This call is not its child's parent!"); //$NON-NLS-1$
        }
        fChildren.add(child);
        // Substract child duration from selftime
        fSelftime -= child.getDuration();
    }

    /**
     * @return the start timestamp of the call
     */
    public long getStart() {
        return fStart;
    }

    /**
     * @return the duration of the call
     */
    public long getDuration() {
        return fDuration;
    }

    /**
     * @return the end timestamp of the calls
     */
    public long getEnd() {
        return fStart + fDuration;
    }

    /**
     * @return the depth of the call (>= 1)
     */
    public int getDepth() {
        return fDepth;
    }

    /**
     * @return the function address
     */
    public Long getSymbol() {
        return fSymbol;
    }

    /**
     * @return the function address
     */
    public String getName() {
        return NonNullUtils.nullToEmptyString(getSymbol().toString());
    }

    /**
     * @return the selftime of the call
     */
    public long getSelftime() {
        return fSelftime;
    }

    /**
     * @return the parent call
     */
    public @Nullable FunctionCall getParent() {
        return fParent;
    }

    /**
     * @return the children calls
     */
    public List<FunctionCall> getChildren() {
        return fChildren;
    }

    @Override
    public String toString() {
        return '[' + String.valueOf(fStart) + ']' + " Duration: " + getDuration() + ", Self Time: " + fSelftime; //$NON-NLS-1$ //$NON-NLS-2$
    }

    @Override
    public int hashCode() {
        return Objects.hash(fDepth, fDuration, fSelftime, fStart, fSymbol);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        FunctionCall other = (FunctionCall) obj;
        if (fDepth != other.fDepth) {
            return false;
        }
        if (fDuration != other.fDuration) {
            return false;
        }
        if (fSelftime != other.fSelftime) {
            return false;
        }
        if (fStart != other.fStart) {
            return false;
        }
        if (fSymbol == null) {
            if (other.fSymbol != null) {
                return false;
            }
        } else if (!fSymbol.equals(other.fSymbol)) {
            return false;
        }
        return true;
    }

    /**
     * Workaround for re-creating an ordered hierarchy
     *
     * @param segments
     *            the segments as returned by the analysis segment store
     * @param targetDepth
     *            the target depth
     * @return the function calls with the correct hierarchy and selftime
     */
    public static Collection<org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.model.FunctionCall> recreateHierarchy(Iterable<@NonNull ISegment> segments, int targetDepth) {
        Map<Integer, TreeSet<org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.model.FunctionCall>> calls = new HashMap<>();
        final Comparator<org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.model.FunctionCall> callComparator = Comparator.comparing(FunctionCall::getStart);

        // Get calls
        int depth = 1;
        Iterable<@NonNull ISegment> callsAtDepth = getSegmentsAtDepth(segments, depth);
        while (Iterables.size(callsAtDepth) > 0) {
            TreeSet<org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.model.FunctionCall> callsSet = calls.get(depth);
            if (callsSet == null) {
                callsSet = new TreeSet<>(callComparator);
                calls.put(depth, callsSet);
            }

            // Process calls
            for (ISegment dCall : callsAtDepth) {
                CalledFunction depthCall = (CalledFunction) dCall;
                callsSet.add(new FunctionCall(depthCall.getStart(), depthCall.getLength(), depth, depthCall.getSymbol()));
            }
            // Get calls at next depth
            depth++;
            callsAtDepth = getSegmentsAtDepth(segments, depth);
        }

        // Add hierarchy links
        for (int d : calls.keySet()) {
            if (calls.containsKey(d + 1)) {
                // For each call at this depth
                TreeSet<FunctionCall> callsAtThisDepth = Objects.requireNonNull(calls.get(d));
                callsAtThisDepth.forEach(call -> {
                    // Check all calls at depth below
                    TreeSet<FunctionCall> callsBelow = Objects.requireNonNull(calls.get(d + 1));
                    callsBelow.forEach(callBelow -> {
                        // If it is a child, link them
                        if (isFirstParentOfSecond(call, callBelow)) {
                            callBelow.setParent(call);
                        }
                    });
                });
            }
        }

        // Sub-map of the target depth calls (the root calls)
        return calls.get(targetDepth);
    }

    private static boolean isFirstParentOfSecond(FunctionCall parent, FunctionCall child) {
        return parent.getStart() < child.getStart() && parent.getEnd() > child.getEnd();
    }

    private static Iterable<@NonNull ISegment> getSegmentsAtDepth(Iterable<@NonNull ISegment> segments, int depth) {
        return Iterables.filter(segments, (s) -> {
            return s instanceof CalledFunction && ((CalledFunction) s).getDepth() == depth;
        });
    }

}
