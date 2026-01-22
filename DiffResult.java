package org.example.democolauam;

import java.util.ArrayList;
import java.util.List;

public class DiffResult {

    public final List<UserChange> usersAdded = new ArrayList<>();
    public final List<UserChange> usersRemoved = new ArrayList<>();
    public final List<UserFieldChange> userFieldChanges = new ArrayList<>();

    public boolean entitlementComparisonAvailable = true;
    public final List<EntChange> entAdded = new ArrayList<>();
    public final List<EntChange> entRemoved = new ArrayList<>();

    public static class UserChange {
        public final String userId;
        public final String name;

        public UserChange(String userId, String name) {
            this.userId = userId;
            this.name = name;
        }
    }

    public static class UserFieldChange {
        public final String userId;
        public final String name;
        public final String field;
        public final String oldValue;
        public final String newValue;

        public UserFieldChange(String userId, String name, String field, String oldValue, String newValue) {
            this.userId = userId;
            this.name = name;
            this.field = field;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }
    }

    public static class EntChange {
        public final String userId;
        public final String name;
        public final String app;
        public final String role;

        public EntChange(String userId, String name, String app, String role) {
            this.userId = userId;
            this.name = name;
            this.app = app;
            this.role = role;
        }
    }
}
