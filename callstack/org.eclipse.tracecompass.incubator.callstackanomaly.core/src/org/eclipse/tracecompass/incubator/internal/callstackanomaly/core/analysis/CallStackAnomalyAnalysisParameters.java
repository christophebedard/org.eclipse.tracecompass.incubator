/*******************************************************************************
 * Copyright (c) 2018 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis;

import static org.eclipse.tracecompass.common.core.NonNullUtils.checkNotNull;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.CallStackAnomalyAnalysis.AnalysisType;

/**
 * Container for callstack anomaly analysis parameters
 *
 * @author Christophe Bourque Bedard
 */
public class CallStackAnomalyAnalysisParameters {

    /** The choice to serialize as primitive arrays */
    private final boolean fIsPrimitiveArrays;

    /** The analysis type */
    private final @NonNull AnalysisType fAnalysisType;
    /** The target depth */
    private final @NonNull Integer fTargetDepth;
    /** The N value */
    private final @Nullable Integer fNValue;
    /** The file/location of the model (to train or apply) */
    private final @Nullable String fModelContainerDirectoryPath;
    /** The learning rate */
    private final @Nullable Double fLearningRate;
    /** The number of epochs */
    private final @Nullable Integer fNumEpochs;
    /** The batch size */
    private final @Nullable Integer fBatchSize;
    /** The sizes of the hidden layers */
    private final @Nullable Integer[] fLayerSizes;
    /** The anomaly threshold */
    private final @Nullable Double fAnomalyThreshold;

    /**
     * Constructor
     * <p>
     * Set the analysis type, the target depth, and the other necessary parameters
     * depending on the analysis type. The rest can be set to <code>null</code>.
     *
     * @param type
     *            the analysis type
     * @param targetDepth
     *            the target depth
     * @param nValue
     *            the N value
     * @param modelFilePath
     *            the model file path
     * @param learningRate
     *            the learning rate
     * @param numEpochs
     *            the number of epochs
     * @param batchSize
     *            the batch size
     * @param layerSizes
     *            the sizes of the hidden layers
     * @param anomalyThreshold
     *            the anomaly threshold
     */
    public CallStackAnomalyAnalysisParameters(
            @NonNull AnalysisType type,
            @NonNull Integer targetDepth,
            @Nullable Integer nValue,
            @Nullable String modelFilePath,
            @Nullable Double learningRate,
            @Nullable Integer numEpochs,
            @Nullable Integer batchSize,
            @Nullable Integer[] layerSizes,
            @Nullable Double anomalyThreshold) {
        this(false, type, targetDepth, nValue, modelFilePath, learningRate, numEpochs, batchSize, layerSizes, anomalyThreshold);
    }

    /**
     * Constructor
     * <p>
     * Set the primitive arrays option, the analysis type, the target depth, and the
     * other necessary parameters depending on the analysis type. The rest can be
     * set to <code>null</code>.
     *
     * @param isPrimitiveArrays
     *            the choice to serialize as primitive arrays (true) or not (false)
     * @param type
     *            the analysis type
     * @param targetDepth
     *            the target depth
     * @param nValue
     *            the N value
     * @param modelContainerDirectoryPath
     *            the model directory container path
     * @param learningRate
     *            the learning rate
     * @param numEpochs
     *            the number of epochs
     * @param batchSize
     *            the batch size
     * @param layerSizes
     *            the sizes of the hidden layers
     * @param anomalyThreshold
     *            the anomaly threshold
     */
    public CallStackAnomalyAnalysisParameters(
            boolean isPrimitiveArrays,
            @NonNull AnalysisType type,
            @NonNull Integer targetDepth,
            @Nullable Integer nValue,
            @Nullable String modelContainerDirectoryPath,
            @Nullable Double learningRate,
            @Nullable Integer numEpochs,
            @Nullable Integer batchSize,
            @Nullable Integer[] layerSizes,
            @Nullable Double anomalyThreshold) {
        fIsPrimitiveArrays = isPrimitiveArrays;
        fAnalysisType = checkNotNull(type);
        fTargetDepth = checkNotNull(targetDepth);
        fNValue = nValue;
        fModelContainerDirectoryPath = modelContainerDirectoryPath;
        fLearningRate = learningRate;
        fNumEpochs = numEpochs;
        fBatchSize = batchSize;
        fLayerSizes = layerSizes;
        fAnomalyThreshold = anomalyThreshold;
    }

    /**
     * @return the choice to serialize as primitive arrays (true) or not (false)
     */
    public boolean getIsPrimitiveArrays() {
        return fIsPrimitiveArrays;
    }

    /**
     * @return the selected tab index
     */
    public @NonNull AnalysisType getSelectedAnalysisType() {
        return fAnalysisType;
    }

    /**
     * @return the selected target depth (adjusted from displayed depth to actual
     *         depth, i.e. first depth value is 0)
     */
    public @NonNull Integer getTargetDepth() {
        return fTargetDepth;
    }

    /**
     * @return the selected N value
     */
    public Integer getNValue() {
        return fNValue;
    }

    /**
     * @return the selected model directory container path
     */
    public String getModelDirectoryContainerPath() {
        return fModelContainerDirectoryPath;
    }

    /**
     * @return the learning rate
     */
    public Double getLearningRate() {
        return fLearningRate;
    }

    /**
     * @return the number of epochs
     */
    public Integer getNumEpochs() {
        return fNumEpochs;
    }

    /**
     * @return the batch size
     */
    public Integer getBatchSize() {
        return fBatchSize;
    }

    /**
     * @return the sizes of the hidden layers
     */
    public Integer[] getLayerSizes() {
        return fLayerSizes;
    }

    /**
     * @return the anomaly threshold
     */
    public Double getAnomalyThreshold() {
        return fAnomalyThreshold;
    }
}
