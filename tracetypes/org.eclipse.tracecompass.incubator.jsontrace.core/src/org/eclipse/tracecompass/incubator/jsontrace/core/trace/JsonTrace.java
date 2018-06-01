/*******************************************************************************
 * Copyright (c) 2018 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.jsontrace.core.trace;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.incubator.internal.jsontrace.core.Activator;
import org.eclipse.tracecompass.incubator.jsontrace.core.job.SortingJob;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfTraceException;
import org.eclipse.tracecompass.tmf.core.io.BufferedRandomAccessFile;
import org.eclipse.tracecompass.tmf.core.project.model.ITmfPropertiesProvider;
import org.eclipse.tracecompass.tmf.core.trace.ITmfContext;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTraceKnownSize;
import org.eclipse.tracecompass.tmf.core.trace.TmfContext;
import org.eclipse.tracecompass.tmf.core.trace.TmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.core.trace.indexer.ITmfPersistentlyIndexable;
import org.eclipse.tracecompass.tmf.core.trace.location.ITmfLocation;
import org.eclipse.tracecompass.tmf.core.trace.location.TmfLongLocation;

/**
 * Json trace. Can read unsorted or sorted JSON traces.
 *
 * @author Katherine Nadeau
 */
public abstract class JsonTrace extends TmfTrace implements ITmfPersistentlyIndexable, ITmfPropertiesProvider, ITmfTraceKnownSize {

    private static final int CHECKPOINT_SIZE = 10000;
    private static final int ESTIMATED_EVENT_SIZE = 50;
    protected static final TmfLongLocation NULL_LOCATION = new TmfLongLocation(-1L);
    private static final TmfContext INVALID_CONTEXT = new TmfContext(NULL_LOCATION, ITmfContext.UNKNOWN_RANK);

    protected static final int MAX_LINES = 100;
    protected static final int MAX_CONFIDENCE = 100;
    protected final @NonNull Map<@NonNull String, @NonNull String> fProperties = new LinkedHashMap<>();

    protected File fFile;

    protected RandomAccessFile fFileInput;

    public abstract void goToCorrectStart(RandomAccessFile rafile) throws IOException;

    public abstract String getTraceType();

    public abstract String getTsKey();

    public abstract Integer getBracketsToSkip();

    @Override
    public void initTrace(IResource resource, String path, Class<? extends ITmfEvent> type) throws TmfTraceException {
        super.initTrace(resource, path, type);
        fProperties.put("Type", getTraceType()); //$NON-NLS-1$
        String dir = TmfTraceManager.getSupplementaryFileDir(this);
        fFile = new File(dir + new File(path).getName());
        if (!fFile.exists()) {
            Job sortJob = new SortingJob(this, path, getTsKey(), getBracketsToSkip());
            sortJob.schedule();
            while (sortJob.getResult() == null) {
                try {
                    sortJob.join();
                } catch (InterruptedException e) {
                    throw new TmfTraceException(e.getMessage(), e);
                }
            }
            IStatus result = sortJob.getResult();
            if (!result.isOK()) {
                throw new TmfTraceException("Job failed " + result.getMessage()); //$NON-NLS-1$
            }
        }
        try {
            fFileInput = new BufferedRandomAccessFile(fFile, "r"); //$NON-NLS-1$
            goToCorrectStart(fFileInput);
        } catch (IOException e) {
            throw new TmfTraceException(e.getMessage(), e);
        }
    }

    @Override
    public synchronized void dispose() {
        if (fFileInput != null) {
            try {
                fFileInput.close();
            } catch (IOException e) {
                Activator.getInstance().logError("Error disposing trace. File: " + getPath(), e); //$NON-NLS-1$
            }
        }
        super.dispose();
    }

    @Override
    public double getLocationRatio(ITmfLocation location) {
        return ((Long) getCurrentLocation().getLocationInfo()).doubleValue() / fFile.length();
    }

