/*******************************************************************************
 * Copyright (c) 2018 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;

import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.common.core.log.TraceCompassLogUtils.ScopeLog;
import org.eclipse.tracecompass.incubator.callstack.core.lttng2.ust.LttngUstCallStackAnalysis;
import org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.Activator;
import org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.detection.CallStackAnomalyDetector;
import org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.detection.DeepAnomalyDetector;
import org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.detection.MetadataAnomalyDetector;
import org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.detection.StatisticalAnomalyDetector;
import org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.model.CallStackArray;
import org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.model.CallStackArraysSerialUtil;
import org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.model.CallStackMetadata;
import org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.model.FunctionCall;
import org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.model.ModelContainerUtils;
import org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.model.StackModel;
import org.eclipse.tracecompass.incubator.internal.dl4tc.dl4j.Dl4jUtils;
import org.eclipse.tracecompass.segmentstore.core.ISegment;
import org.eclipse.tracecompass.segmentstore.core.ISegmentStore;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.statesystem.AbstractTmfStateProvider;
import org.eclipse.tracecompass.tmf.core.statesystem.ITmfStateProvider;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.core.util.Pair;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;

/**
 * State provider for callstack anomaly detection
 *
 * @author Christophe Bedard
 */
public class CallStackAnomalyAnalysisProvider extends AbstractTmfStateProvider {

    /** The parameters */
    private final CallStackAnomalyAnalysisParameters fParameters;

    /** The callstack arrays container and serializer */
    private CallStackArraysSerialUtil fArrays;

    private @NonNull IProgressMonitor fMonitor;
    private boolean fHandled = false;
    private ITmfStateSystemBuilder ss;
    private File fSsFile;

    /** Attribute for information list */
    public static final String ATTRIBUTE_INFO = "Info"; //$NON-NLS-1$
    /** Attribute for min score */
    public static final String ATTRIBUTE_MIN = "min"; //$NON-NLS-1$
    /** Attribute for max score */
    public static final String ATTRIBUTE_MAX = "max"; //$NON-NLS-1$
    /** Attribute for threshold */
    public static final String ATTRIBUTE_THRESHOLD = "threshold"; //$NON-NLS-1$
    /** Attribute for results */
    public static final String ATTRIBUTE_RESULTS = "Results"; //$NON-NLS-1$

    private static final int WORK_DL4J_LOAD = 1;
    private static final int WORK_METADATA = 1;
    private static final int WORK_ARRAYS = 2;
    private static final int WORK_ANALYSIS = 3;
    private static final int WORK_TOTAL = WORK_DL4J_LOAD + WORK_METADATA + WORK_ARRAYS + WORK_ANALYSIS + 1;

    /**
     * Constructor
     * <p>
     * Set the analysis type, pass the needed parameters, and set the rest to null.
     *
     * @param trace
     *            the trace
     * @param parameters
     *            the analysis parameters
     * @param monitor
     *            the monitor (from the analysis)
     * @param ssFile
     *            the state system file for this analysis
     */
    public CallStackAnomalyAnalysisProvider(@NonNull ITmfTrace trace, CallStackAnomalyAnalysisParameters parameters, @NonNull IProgressMonitor monitor, File ssFile) {
        super(trace, CallStackAnomalyAnalysis.ID);
        fMonitor = monitor;
        fParameters = parameters;
        logParameters();

        // Delete state system supplementary file if it exists, otherwise the analysis
        // cannot be executed again
        fSsFile = ssFile;
        if (fSsFile.exists()) {
            fSsFile.delete();
        }
    }

    @Override
    protected void eventHandle(@NonNull ITmfEvent event) {
        if (fHandled) {
            return;
        }

        ss = getStateSystemBuilder();
        if (ss == null) {
            return;
        }

        executeAnalysis();
        fHandled = true;
    }

