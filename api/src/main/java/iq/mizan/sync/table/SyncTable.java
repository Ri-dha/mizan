package iq.mizan.sync.table;

import java.util.Map;

/**
 * A table clients may push to and pull from. Implemented once per record type, in the module
 * that owns the table; the sync engine handles the envelope columns itself.
 */
public interface SyncTable {

    String name();

    /** Field name on the wire (camelCase) to its type; encrypted records only use the names (ADR e2e-1). */
    Map<String, SyncColumnType> fields();

    /** Written once and never changed afterwards, such as net worth snapshots (BR-14). */
    default boolean appendOnly() {
        return false;
    }
}
