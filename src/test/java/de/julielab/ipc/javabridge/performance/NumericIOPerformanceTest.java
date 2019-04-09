package de.julielab.ipc.javabridge.performance;

import de.julielab.ipc.javabridge.Options;
import de.julielab.ipc.javabridge.StdioBridge;
import org.testng.annotations.Ignore;
import org.testng.annotations.Test;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * This class is not so much a unit test as it is a speed comparison from which we want to learn if it is
 * worth it to convert a numeric vector into a byte representation in the python script and unpack it back
 * to a vector on the receiving end instead of just sending strings hence and forth.
 * <p>
 * The output of a run done on a MacBook Air mid2013 i7:
 * <p>
 * String: 27241
 * BinaryDoubles: 7688
 */
public class NumericIOPerformanceTest {
    private int numRuns = 100000;

    @Test
    public void run() throws Exception {
        long timeString = runStringExchange();
        long timeBinaryDoubles = runBinaryDoubleExchange();

        System.out.println("String: " + timeString);
        System.out.println("BinaryDoubles: " + timeBinaryDoubles);
    }

    public long runStringExchange() throws Exception {
        Options<String> options = new Options<>(String.class);
        options.setExternalProgramTerminationSignal("exit");
        options.setExecutable("python");

        StdioBridge<String> bridge = new StdioBridge<>(options, "-u", "src/test/resources/python/numeric/stringio.py");
        bridge.start();
        long time = System.currentTimeMillis();
        for (int i = 0; i < numRuns; i++) {
            final String stringVector = bridge.sendAndReceive("dummy").findAny().get();
            final double[] doubles = Stream.of(stringVector.split("[\\]\\[,]")).filter(Predicate.not(String::isEmpty)).mapToDouble(Double::parseDouble).toArray();
        }
        time = System.currentTimeMillis() - time;
        bridge.stop();
        return time;
    }

    public long runBinaryDoubleExchange() throws Exception {
        Options<byte[]> options = new Options<>(byte[].class);
        options.setExternalProgramTerminationSignal("exit");
        options.setExecutable("python");

        StdioBridge<byte[]> bridge = new StdioBridge<>(options, "-u", "src/test/resources/python/numeric/binarydoubleio.py");
        bridge.start();
        long time = System.currentTimeMillis();
        for (int i = 0; i < numRuns; i++) {
            final byte[] byteArrayVector = bridge.sendAndReceive("dummy").findAny().get();
            DoubleBuffer db = ByteBuffer.wrap(byteArrayVector).asDoubleBuffer();
            double[] array = new double[db.capacity()];
            for (int j = 0; j < db.capacity(); j++)
                array[j] = db.get(j);
        }
        time = System.currentTimeMillis() - time;
        bridge.stop();
        return time;
    }
}
