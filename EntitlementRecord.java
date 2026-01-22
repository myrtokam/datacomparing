package org.example.democolauam;

public class EntitlementRecord {
    public final String userId;
    public final String name;
    public final String app;
    public final String role;

    public EntitlementRecord(String userId, String name, String app, String role) {
        this.userId = userId;
        this.name = name;
        this.app = app;
        this.role = role;
    }
}

