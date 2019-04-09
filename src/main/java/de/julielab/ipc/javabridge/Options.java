package de.julielab.ipc.javabridge;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * This is class is a simple container for options given to the constructor of {@link StdioBridge}. The meaning
 * of each option is explained at its setter method.
 */
public class Options<O> {

    private String executable;
    private Function<O, O> resultTransformator;
    private Predicate<O> resultLineIndicator;
    private String externalProgramTerminationSignal;
    private Class<O> resultType;
    private String multilineResponseDelimiter;
    public Options(Class<O> resultType) {
        this.resultType = resultType;
    }


    public Class<O> getResultType() {
        return resultType;
    }

    public Function<O, O> getResultTransformator() {
        return resultTransformator;
    }

    /**
     * The result transformator is a method that takes the received line from the external process and transforms
     * it into the final format. This is used to remove markers on the input line that indicate the line to be
     * a result line in contrast to other program output. For example, lines could be prefixed with <code>Output:</code>
     * to indicate lines that are meant for the Java program to read.
     *
     * @param resultTransformator The result transformer.
     */
    public void setResultTransformator(Function<O, O> resultTransformator) {
        this.resultTransformator = resultTransformator;
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
     * @param multilineResponseDelimiter
     */
    public void setMultilineResponseDelimiter(String multilineResponseDelimiter) {
        this.multilineResponseDelimiter = multilineResponseDelimiter;
    }
}
