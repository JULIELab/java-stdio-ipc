package de.julielab.ipc.javabridge;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * This is class is a simple container for options given to the constructor of {@link StdioBridge}. The meaning
 * of each option is explained at its setter method.
 */
public class Options<O> {

    private String executable;
    private Function<O, O> resultReshaper;
    private Predicate<O> resultLineIndicator;
    private String externalProgramTerminationSignal;
    private Class<O> resultType;
    private String multilineResponseDelimiter;
    private boolean gzipSentData;
    private boolean gzipReceivedData;
    private String externalProgramReadySignal;
    private String terminationSignalFromErrorStream;

    public Options(Class<O> resultType) {
        this.resultType = resultType;
    }

    public String getTerminationSignalFromErrorStream() {
        return terminationSignalFromErrorStream;
    }

    public void setTerminationSignalFromErrorStream(String terminationSignalFromErrorStream) {
        this.terminationSignalFromErrorStream = terminationSignalFromErrorStream;
    }

    public String getExternalProgramReadySignal() {
        return externalProgramReadySignal;
    }

    public void setExternalProgramReadySignal(String externalProgramReadySignal) {
        this.externalProgramReadySignal = externalProgramReadySignal;
    }

    public boolean isGzipSentData() {
        return gzipSentData;
    }

    /**
     * Whether or not the data sent to the external program should be compressed in GZIP format or left untouched.
     * This should be left at <tt>false</tt> because the compression introduces a large time overhead.
     *
     * @param gzipSentData If the sent data should be compressed.
     */
    public void setGzipSentData(boolean gzipSentData) {
        this.gzipSentData = gzipSentData;
    }

    public boolean isGzipReceivedData() {
        return gzipReceivedData;
    }

    /**
     * Whether or not the data received from the external program should be decompressed from GZIP format or left untouched.
     * This should be left at <tt>false</tt>, if possible, because the compression introduces a large time overhead.
     *
     * @param gzipReceivedData If the received data should be decompressed.
     */
    public void setGzipReceivedData(boolean gzipReceivedData) {
        this.gzipReceivedData = gzipReceivedData;
    }

    public Class<O> getResultType() {
        return resultType;
    }

    public Function<O, O> getResultReshaper() {
        return resultReshaper;
    }

    /**
     * The result reshaper is a method that takes the received line from the external process and transforms
     * it into the final format. This is used to remove markers on the input line that indicate the line to be
     * a result line in contrast to other program output. For example, lines could be prefixed with <code>Output:</code>
     * to indicate lines that are meant for the Java program to read.
     *
     * @param resultReshaper The result reshaper.
     */
    public void setResultReshaper(Function<O, O> resultReshaper) {
        this.resultReshaper = resultReshaper;
    }

    public String getExecutable() {
        return executable;
    }

    /**
     * The name of the external program to execute. For Python programs this would by <code>python</code>, for example,
     * or even another version specific path like <code>/usr/local/bin/python36</code>.
     *
     * @param executable The path to the executable for the external program.
     */
    public void setExecutable(String executable) {
        this.executable = executable;
    }

    public Predicate<O> getResultLineIndicator() {
        return resultLineIndicator;
    }

    /**
     * A {@link Predicate} that is used to find the actual output lines of the external program. This is useful
     * when the external process might output not only result lines meant to receive by the Java side but also
     * other messages. Then, the external program could indicate lines to actually be received with a prefix like
     * <code>Result:</code>, for example. The predicate would then need to recognize lines beginning with this
     * prefix to filter out undesired lines. No predicate has to be defined. However, without a predicate, the external
     * program is only allowed to output exactly one line per request. Otherwise, we might miss the actual data lines
     * we are interested in because we can never know when the external process has sent all data. With the predicate set,
     * we will wait for each {@link StdioBridge#receive()} call for the next line to occur that is accepted by the
     * predicate.
     *
     * @param resultLineIndicator A predicate to filter output lines, may be null.
     */
    public void setResultLineIndicator(Predicate<O> resultLineIndicator) {
        this.resultLineIndicator = resultLineIndicator;
    }

    public String getExternalProgramTerminationSignal() {
        return externalProgramTerminationSignal;
    }

    /**
     * To gracefully terminate the external process, some signal like <code>quit</code> or <code>exit</code>
     * could be accepted by the external program as signal to end the application. If such a signal is accepted,
     * provide it here. It is sent to the external process on {@link StdioBridge#stop()}.
     *
     * @param externalProgramTerminationSignal A string that signals the external process to end.
     */
    public void setExternalProgramTerminationSignal(String externalProgramTerminationSignal) {
        this.externalProgramTerminationSignal = externalProgramTerminationSignal;
    }

    public String getMultilineResponseDelimiter() {
        return multilineResponseDelimiter;
    }

    /**
     * If the external program may respond with multiple lines per {@link StdioBridge#send(String)} call,
     * there must be a signal to indicate that the response is complete. This signal is the <tt>multilineResponseDelimiter.</tt>
     * Setting this field does activate the possibility to receive multiple lines of response for a single request.
     *
     * @param multilineResponseDelimiter
     */
    public void setMultilineResponseDelimiter(String multilineResponseDelimiter) {
        this.multilineResponseDelimiter = multilineResponseDelimiter;
    }
}
