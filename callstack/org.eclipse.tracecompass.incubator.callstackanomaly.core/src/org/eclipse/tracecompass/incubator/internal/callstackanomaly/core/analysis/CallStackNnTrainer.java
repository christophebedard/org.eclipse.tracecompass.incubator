/*******************************************************************************
 * Copyright (c) 2018 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis;

import java.util.Collections;

import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration.ListBuilder;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.model.CallStackArraysSerialUtil;
import org.eclipse.tracecompass.incubator.internal.callstackanomaly.core.analysis.model.CallStackMetadata;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.learning.config.AdaGrad;
import org.nd4j.linalg.lossfunctions.LossFunctions;

/**
 * Class for NN model training
 *
 * @author Christophe Bedard
 */
public class CallStackNnTrainer {

    private static final int RANDOM_SEED = 12345;

    /**
     * Train model
     *
     * @param arrays
     *            the callstack arrays container
     * @param params
     *            the parameters
     * @return the trained NN model
     */
    public static MultiLayerNetwork train(CallStackArraysSerialUtil arrays, CallStackAnomalyAnalysisParameters params) {
        double learningRate = params.getLearningRate();
        int numEpochs = params.getNumEpochs();
        int batchSize = params.getBatchSize();
        Integer[] layerSizes = params.getLayerSizes();

        // Get input size from arrays
        CallStackDataSetIterator iter = new CallStackDataSetIterator(arrays, batchSize);
        CallStackMetadata traceMetadata = arrays.getCallstackMetadata();
        final int inputSize = 2 * traceMetadata.getAddressSize() * traceMetadata.getDepthSize();

        // Create model
        ListBuilder builder = new NeuralNetConfiguration.Builder()
                .seed(RANDOM_SEED)
                .weightInit(WeightInit.XAVIER)
                .updater(new AdaGrad(learningRate))
                .activation(Activation.RELU)
                .l2(0.00001)
                .list();

        // First layer
        builder.layer(0, new DenseLayer.Builder().nIn(inputSize).nOut(layerSizes[0]).build());
        // Hidden layers
        final int numHiddenLayers = layerSizes.length;
        for (int i = 0; i < (numHiddenLayers - 1); ++i) {
            final int layerNumber = i + 1;
            final int nIn = layerSizes[i];
            final int nOut = layerSizes[i + 1];
            builder.layer(layerNumber, new DenseLayer.Builder().nIn(nIn).nOut(nOut).build());
        }
        // Last layer
        MultiLayerConfiguration conf = builder
                .layer(numHiddenLayers, new OutputLayer.Builder().nIn(layerSizes[numHiddenLayers - 1]).nOut(inputSize)
                .lossFunction(LossFunctions.LossFunction.MSE)
                .build())
        .pretrain(false).backprop(true)
        .build();

        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.setListeners(Collections.singletonList(new ScoreIterationListener(Integer.MAX_VALUE)));

        // Train
        long checkpoint = System.currentTimeMillis();
        for (int epoch = 0; epoch < numEpochs; epoch++) {
            while (iter.hasNext()) {
                net.fit(iter.next());
            }
            iter.reset();
            System.out.println("Epoch " + epoch + " complete"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        CallStackAnomalyAnalysis.debug.println("total training=" + (System.currentTimeMillis() - checkpoint)); //$NON-NLS-1$

        iter.dispose();

        return net;
    }
}
