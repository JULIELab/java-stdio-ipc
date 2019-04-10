package de.julielab.ipc.javabridge;

import java.nio.ByteBuffer;
import java.util.function.Function;

public class ResultDecoders {
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
