package de.julielab.ipc.javabridge;

import org.testng.annotations.Ignore;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * In this class we test the basic functionality of the external python IO: Sending data, receiving data, waiting
 * for alle response lines to be received, etc.
 */
public class StdioBridgeTest {
    @Test
    public void simpleTest() throws InterruptedException {
        Options params = new Options<>(String.class);
        params.setExecutable("python");
        params.setExternalProgramTerminationSignal("exit");
        StdioBridge<String> bridge = new StdioBridge<>(params, "-u", "src/test/resources/python/simple.py");
        assertThatCode(bridge::start).doesNotThrowAnyException();
        bridge.send("Hallo");
        List<String> list = bridge.receive().collect(Collectors.toList());
        assertThat(list).isNotEmpty();
        assertThat(list).containsExactly("Got line: Hallo");
        bridge.send("Another line");
        assertThat(bridge.receive()).containsExactly("Got line: Another line");
        assertThatCode(bridge::stop).doesNotThrowAnyException();
    }

    /**
     * @throws IOException
     * @throws InterruptedException
     * @Ignore We don't do multi retrieval currently
     */
    @Ignore
    @Test
    public void simpleMultiSendSingleRetrieveTest() throws InterruptedException {
        Options params = new Options<>(String.class);
        params.setExecutable("python");
        params.setExternalProgramTerminationSignal("exit");
        StdioBridge<String> bridge = new StdioBridge<>(params, "-u", "src/test/resources/python/simple.py");
        assertThatCode(bridge::start).doesNotThrowAnyException();
        bridge.send("Hallo");
        bridge.send("Hallo");
        bridge.send("Hallo");
        bridge.send("Hallo");
        // We need to wait a moment to make sure that all the responses have been read
        Thread.sleep(500);
        List<String> list = bridge.receive().collect(Collectors.toList());
        assertThat(list).isNotEmpty();
        assertThat(list).hasSize(4);
        assertThat(list).containsExactly("Got line: Hallo", "Got line: Hallo", "Got line: Hallo", "Got line: Hallo");
        bridge.send("Another line");
        assertThat(bridge.receive()).containsExactly("Got line: Another line");
        assertThatCode(bridge::stop).doesNotThrowAnyException();
    }

    @Test
    public void simpleSendAndReceive() throws InterruptedException {
        Options params = new Options<>(String.class);
        params.setExecutable("python");
        params.setExternalProgramTerminationSignal("exit");
        StdioBridge<String> bridge = new StdioBridge<>(params, "-u", "src/test/resources/python/simple.py");
        assertThatCode(bridge::start).doesNotThrowAnyException();
        assertThat(bridge.sendAndReceive("Double Action")).containsExactly("Got line: Double Action");
        assertThat(bridge.sendAndReceive("Double Action")).containsExactly("Got line: Double Action");
        assertThat(bridge.sendAndReceive("Double Action")).containsExactly("Got line: Double Action");
        assertThatCode(bridge::stop).doesNotThrowAnyException();
    }

    @Test
    public void noiseTest() throws InterruptedException {
        Options<String> params = new Options<>(String.class);
        params.setExecutable("python");
        params.setResultLineIndicator(s -> s.startsWith("Output:"));
        params.setExternalProgramTerminationSignal("exit");
        StdioBridge<String> bridge = new StdioBridge<>(params, "-u", "src/test/resources/python/noise.py");
        assertThatCode(bridge::start).doesNotThrowAnyException();
        bridge.send("Hallo");
        List<String> list = bridge.receive().collect(Collectors.toList());
        assertThat(list).isNotEmpty();
        assertThat(list).containsExactly("Output: Hallo");
        bridge.send("Another line");
        assertThat(bridge.receive()).containsExactly("Output: Another line");
        assertThatCode(bridge::stop).doesNotThrowAnyException();
    }

    @Test
    public void noiseSendAndReceive() throws InterruptedException {
        Options<String> params = new Options<>(String.class);
        params.setExecutable("python");
        params.setResultLineIndicator(s -> s.startsWith("Output:"));
        params.setExternalProgramTerminationSignal("exit");
        StdioBridge bridge = new StdioBridge<>(params, "-u", "src/test/resources/python/noise.py");
        assertThatCode(bridge::start).doesNotThrowAnyException();
        assertThat(bridge.sendAndReceive("Double Action")).containsExactly("Output: Double Action");
        assertThat(bridge.sendAndReceive("Double Action")).containsExactly("Output: Double Action");
        assertThat(bridge.sendAndReceive("Double Action")).containsExactly("Output: Double Action");
        assertThatCode(bridge::stop).doesNotThrowAnyException();
    }

    @Test
    public void resultTransformator() throws InterruptedException {
        Options<String> params = new Options<>(String.class);
        params.setExecutable("python");
        params.setResultLineIndicator(s -> s.startsWith("Output:"));
        params.setResultTransformator(s -> s.substring(8));
        params.setExternalProgramTerminationSignal("exit");
        StdioBridge<String> bridge = new StdioBridge<>(params, "-u", "src/test/resources/python/noise.py");
        assertThatCode(bridge::start).doesNotThrowAnyException();
        assertThat(bridge.sendAndReceive("Double Action")).containsExactly("Double Action");
        assertThat(bridge.sendAndReceive("Double Action")).containsExactly("Double Action");
        assertThat(bridge.sendAndReceive("Double Action")).containsExactly("Double Action");
        assertThatCode(bridge::stop).doesNotThrowAnyException();
    }

    @Test
    public void receiveMultipleLines() throws Exception {
        Options<String> params = new Options<>(String.class);
        params.setExecutable("python");
        params.setExternalProgramTerminationSignal("exit");
        params.setMultilineResponseDelimiter("last line");
        StdioBridge<String> bridge = new StdioBridge<>(params, "-u", "src/test/resources/python/multilineResponse.py");
        assertThatCode(bridge::start).doesNotThrowAnyException();
        bridge.send("some data");
        final List<String> responses = bridge.receive().collect(Collectors.toList());
        assertThat(responses).hasSize(3);
        assertThatCode(bridge::stop).doesNotThrowAnyException();
    }
}
