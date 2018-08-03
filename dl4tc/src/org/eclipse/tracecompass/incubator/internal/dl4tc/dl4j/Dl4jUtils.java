/*******************************************************************************
 * Copyright (c) 2018 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.dl4tc.dl4j;

import java.util.Arrays;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.string.NDArrayStrings;

/**
 * Class for DL4J/ND4J utils
 * 
 * @author Christophe Bedard
 */
public class Dl4jUtils {

	/**
	 * Force load dl4j
	 */
	public static void load() {
		INDArray dummy = Nd4j.create(new double[] { 1.0 }, new int[] { 1, 1 });
		dummy.toString();
	}

	/**
	 * Convert a 2D primitive array to a Nd4j {@link INDArray}
	 * 
	 * @param array
	 *            the multidimensional primitive array to convert
	 * @return the {@link INDArray}
	 */
	public static INDArray arrayToNd4j(double[][] array) {
		double[] flatArray = Arrays.stream(array).flatMapToDouble(Arrays::stream).toArray();
		INDArray arrayNd = Nd4j.create(flatArray, new int[] { array.length, array[0].length });
		return arrayNd;
	}

	/**
	 * Get a string representation of an {@link INDArray} with hardcoded format
	 * 
	 * @param array
	 *            the array to print
	 * @param numberOfTabs
	 *            the number of tabs used to indent the array
	 */
	public static String nd4jArrayToString(INDArray array, int numberOfTabs) {
		return nd4jArrayToString(array, numberOfTabs, "0");
	}

	/**
	 * Get a string representation of an {@link INDArray} with hardcoded format
	 * 
	 * @param array
	 *            the array to print
	 * @param numberOfTabs
	 *            the number of tabs used to indent the array
	 * @param decFormat
	 *            the decimal format used to display the numbers
	 */
	public static String nd4jArrayToString(INDArray array, int numberOfTabs, String decFormat) {
		String indentation = new String(new char[numberOfTabs]).replace("\0", "\t");
		return (indentation + new NDArrayStrings(",", decFormat).format(array).replaceAll("\n", "\n" + indentation));
	}

	/**
	 * Perform an element-wise less than or equal comparison (a <= b)
	 * 
	 * @param left
	 *            the array to the left (a)
	 * @param right
	 *            the array to the right (b)
	 * @return the resulting array, with 1 at position <code>i</code> if
	 *         <code>a_i <= b_i</code>, 0 otherwise
	 */
	public static INDArray leq(INDArray left, INDArray right) {
		INDArray result = Nd4j.create(left.shape());
		for (int i = 0; i < left.data().length(); ++i) {
			result.putScalar(i, (left.getDouble(i) <= right.getDouble(i)) ? 1.0 : 0.0);
		}
		return result;
	}
}
