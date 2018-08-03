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
import java.io.IOException;

import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;
import org.eclipse.tracecompass.tmf.core.util.Pair;

/**
 * Utils for directory container for a NN model and related metadata
 *
 * @author Christophe Bedard
 */
public class ModelContainerUtils {

    private static final String MODEL_FILE_NAME = "model.zip"; //$NON-NLS-1$
    private static final String METADATA_FILE_NAME = "metadata" + CallStackMetadata.CALLSTACK_METADATA_EXTENSION; //$NON-NLS-1$

    /**
     * Write a NN model and metadata to a directory
     *
     * @param directoryPath
     *            the desired path to the directory containing the model and metadata
     * @param model
     *            the model
     * @param metadata
     *            the metadata
     * @return true if successful, false otherwise
     */
    public static boolean toContainer(String directoryPath, MultiLayerNetwork model, CallStackMetadata metadata) {
        File directory = new File(directoryPath);
        // Get actual file paths where to write the model and metadata
        File modelFile = new File(directory, MODEL_FILE_NAME);
        File metadataFile = new File(directory, METADATA_FILE_NAME);

        try {
            // Write model and metadata
            model.save(modelFile);
            CallStackMetadata.writeToFile(metadata, metadataFile);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Get model and metadata from directory container
     *
     * @param directoryPath
     *            the path to the directory containing the model and metadata
     * @return the NN model and metadata pair
     */
    public static Pair<MultiLayerNetwork, CallStackMetadata> fromContainer(String directoryPath) {
        File directory = new File(directoryPath);
        // Get actual file paths where to read the model and metadata
        File modelFile = new File(directory, MODEL_FILE_NAME);
        File metadataFile = new File(directory, METADATA_FILE_NAME);

        // Check that the files exist
        if (!(modelFile.exists() && metadataFile.exists())) {
            return null;
        }

        try {
            // Read from extracted files and return
            MultiLayerNetwork model = ModelSerializer.restoreMultiLayerNetwork(modelFile);
            CallStackMetadata metadata = CallStackMetadata.readFromFile(metadataFile);
            return new Pair<>(model, metadata);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
