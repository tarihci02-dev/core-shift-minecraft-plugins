package com.coreshift.maden;

public final class SelectionException
extends Exception {
    private final Reason reason;

    public SelectionException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public SelectionException(Reason reason, Throwable throwable) {
        super(reason.name(), throwable);
        this.reason = reason;
    }

    public Reason reason() {
        return this.reason;
    }

    public static enum Reason {
        UNAVAILABLE,
        INCOMPLETE,
        NOT_CUBOID;

    }
}
