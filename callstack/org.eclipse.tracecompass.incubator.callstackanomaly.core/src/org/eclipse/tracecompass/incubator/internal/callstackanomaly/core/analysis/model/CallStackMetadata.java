/*******************************************************************************
 * Copyright (c) 2018 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.model;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.SortedMap;

import org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.Activator;
import org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.detection.MetadataAnomalyDetector;

/**
 * Serializable container for callstack metadata
 * <p>
 * Since a NN model's input size currently depends on the callstack structure,
 * this metadata should be kept alongside the generated model file in order to
 * validate that a trace the user wants to apply the model on is compatible. If
 * it is not compatible, then this can be used to verify which callstacks in the
 * trace make it incompatible.
 *
 * @see MetadataAnomalyDetector
 *
 * @author Christophe Bedard
 */
public class CallStackMetadata implements Serializable {

    private static final long serialVersionUID = 1295323018300755214L;

    /** Extension for metadata file */
    public static final String CALLSTACK_METADATA_EXTENSION = ".dat"; //$NON-NLS-1$

    /*
     * Not serialized, since it's only needed right after the metadata was created.
     * This holds it between metadata generation and arrays generation.
     */
    private SortedMap<Long, Integer> fMaxNumberOfCalls = null;

    private int fDepthSize;
    private int fAddressSize;

    /**
     * Constructor
     *
     * @param maxNumberOfCalls
     *            the maximum number of calls at any depth for each address
     * @param depthSize
     *            the depth size of the array (number of rows)
     */
    public CallStackMetadata(SortedMap<Long, Integer> maxNumberOfCalls, int depthSize) {
        fMaxNumberOfCalls = maxNumberOfCalls;
        fDepthSize = depthSize;
        // Compute number of columns
        fAddressSize = computeAddressSize(maxNumberOfCalls);
    }

    private static int computeAddressSize(SortedMap<Long, Integer> maxNumberOfCalls) {
        return maxNumberOfCalls.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Get the maximum number of calls at any depth for each address
     *
     * @return the maximum number of calls at any depth for each address
     */
    public SortedMap<Long, Integer> getMaxNumberOfCalls() {
        return fMaxNumberOfCalls;
    }

    /**
     * Get the depth size
     *
     * @return the depth size (number of rows)
     */
    public int getDepthSize() {
        return fDepthSize;
    }

    /**
     * Get the number of columns for a callstack array with this metadata, which is
     * the sum of the maximum numbers of calls of all addresses
     *
     * @return the number of columns
     */
    public int getAddressSize() {
        return fAddressSize;
    }

    private void readObject(ObjectInputStream inputStream) throws IOException {
        fDepthSize = inputStream.readInt();
        fAddressSize = inputStream.readInt();
    }

    private void writeObject(ObjectOutputStream outputStream) throws IOException {
        outputStream.writeInt(fDepthSize);
        outputStream.writeInt(fAddressSize);
    }

    /**
     * Write callstack metadata to file
     *
     * @param data
     *            the metadata object to write
     * @param file
     *            the file to create and write
     */
    public static void writeToFile(CallStackMetadata data, File file) {
        try (ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(file))) {
            outputStream.writeObject(data);
        } catch (IOException e) {
            Activator.getInstance().logError("Problem writing model metadata to file", e); //$NON-NLS-1$
        }
    }

    /**
     * Read model metadata from file
     *
     * @param file
     *            the file to read
     * @return the metadata object read from the file
     */
    public static CallStackMetadata readFromFile(File file) {
        try (ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(file))) {
            return (CallStackMetadata) inputStream.readObject();
        } catch (IOException | ClassNotFoundException e) {
            Activator.getInstance().logError("Problem reading model metadata from file", e); //$NON-NLS-1$
        }
        return null;
    }

    /**
     * Get the model metadata file name with extension
     *
     * @return the model metadata file name with extension
     */
    public static String getMetadataFileName() {
        return CallStackMetadata.class.getName() + CALLSTACK_METADATA_EXTENSION;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("depthSize: " + fDepthSize); //$NON-NLS-1$
        builder.append("addressSize: " + fAddressSize); //$NON-NLS-1$
        return builder.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + fAddressSize;
        result = prime * result + fDepthSize;
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
        if (!(obj instanceof CallStackMetadata)) {
            return false;
        }
        CallStackMetadata other = (CallStackMetadata) obj;
        if (fAddressSize != other.fAddressSize) {
            return false;
        }
        if (fDepthSize != other.fDepthSize) {
            return false;
        }
        return true;
    }
}