    /**
     * Execute analysis tasks
     *
     * @return true if successful, false otherwise
     */
    private boolean executeAnalysis() {
        try (ScopeLog sl = new ScopeLog(CallStackAnomalyAnalysis.LOGGER, Level.CONFIG, "callstack anomaly analysis")) { //$NON-NLS-1$
            CallStackAnomalyAnalysis.debug.println("begin analysis time=" + new SimpleDateFormat(CallStackAnomalyAnalysis.DATE_FORMAT).format(new Date())); //$NON-NLS-1$

            fMonitor.beginTask(CallStackAnomalyAnalysis.ANALYSIS_DESCRIPTION, WORK_TOTAL);

            loadDl4j();

            if (fMonitor.isCanceled()) {
                return false;
            }

            // Initialize callstack arrays container
            fArrays = new CallStackArraysSerialUtil(getSupplementaryFilesPath());

            // Generate the arrays if necessary
            if (!fArrays.exists()) {
                boolean isArraysGenSuccessful = generateArrays();
                if (!isArraysGenSuccessful) {
                    Activator.getInstance().logError("Callstack arrays generation failed"); //$NON-NLS-1$
                    // Abort and cleanup
                    fArrays.dispose();
                    return false;
                }
            } else {
                fMonitor.worked(WORK_METADATA + WORK_ARRAYS);
            }

            if (fMonitor.isCanceled()) {
                return false;
            }

            // Perform given analysis type
            performAnalysis();

            CallStackAnomalyAnalysis.debug.println("end analysis time=" + new SimpleDateFormat(CallStackAnomalyAnalysis.DATE_FORMAT).format(new Date())); //$NON-NLS-1$
            CallStackAnomalyAnalysis.debug.println();
        }

        return true;
    }

    /**
     * Perform chosen analysis
     */
    private void performAnalysis() {
        switch (fParameters.getSelectedAnalysisType()) {
        default:
        case STATISTICAL:
            try (ScopeLog sl = new ScopeLog(CallStackAnomalyAnalysis.LOGGER, Level.CONFIG, "statistical analysis")) { //$NON-NLS-1$
                performStatisticalAnalysis();
            }
            break;
        case NN_TRAIN:
            try (ScopeLog sl = new ScopeLog(CallStackAnomalyAnalysis.LOGGER, Level.CONFIG, "NN training")) { //$NON-NLS-1$
                performNnTraining();
            }
            break;
        case NN_APPLY:
            try (ScopeLog sl = new ScopeLog(CallStackAnomalyAnalysis.LOGGER, Level.CONFIG, "NN detection")) { //$NON-NLS-1$
                performNnDetection();
            }
            break;
        }
        fMonitor.worked(WORK_ANALYSIS);
    }

    /**
     * Perform statistical anomaly detection
     */
    private void performStatisticalAnalysis() {
        fMonitor.subTask("applying statistical anomaly detection"); //$NON-NLS-1$
        CallStackAnomalyDetector anomalyDetector = new StatisticalAnomalyDetector(
                fArrays,
                ss,
                fParameters);
        anomalyDetector.apply();
    }

    /**
     * Train neural network model and write model to disk
     */
    private void performNnTraining() {
        fMonitor.subTask("training NN model"); //$NON-NLS-1$
        MultiLayerNetwork nn = CallStackNnTrainer.train(
                fArrays,
                fParameters);
        if (!ModelContainerUtils.toContainer(fParameters.getModelDirectoryContainerPath(), nn, fArrays.getCallstackMetadata())) {
            Activator.getInstance().logError("Model container export failed"); //$NON-NLS-1$
        }
    }

