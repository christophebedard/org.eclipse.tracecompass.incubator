/*******************************************************************************
 * Copyright (c) 2018 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.model;

import java.util.Objects;

/**
 * Structure that represents an offset and a selftime.
 *
 * @author Christophe Bedard
 */
public class CallEntry {
    /** The call's offset with the root of the sub-callstack it belongs to */
    private final long fOffset;
    /** The call's selftime */
    private final long fSelftime;

    /**
     * Constructor
     *
     * @param offset
     *            the offset with the sub-callstack's root
     *            (start time difference)
     * @param selftime
     *            the call's selftime
     */
    public CallEntry(long offset, long selftime) {
        fOffset = offset;
        fSelftime = selftime;
    }

    /**
     * @return the offset
     */
    public long getOffset() {
        return fOffset;
    }

    /**
     * @return the selftime
     */
    public long getSelftime() {
        return fSelftime;
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
        CallEntry other = (CallEntry) obj;
        return fOffset == other.fOffset && fSelftime == other.fSelftime;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fOffset, fSelftime);
    }

    @Override
    public String toString() {
        return "offset = " + fOffset + " | selftime = " + fSelftime; //$NON-NLS-1$ //$NON-NLS-2$
    }
}
