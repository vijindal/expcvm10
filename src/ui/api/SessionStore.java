package ui.api;

import session.CalculationSession;
import session.calctype.ModelSelection;
import ui.layer.ModelBrowseService;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of live {@link CalculationSession} instances, one per
 * API session ID. Per {@code docs/plan-rest-api-calculation-session.md}:
 * one {@code CalculationSession} per API session (never a shared
 * singleton), and concurrent requests to the SAME session ID synchronize
 * on that session's own lock rather than being rejected or corrupting state.
 *
 * <p>Session expiry/cleanup is deliberately deferred (see the plan doc) --
 * this first implementation only frees a session on explicit {@link #remove}.
 */
public final class SessionStore {

    /** Pairs a session with the lock requests against it must hold. */
    public static final class Entry {
        public final CalculationSession session = new CalculationSession();
        public final ModelBrowseService browse = new ModelBrowseService(session);
        public final Object lock = new Object();

        /**
         * The {@link ModelSelection} last passed to {@code PUT .../model},
         * so a later {@code POST .../calculations/{kind}} can call {@link
         * session.calctype.CalculationCatalog#runCalculating} without the
         * caller resending tdb/elements/phases on every calculation
         * request. {@code null} until the model endpoint has been called
         * at least once.
         */
        public volatile ModelSelection modelSelection;
    }

    private final Map<String, Entry> sessions = new ConcurrentHashMap<>();

    /** Creates a new, empty session and returns its id. */
    public String create() {
        String id = UUID.randomUUID().toString();
        sessions.put(id, new Entry());
        return id;
    }

    /** Returns the entry for {@code id}, or {@code null} if it doesn't exist. */
    public Entry get(String id) {
        return sessions.get(id);
    }

    /** Removes and discards the session, if it exists. */
    public void remove(String id) {
        sessions.remove(id);
    }
}
