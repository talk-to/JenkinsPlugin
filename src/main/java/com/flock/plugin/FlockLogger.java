package com.flock.plugin;

import java.io.PrintStream;

public class FlockLogger {
    private PrintStream printStream;

    FlockLogger(PrintStream printStream) {
        this.printStream = printStream;
    }

    public void log(Object message) {
        printStream.println(FlockLoggerInformationProvider.FLOCK_LOGS_IDENTIFIER + message);
    }

}