    /**
     * Read model from disk, and check if it is compatible with the current trace.
     * If it is, perform anomaly detection, otherwise, find incompatible
     * sub-callstacks.
     */
    private void performNnDetection() {
        // Get model and its metadata
        Pair<MultiLayerNetwork, CallStackMetadata> containerData = ModelContainerUtils.fromContainer(fParameters.getModelDirectoryContainerPath());
        if (containerData == null) {
            Activator.getInstance().logError("Model container import failed; aborting"); //$NON-NLS-1$
            return;
        }
        MultiLayerNetwork nn = containerData.getFirst();
        CallStackMetadata modelMetadata = containerData.getSecond();

        // Check the model's metadata against the trace's metadata
        fMonitor.subTask("validating model against trace"); //$NON-NLS-1$
        // Load trace metadata
        fArrays.loadMetadata();
        boolean isValid = isMetadataCompatible(fArrays.getCallstackMetadata(), modelMetadata);

        CallStackAnomalyDetector anomalyDetector;
        if (isValid) {
            fMonitor.subTask("applying NN anomaly detection"); //$NON-NLS-1$
            anomalyDetector = new DeepAnomalyDetector(
                    fArrays,
                    ss,
                    nn,
                    fParameters);
        } else {
            fMonitor.subTask("applying metadata anomaly detection"); //$NON-NLS-1$
            anomalyDetector = new MetadataAnomalyDetector(
                    fArrays,
                    ss,
                    modelMetadata);
        }
        anomalyDetector.apply();
    }

