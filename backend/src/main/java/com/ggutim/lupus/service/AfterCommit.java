package com.ggutim.lupus.service;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Defers a side effect — a WebSocket broadcast, specifically — until the
 * current transaction commits.
 *
 * <p>Every mutating endpoint here broadcasts a "something changed" signal
 * before returning, and the client reacting to it (its own live
 * subscription, most often) immediately re-fetches state over a brand
 * new HTTP request/transaction. Sending that signal from inside the
 * still-open transaction lets that re-fetch race the commit under
 * READ_COMMITTED isolation: it can read the room as it was <em>before</em>
 * this change, then overwrite the correct state a moment later just
 * received from this same call's own response. Running the broadcast
 * here instead guarantees the change is already committed and visible
 * by the time anyone reacts to it.
 */
final class AfterCommit {

    private AfterCommit() {
    }

    static void run(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