    @Override
    public ITmfContext seekEvent(ITmfLocation location) {
        if (fFile == null) {
            return INVALID_CONTEXT;
        }
        final TmfContext context = new TmfContext(NULL_LOCATION, ITmfContext.UNKNOWN_RANK);
        if (NULL_LOCATION.equals(location) || fFile == null) {
            return context;
        }
        try {
            if (location == null) {
                fFileInput.seek(1);
            } else if (location.getLocationInfo() instanceof Long) {
                fFileInput.seek((Long) location.getLocationInfo());
            }
            context.setLocation(new TmfLongLocation(fFileInput.getFilePointer()));
            context.setRank(0);
            return context;
        } catch (final FileNotFoundException e) {
            Activator.getInstance().logError("Error seeking event. File not found: " + getPath(), e); //$NON-NLS-1$
            return context;
        } catch (final IOException e) {
            Activator.getInstance().logError("Error seeking event. File: " + getPath(), e); //$NON-NLS-1$
            return context;
        }
    }

    @Override
    public ITmfContext seekEvent(double ratio) {
        File file = fFile;
        if (file == null) {
            return INVALID_CONTEXT;
        }
        long filePos = (long) (file.length() * ratio);
        long estimatedRank = filePos / ESTIMATED_EVENT_SIZE;
        return seekEvent(new TmfLongLocation(estimatedRank));
    }

    @Override
    public ITmfLocation getCurrentLocation() {
        long temp = -1;
        try {
            temp = fFileInput.getFilePointer();
        } catch (IOException e) {
            // swallow it for now
        }
        return new TmfLongLocation(temp);
    }

    @Override
    public @NonNull Map<@NonNull String, @NonNull String> getProperties() {
        return fProperties;
    }

    @Override
    public ITmfLocation restoreLocation(ByteBuffer bufferIn) {
        return new TmfLongLocation(bufferIn);
    }

    @Override
    public int getCheckpointSize() {
        return CHECKPOINT_SIZE;
    }

    /**
     * Wrapper to get a character reader, allows to reconcile between java.nio and
     * java.io
     *
     * @author Matthew Khouzam
     *
     */
    public static interface IReaderWrapper {
        /**
         * Read the next character
         *
         * @return the next char
         * @throws IOException
         *             out of chars to read
         */
        int read() throws IOException;
    }

    /**
     * Manually parse a string of JSON. High performance to extract one object
     *
     * @param parser
     *            the reader
     * @return a String with a json object
     * @throws IOException
     *             end of file, file not found or such
     */
    public static @Nullable String readNextEventString(IReaderWrapper parser) throws IOException {
        StringBuffer sb = new StringBuffer();
        int scope = -1;
        int arrScope = 0;
        boolean inQuotes = false;
        int elem = parser.read();
        while (elem != -1) {
            if (elem == '"') {
                inQuotes = !inQuotes;
            } else {
                if (inQuotes) {
                    // do nothing
                } else if (elem == '[') {
                    arrScope++;
                } else if (elem == ']') {
                    if (arrScope > 0) {
                        arrScope--;
                    } else {
                        return null;
                    }
                } else if (elem == '{') {
                    scope++;
                } else if (elem == '}') {
                    if (scope > 0) {
                        scope--;
                    } else {
                        sb.append((char) elem);
                        return sb.toString();
                    }
                }
            }
            if (scope >= 0) {
                sb.append((char) elem);
            }
            elem = parser.read();
        }
        return null;
    }

    @Override
    public int size() {
        RandomAccessFile fileInput = fFileInput;
        if (fileInput == null) {
            return 0;
        }
        long length = 0;
        try {
            length = fileInput.length();
        } catch (IOException e) {
            // swallow it for now
        }
        return length > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) length;
    }

    @Override
    public int progress() {
        RandomAccessFile fileInput = fFileInput;
        if (fileInput == null) {
            return 0;
        }
        long length = 0;
        try {
            length = fileInput.getFilePointer();
        } catch (IOException e) {
            // swallow it for now
        }
        return length > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) length;
    }
}