    /**
     * Get callstack segment store and generate arrays needed for actual analysis
     *
     * @return true if successful, false otherwise
     */
    private boolean generateArrays() {
        try (ScopeLog sl = new ScopeLog(CallStackAnomalyAnalysis.LOGGER, Level.CONFIG, "arrays generation")) { //$NON-NLS-1$
            // Get callstack analysis
            fMonitor.subTask("acquiring callstack analysis results"); //$NON-NLS-1$
            ITmfTrace trace = getTrace();
            Iterable<LttngUstCallStackAnalysis> callstackAnalyses = TmfTraceUtils.getAnalysisModulesOfClass(trace, LttngUstCallStackAnalysis.class);
            @Nullable
            LttngUstCallStackAnalysis callstackAnalysis = Iterables.getFirst(callstackAnalyses, null);
            if (callstackAnalysis == null) {
                Activator.getInstance().logError("No callstack analysis found"); //$NON-NLS-1$
                return false;
            }

            // Make sure it is done
            callstackAnalysis.schedule();
            callstackAnalysis.waitForCompletion();

            ISegmentStore<@NonNull ISegment> callstack = callstackAnalysis.getSegmentStore();
            if (callstack == null) {
                Activator.getInstance().logError("No segment store"); //$NON-NLS-1$
                return false;
            }

            boolean isArraysFileGenSuccessful = generateArraysFile(callstack);
            if (!isArraysFileGenSuccessful) {
                Activator.getInstance().logError("Callstack arrays file generation failed"); //$NON-NLS-1$
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------------
    // Callstack arrays generation methods
    // ------------------------------------------------------------------------

    /**
     * Process trace for metadata, generate callstack arrays, and write them to a
     * file
     *
     * @param callstack
     *            the callstack, as a segment store
     * @return true if successful, false otherwise
     */
    private boolean generateArraysFile(ISegmentStore<@NonNull ISegment> callstack) {
        long checkpoint;

        // Only use calls at a target depth as sub-root calls for the moment
        fMonitor.subTask("filtering"); //$NON-NLS-1$

        Collection<org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.model.FunctionCall> rootCalls = FunctionCall.recreateHierarchy(callstack, fParameters.getTargetDepth());
        if (rootCalls.isEmpty()) {
            Activator.getInstance().logError("No root calls found"); //$NON-NLS-1$
            return false;
        }

        // Compute metadata
        fMonitor.subTask("gathering callstack information"); //$NON-NLS-1$
        checkpoint = System.currentTimeMillis();
        CallStackMetadata metadata = getTraceMetadata(rootCalls);
        CallStackAnomalyAnalysis.debug.println("total metadata=" + (System.currentTimeMillis() - checkpoint)); //$NON-NLS-1$
        fMonitor.worked(WORK_METADATA);

        // Generate and write arrays
        fMonitor.subTask("generating and writing arrays"); //$NON-NLS-1$
        fArrays.initWrite(metadata, fParameters.getIsPrimitiveArrays());
        checkpoint = System.currentTimeMillis();
        generateAndWriteArrays(rootCalls, metadata, fParameters.getIsPrimitiveArrays());
        fArrays.closeWrite();
        CallStackAnomalyAnalysis.debug.println("total arrays=" + (System.currentTimeMillis() - checkpoint)); //$NON-NLS-1$
        fMonitor.worked(WORK_ARRAYS);

        return true;
    }

    /**
     * Get overall trace metadata
     *
     * @param rootCalls
     *            the root calls
     * @return the resulting metadata
     */
    private CallStackMetadata getTraceMetadata(Iterable<org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.model.FunctionCall> rootCalls) {
        // Initialize maximums
        SortedMap<Long, MutableInt> maximums = new TreeMap<>();
        MutableInt maxDepth = new MutableInt();

        // For each callstack
        rootCalls.forEach(rootCall -> {
            // Process and get map of number of calls
            Table<Long, Integer, MutableInt> counts = HashBasedTable.create();
            computeMetadata(rootCall, counts, maxDepth);

            // Compare to current maximums and update
            updateMaxCounts(maximums, counts);
        });

        return new CallStackMetadata(
                Maps.transformEntries(maximums, (address, mutableInt) -> (mutableInt.get())),
                maxDepth.get());
    }

    /**
     * Compare the number of calls of each function per depth with the current
     * maximum. Update the latter if necessary.
     *
     * @param maximums
     *            the current maximum values
     * @param counts
     *            the counts from one individual callstack
     */
    private void updateMaxCounts(SortedMap<Long, MutableInt> maximums, Table<Long, Integer, MutableInt> counts) {
        // For each address
        for (Long address : counts.rowKeySet()) {
            // Get max from all depths
            int maxCalls = Collections.max(counts.row(address).values(), Comparator.comparingInt(MutableInt::get)).get();

            // Compare with current max and update if greater
            MutableInt currentMax = maximums.get(address);
            if (currentMax == null) {
                currentMax = new MutableInt(maxCalls);
                maximums.put(address, currentMax);
            } else if (maxCalls > currentMax.get()) {
                currentMax.set(maxCalls);
            }
        }
    }

    /**
     * Compute metadata for one callstack
     * <p>
     * Find the maximum depth and the number of calls of each function per depth.
     *
     * @param call
     *            the parent call to check
     * @param counts
     *            the current counts (to add to)
     * @param maxDepth
     *            the current maximum (to update)
     */
    private void computeMetadata(FunctionCall call, Table<Long, Integer, MutableInt> counts, MutableInt maxDepth) {
        long address = call.getSymbol();
        int depth = call.getDepth();

        // Process current calls
        if (depth > maxDepth.get()) {
            maxDepth.set(depth);
        }
        MutableInt countValue = counts.get(address, depth);
        if (countValue == null) {
            countValue = new MutableInt();
            counts.put(address, depth, countValue);
        }
        countValue.increment();

        // Process children
        call.getChildren().forEach(child -> computeMetadata(child, counts, maxDepth));
    }

    /**
     * Generate callstack arrays for given roots and write to file
     *
     * @param rootCalls
     *            the root calls to generate arrays for
     * @param metadata
     *            the trace's callstack metadata
     * @param isPrimitive
     *            the choice to serialize as primitive arrays (true) or not (false)
     */
    private void generateAndWriteArrays(Iterable<org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.model.FunctionCall> rootCalls, CallStackMetadata metadata, boolean isPrimitive) {
        SortedMap<Long, Integer> maximums = metadata.getMaxNumberOfCalls();
        int numRows = metadata.getDepthSize();
        int numCols = metadata.getAddressSize();
        rootCalls.forEach(rootCall -> {
            StackModel stackModel = new StackModel(rootCall);
            handleCall(rootCall, rootCall, stackModel);
            CallStackArray callstackArray = new CallStackArray(stackModel, maximums, numRows, numCols, isPrimitive);
            fArrays.write(callstackArray);
        });
    }

    /**
     * Handle a call to add it to a {@link StackModel} recursively
     *
     * @param call
     *            the function call to add to the data
     * @param rootCall
     *            the root call of the sub-callstack the call belongs to
     * @param model
     *            the {@link StackModel} to insert data into
     */
    private static void handleCall(FunctionCall call, FunctionCall rootCall, StackModel model) {
        long address = call.getSymbol();
        int depth = call.getDepth();
        long offset = call.getStart() - rootCall.getStart();
        long selfTime = call.getSelftime();

        model.addSelftime(depth, address, offset, selfTime);

        call.getChildren().forEach(child -> handleCall(child, rootCall, model));
    }

    // ------------------------------------------------------------------------
    // Validation methods
    // ------------------------------------------------------------------------

    /**
     * Compare metadata from the imported NN model to this trace's metadata, and
     * validate that they are compatible.
     * <p>
     * If this returns false, it may only mean that there is a single anomaly, or
     * that the model and trace metadata are completely incompatible (i.e. more
     * checks are needed).
     *
     * @param traceMetadata
     *            the current trace's metadata
     * @param modelMetadata
     *            the imported model's metadata
     * @return the validation result: true is valid, false otherwise
     */
    private static boolean isMetadataCompatible(CallStackMetadata traceMetadata, CallStackMetadata modelMetadata) {
        return MetadataAnomalyDetector.isMetadataCompatible(
                traceMetadata.getDepthSize(),
                traceMetadata.getAddressSize(),
                modelMetadata.getDepthSize(),
                modelMetadata.getAddressSize());
    }

    // ------------------------------------------------------------------------
    // Helper methods
    // ------------------------------------------------------------------------

    private @NonNull String getSupplementaryFilesPath() {
        return TmfTraceManager.getSupplementaryFileDir(getTrace());
    }

    private class MutableInt {
        private int fValue;

        public MutableInt() {
            this(0);
        }

        public MutableInt(int value) {
            fValue = value;
        }

        public void increment() {
            fValue++;
        }

        public void set(int newValue) {
            fValue = newValue;
        }

        public int get() {
            return fValue;
        }
    }

    @Override
    public void dispose() {
        CallStackAnomalyAnalysis.debug.flush();
        super.dispose();
    }

    @Override
    public int getVersion() {
        return 0;
    }

    @Override
    public @NonNull ITmfStateProvider getNewInstance() {
        return new CallStackAnomalyAnalysisProvider(getTrace(), fParameters, fMonitor, fSsFile);
    }

    /**
     * Force-load dl4j
     */
    @Deprecated
    private void loadDl4j() {
        fMonitor.subTask("loading dl4j"); //$NON-NLS-1$
        long checkpoint = System.currentTimeMillis();
        Dl4jUtils.load();
        CallStackAnomalyAnalysis.debug.println("total lib=" + (System.currentTimeMillis() - checkpoint)); //$NON-NLS-1$
        fMonitor.worked(WORK_DL4J_LOAD);
    }

    /**
     * Debug
     */
    @Deprecated
    private void logParameters() {
        CallStackAnomalyAnalysis.debug.println("type=" + fParameters.getSelectedAnalysisType()); //$NON-NLS-1$
        CallStackAnomalyAnalysis.debug.println("target depth=" + fParameters.getTargetDepth()); //$NON-NLS-1$
        CallStackAnomalyAnalysis.debug.println("N value=" + fParameters.getNValue()); //$NON-NLS-1$
        CallStackAnomalyAnalysis.debug.println("learning rate=" + fParameters.getLearningRate()); //$NON-NLS-1$
        CallStackAnomalyAnalysis.debug.println("num epochs=" + fParameters.getNumEpochs()); //$NON-NLS-1$
        CallStackAnomalyAnalysis.debug.println("batch size=" + fParameters.getBatchSize()); //$NON-NLS-1$
        CallStackAnomalyAnalysis.debug.println("anomaly threshold=" + fParameters.getAnomalyThreshold()); //$NON-NLS-1$
    }
}
