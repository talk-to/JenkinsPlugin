package com.flock.plugin;

import java.io.PrintStream;

public class FlockLogger {
    private static String FLOCK_LOGS_IDENTIFIER = "* FLOCK LOGS *  : ";
    private PrintStream printStream;

    FlockLogger(PrintStream printStream) {
        this.printStream = printStream;
    }

    public void log(Object message) {
        printStream.println(FlockLogger.FLOCK_LOGS_IDENTIFIER + message);
    }

}
