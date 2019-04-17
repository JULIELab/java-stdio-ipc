package de.julielab.ipc.javabridge;

public class ExternalProgramTerminationException extends RuntimeException {
    public ExternalProgramTerminationException() {
    }

    public ExternalProgramTerminationException(String message) {
        super(message);
    }

    public ExternalProgramTerminationException(String message, Throwable cause) {
        super(message, cause);
    }

    public ExternalProgramTerminationException(Throwable cause) {
        super(cause);
    }

    public ExternalProgramTerminationException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
