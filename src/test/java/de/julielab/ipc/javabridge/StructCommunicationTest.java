package de.julielab.ipc.javabridge;

import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.util.Base64;
import java.util.stream.Stream;

public class StructCommunicationTest {
    @Test
    public void test() throws InterruptedException, IOException {
        Options<String> options = new Options();
        options.setExecutable("python");
        options.setResultLineIndicator(line -> line.startsWith("Vector bytes: "));
        options.setResultTransformator(line -> line.substring("Vector bytes: b".length()).replaceAll("'", ""));
        StdioBridge<String> bridge = new StdioBridge(options, "-u",  "src/test/resources/python/arrayVectorExchange.py");
        bridge.start();
        Stream<String> responses = bridge.sendAndReceive("nonsense");
        Stream<String> mow = bridge.sendAndReceive("something else");
        String s = responses.findAny().get();
        byte[] decode = Base64.getDecoder().decode(s);

        DoubleBuffer db = ByteBuffer.wrap(decode).asDoubleBuffer();
        for (int i = 0; i < db.capacity(); ++i)
        System.out.println(   db.get(i));
    }
}
