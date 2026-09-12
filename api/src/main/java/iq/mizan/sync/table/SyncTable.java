package iq.mizan.sync.table;

import java.util.Map;

/**
 * A table clients may push to and pull from. Implemented once per record type, in the module
 * that owns the table; the sync engine handles the envelope columns itself.
 */
public interface SyncTable {

    String name();

    /** Field name on the wire (camelCase) to column type; column names derive from it. */
    Map<String, SyncColumnType> fields();
}
