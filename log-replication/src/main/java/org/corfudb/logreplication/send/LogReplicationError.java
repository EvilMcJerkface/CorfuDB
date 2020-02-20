package org.corfudb.logreplication.send;

/**
 * Log Replication Error
 */
public enum LogReplicationError {
    TRIM_SNAPSHOT_SYNC(0, "A trim exception has occurred during snapshot sync."),
    TRIM_LOG_ENTRY_SYNC(1, "A trim exception has occurred during log entry sync."),
    SENDER_ERROR(2, "Listener error while processing message."),
    UNKNOWN (3, "Unknown exception caused sync cancel.");

    private final int code;
    private final String description;

    /**
     * Constructor
     *
     * @param code error code
     * @param description error description
     */
    LogReplicationError(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public int getCode() {
        return code;
    }

    @Override
    public String toString() {
        return code + ": " + description;
    }
}