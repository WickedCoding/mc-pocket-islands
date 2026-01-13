package com.wickedsik.personalworlds.player;

/**
 * Represents the result of a visit access control check.
 * Used to provide specific feedback when a player cannot visit another's island.
 */
public enum VisitDenialReason {
    /**
     * Visit is allowed - no denial.
     */
    ALLOWED,

    /**
     * Visitor does not have an invitation from the host.
     */
    NOT_INVITED,

    /**
     * Host player is offline.
     */
    HOST_OFFLINE,

    /**
     * Host player is online but not on their own island.
     * Only applies when config.allowVisitWhenHostNotHome is false.
     */
    HOST_NOT_HOME;

    /**
     * Check if this result allows the visit.
     *
     * @return true if visit is allowed
     */
    public boolean isAllowed() {
        return this == ALLOWED;
    }

    /**
     * Check if this result denies the visit.
     *
     * @return true if visit is denied
     */
    public boolean isDenied() {
        return this != ALLOWED;
    }
}
