package de.julielab.ipc.javabridge;

import java.nio.ByteBuffer;
import java.util.function.Function;

/**
 * This class offers conversion functions from a byte[] to some encoded data structure therein. They are to be
 * used in a {@link java.util.stream.Stream#map(Function)} call to the stream returned by {@link StdioBridge#receive()} or
 * one of the <tt>sendAndReceive()</tt> methods.
 */
public class ResultDecoders {
    /**
     * This decode converts a byte array into an array of double vectors. The format of the byte array is
     * required to look the following:
     * <ol>
     *     <li>The first 4 bytes must represent an integer that indicates the number of vectors (double arrays) returned</li>
     *     <li>The bytes 5 to 8 must represent an integer that indicates the length of each vector (the length must be uniform)</li>
     *     <li>The rest of the bytes must represent doubles that fit in number to the above described dimensions</li>
     * </ol>
     */
    public static Function<byte[], double[][]> decodeVectors = bytes -> {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        final int numVectors = buffer.getInt();
        final int vectorLength = buffer.getInt();
        double[][] vectors = new double[numVectors][];
        for (int i = 0; i < numVectors; i++) {
            vectors[i] = new double[vectorLength];
            for (int j = 0; j < vectorLength; j++) {
                vectors[i][j] = buffer.getDouble();
            }
        }
        return vectors;
    };
}
