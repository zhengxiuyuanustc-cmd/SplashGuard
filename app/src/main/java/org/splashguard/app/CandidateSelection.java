package org.splashguard.app;

/**
 * Scan-scoped identity guard, not a way to authenticate a genuine close button.
 * Offer every matching node before using unique(). Identity equality must mean
 * the same actual node; matching text, resource IDs or bounds are not identities.
 * A second distinct node permanently makes this selection ambiguous.
 */
public final class CandidateSelection<T> {
    private T first;
    private boolean ambiguous;

    /** Null is rejected without changing the current selection. */
    public void offer(T identity) {
        if (identity == null) throw new IllegalArgumentException("Candidate identity must not be null");
        if (ambiguous) return;
        if (first == null) first = identity;
        else if (!first.equals(identity)) ambiguous = true;
    }

    /** Returns the original unique handle, or null for an empty or ambiguous set. */
    public T unique() { return ambiguous ? null : first; }

    public boolean ambiguous() { return ambiguous; }
}
