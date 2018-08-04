package de.julielab.ipc.javabridge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <p>
 * This class offers a possibility to communicate from within a Java application with another, external program.
 * </p>
 * <p>
 * This is useful for the connection to applications written in a language for which no direct binding from Java exist.
 * This class resorts to the simple exchange of messages via standard
 * input and output, thus, a pipe. For this purpose, the external process is started using a {@link ProcessBuilder}.
 * Once the external process is started, messages can be send to the process via {@link #send(String)} and
 * {@link #sendAndReceive(String)}. After a message is sent, an answer can be received via {@link #receive()}.
 * </p>
 * <p>The {@link #send(String)} method is <em>synchronous</em>, i.e. this class waits until the given message
 * has been sent to the external process. To not miss the answer, a background thread is always reading lines
 * from the standard input of the external process. To get these messages, call {@link #receive()}. This method
 * either gets existing messages that already have been read or it <em>waits</em> for a message to arrive. Thus,
 * the method might block <em>indefinitely</em> in case that no message is sent by the external process.
 * This is way calls to {@link #send(String)} and {@link #receive()} should always come in pairs. For this purpose,
 * the method {@link #sendAndReceive(String)} is helpful.</p>
 * <p>At the moment, this class supports arbitrary requests and one-line responses. Thus, the external process
 * must either respond for each request with exactly one line or, if other output is necessary for the external
 * program, is might output multiple lines but should then mark the actual output line somehow, e.g. by a prefix like
 * <code>Result:</code> or similar</p>
 * <p>The {@link Options} class accepts a {@link Predicate} to recognize such specific output lines. The Options
 * class also allows setting the actual program command whereas the program arguments are set to the descriptor of this
 * class. Refer to {@link Options} for details about configuration settings offered there.</p>
 * <p>The terminate the external process and the receiver thread and also the STDERR reading thread, call
 * {@link #stop()} one the bridge is no longer required.</p>
 * <p>IMPORTANT NOTE FOR PYTHON PROGRAMS: When working with Python for the external program, always specify the <em>-u</em>
 * switch in the program arguments. It causes Python to refrain from internal input/output buffering. Without the switch,
 * the communication between Java and Python via STDOUT/IN will most like get stuck because the buffers don't get
 * filled at some point an no data is actually sent.</p>
 */
public class StdioBridge {

    private final static Logger log = LoggerFactory.getLogger(StdioBridge.class);

    private String[] arguments;
    private Process process;
    private Communicator communicator;
    private ErrorStreamConsumer errorStreamConsumer;
    private Options options;

    public StdioBridge(Options options, String... arguments) {
        this.options = options;
        if (arguments.length == 0)
            throw new IllegalArgumentException("No external program to run has been specified.");
        this.arguments = arguments;
    }


    public void start() throws IOException {
        String[] command = new String[arguments.length + 1];
        command[0] = options.getExecutable();
        System.arraycopy(arguments, 0, command, 1, arguments.length);
        ProcessBuilder builder = new ProcessBuilder(command);
        process = builder.start();
        errorStreamConsumer = new
                ErrorStreamConsumer(process.getErrorStream());
        errorStreamConsumer.start();
        log.info("Started process with arguments {}", Arrays.toString(arguments));
        BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
        //BufferedInputStream bis = new BufferedInputStream(process.getInputStream());
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        communicator = new Communicator(br, bw, options.getResultLineIndicator());
    }

    public void stop() throws InterruptedException {
        if (options.getExternalProgramTerminationSignal() != null) {
            communicator.send(options.getExternalProgramTerminationSignal());
            log.info("Sent the external process termination signal \"{}\" and waiting for the process to end.", options.getExternalProgramTerminationSignal());
            process.waitFor();
        }
        if (communicator != null)
            communicator.close();
        if (errorStreamConsumer != null)
            errorStreamConsumer.close();
        if (process != null) {
            if (process.isAlive()) {
                process.destroy();
                process.waitFor();
            }
            int exitValue = process.exitValue();
            log.info("Process exited with exit value {}. The run arguments was: {}", exitValue, Arrays.toString(arguments));
        }
    }

    /**
     * Synchronously sends the given string data to the external program. It must be programmed in a way to accept
     * these data.
     * @param data The message to be sent to the external process.
     */
    public void send(String data) {
        if (communicator == null)
            throw new IllegalStateException("The internal Python-Java communicator has not been initialized. Did you forget to execute start()?");
        communicator.send(data);
    }

    /**
     * <p>Receives data from the external process.</p>
     * <p>For this purpose, this method will <em>block</em> until data is available. If {@link Options#getResultLineIndicator()}
     * is given, the method will wait until a line is encountered that is accepted by the respective predicate.</p>
     * <p>This method should only be called if {@link #send(String)} has been called before. Otherwise, there will
     * probably be nothing to read the the method will block indefinitely</p>
     *
     * @return The next data line received from the external process.
     * @throws InterruptedException If the method is interrupted while waiting for the next input.
     */
    public Stream<String> receive() throws InterruptedException {
        List<String> lines = communicator.receive();
        //if (options.getResultTransformator() != null)
          //  return lines.stream().map(options.getResultTransformator()::apply);
        return lines.stream();
    }

    /**
     * Just calls {@link #send(String)} and {@link #receive()} one ofter the other. Exclusively using this method
     * ensures that there is always something to read and the receive method does not block forever.
     * @param data The data to send.
     * @return The received response.
     * @throws InterruptedException It waiting for a response is interrupted.
     */
    public Stream<String> sendAndReceive(String data) throws InterruptedException {
        send(data);
        return receive();
    }


class BinaryCommunicator {
    private final Logger log = LoggerFactory.getLogger(BinaryCommunicator.class);
    private final Reader reader;
    private final Writer writer;
    private BufferedInputStream bis;
    private BlockingQueue<byte[]> inputDeque = new LinkedBlockingQueue<>();
    private Deque<String> outputDeque = new ArrayDeque<>();
    private BufferedWriter bw;
    private Predicate<String> resultLineIndicator;

    public BinaryCommunicator(BufferedInputStream bis, BufferedWriter bw, Predicate<String> resultLineIndicator) {
        this.bis = bis;
        this.bw = bw;
        this.resultLineIndicator = resultLineIndicator;
        this.reader = new Reader();
        this.writer = new Writer();

        reader.start();
    }

    public void close() {
        if (!inputDeque.isEmpty())
            log.warn("Python-Java bridge was closed before all data was received from the external program. {} bytes are outstanding.", inputDeque.stream().count());
        reader.interrupt();
        if (!outputDeque.isEmpty())
            log.warn("Python-Java bridge was closed before all data was sent to the external program: " + outputDeque.stream().collect(Collectors.joining(", ")));
    }

    public void send(String data) {
        outputDeque.add(data);
        writer.run();
    }

    public List<byte[]> receive() throws InterruptedException {
        List<byte[]> receivedData = new ArrayList<>();

        synchronized (reader) {
            if (inputDeque.isEmpty()) {
                log.trace("Waiting for something to be read");
                reader.wait();
            }
            inputDeque.drainTo(receivedData);
        }
        log.trace("Reading from internal buffer: " + receivedData);
        return receivedData;
    }

    public List<byte[]> sendAndReceive(String data) throws InterruptedException {
        send(data);
        return receive();
    }

    /**
     * A thread reading all output from the Python program
     */
    private class Reader extends Thread {
        public void run() {
            setName("ReaderThread");
            log.debug("Starting reader thread");
            String line;
            try {
                byte[] buffer = new byte[1];
                int bytesRead = -1;
                while ((bytesRead = bis.read(buffer)) != -1) {
                    synchronized (this) {
                       // if (resultLineIndicator == null || resultLineIndicator.test(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8))) {
                            if (bytesRead > 0) {
                                byte[] b = new byte[bytesRead];
                                System.arraycopy(buffer, 0, b, 0, bytesRead);
                                //inputDeque.add(b);
                            }
                            notify();
                        }
                //    }
                    log.trace("Received: {} bytes", bytesRead);
                }
                System.out.println("AFTEr WHILE");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class Writer {
        public void run() {
            try {
                while (!outputDeque.isEmpty()) {
                    String toWrite = outputDeque.pop();
                    log.trace("Writing: " + toWrite);
                    bw.write(toWrite);
                    bw.newLine();
                    // Important! When we don't flush, the data so sent will most like just reside in the buffer,
                    // at least the last part of it, and dont get sent to the external process. The external process
                    // will then probably block indefinitely, waiting for our request to finish.
                    bw.flush();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
}


class Communicator {
    private final static Logger log = LoggerFactory.getLogger(Communicator.class);
    private final Reader reader;
    private final Writer writer;
    private BufferedReader br;
    private BlockingQueue<String> inputDeque = new LinkedBlockingQueue<>();
    private Deque<String> outputDeque = new ArrayDeque<>();
    private BufferedWriter bw;
    private Predicate<String> resultLineIndicator;

    public Communicator(BufferedReader br, BufferedWriter bw, Predicate<String> resultLineIndicator) {
        this.br = br;
        this.bw = bw;
        this.resultLineIndicator = resultLineIndicator;
        this.reader = new Reader();
        this.writer = new Writer();

        reader.start();
    }

    public void close() {
        if (!inputDeque.isEmpty())
            log.warn("Python-Java bridge was closed before all data was received from the external program:" + inputDeque.stream().collect(Collectors.joining(", ")));
        reader.interrupt();
        if (!outputDeque.isEmpty())
            log.warn("Python-Java bridge was closed before all data was sent to the external program: " + outputDeque.stream().collect(Collectors.joining(", ")));
    }

    public void send(String data) {
        outputDeque.add(data);
        writer.run();
    }

    public List<String> receive() throws InterruptedException {
        List<String> receivedData = new ArrayList<>();

        synchronized (reader) {
            if (inputDeque.isEmpty()) {
                log.trace("Waiting for something to be read");
                reader.wait();
            }
            inputDeque.drainTo(receivedData);
        }
        log.trace("Reading from internal buffer: " + receivedData);
        return receivedData;
    }

    public List<String> sendAndReceive(String data) throws InterruptedException {
        send(data);
        return receive();
    }

    /**
     * A thread reading all output from the Python program
     */
    private class Reader extends Thread {
        public void run() {
            setName("ReaderThread");
            log.debug("Starting reader thread");
            String line;
            try {
                while ((line = br.readLine()) != null) {
                    synchronized (this) {
                        if (resultLineIndicator == null || resultLineIndicator.test(line)) {
                            if (line.length() > 0)
                                inputDeque.add(line);
                            notify();
                        }
                    }
                    log.trace("Received: {}", line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class Writer {
        public void run() {
            try {
                while (!outputDeque.isEmpty()) {
                    String toWrite = outputDeque.pop();
                    log.trace("Writing: " + toWrite);
                    bw.write(toWrite);
                    bw.newLine();
                    // Important! When we don't flush, the data so sent will most like just reside in the buffer,
                    // at least the last part of it, and dont get sent to the external process. The external process
                    // will then probably block indefinitely, waiting for our request to finish.
                    bw.flush();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

class ErrorStreamConsumer extends Thread {
    private final static Logger log = LoggerFactory.getLogger(ErrorStreamConsumer.class);
    InputStream is;

    ErrorStreamConsumer(InputStream is) {
        this.is = is;
    }

    public void close() {
        interrupt();
    }

    public void run() {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = br.readLine()) != null)
                log.error(line);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}