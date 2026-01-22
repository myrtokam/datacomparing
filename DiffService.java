package org.example.democolauam;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DiffService {

    public DiffResult compare(List<EntitlementRecord> oldRecs, List<EntitlementRecord> newRecs) {
        DiffResult res = new DiffResult();

        // Map userId -> name (take first non-blank)
        Map<String, String> oldUsers = buildUserMap(oldRecs);
        Map<String, String> newUsers = buildUserMap(newRecs);

        // Users added/removed
        for (String userId : newUsers.keySet()) {
            if (!oldUsers.containsKey(userId)) {
                res.usersAdded.add(new DiffResult.UserChange(userId, newUsers.get(userId)));
            }
        }
        for (String userId : oldUsers.keySet()) {
            if (!newUsers.containsKey(userId)) {
                res.usersRemoved.add(new DiffResult.UserChange(userId, oldUsers.get(userId)));
            }
        }

        // User field changes (Name change)
        for (String userId : newUsers.keySet()) {
            if (oldUsers.containsKey(userId)) {
                String oldName = safe(oldUsers.get(userId));
                String newName = safe(newUsers.get(userId));
                if (!oldName.equals(newName) && !(oldName.isBlank() && newName.isBlank())) {
                    res.userFieldChanges.add(new DiffResult.UserFieldChange(
                            userId,
                            newName.isBlank() ? oldName : newName,
                            "Name",
                            oldName,
                            newName
                    ));
                }
            }
        }

        // Entitlements compare
        Map<String, EntitlementRecord> oldEnt = buildEntMap(oldRecs);
        Map<String, EntitlementRecord> newEnt = buildEntMap(newRecs);

        for (String k : newEnt.keySet()) {
            if (!oldEnt.containsKey(k)) {
                EntitlementRecord r = newEnt.get(k);
                res.entAdded.add(new DiffResult.EntChange(r.userId, r.name, r.app, r.role));
            }
        }
        for (String k : oldEnt.keySet()) {
            if (!newEnt.containsKey(k)) {
                EntitlementRecord r = oldEnt.get(k);
                res.entRemoved.add(new DiffResult.EntChange(r.userId, r.name, r.app, r.role));
            }
        }

        // Sorting (stable UI)
        res.usersAdded.sort(Comparator.comparing(a -> safe(a.userId)));
        res.usersRemoved.sort(Comparator.comparing(a -> safe(a.userId)));
        res.userFieldChanges.sort(Comparator.comparing(a -> safe(a.userId)));
        res.entAdded.sort(Comparator.comparing(a -> safe(a.userId) + "|" + safe(a.app) + "|" + safe(a.role)));
        res.entRemoved.sort(Comparator.comparing(a -> safe(a.userId) + "|" + safe(a.app) + "|" + safe(a.role)));

        return res;
    }

    private Map<String, String> buildUserMap(List<EntitlementRecord> recs) {
        Map<String, String> m = new HashMap<>();
        for (EntitlementRecord r : recs) {
            String id = safe(r.userId);
            if (id.isBlank()) continue;
            String name = safe(r.name);
            // keep first non-blank
            if (!m.containsKey(id) || (m.get(id).isBlank() && !name.isBlank())) {
                m.put(id, name);
            }
        }
        return m;
    }

    private Map<String, EntitlementRecord> buildEntMap(List<EntitlementRecord> recs) {
        Map<String, EntitlementRecord> m = new HashMap<>();
        for (EntitlementRecord r : recs) {
            String key = entKey(r);
            if (key.isBlank()) continue;
            // keep last, doesn't matter
            m.put(key, r);
        }
        return m;
    }

    private String entKey(EntitlementRecord r) {
        // unique: userId + app + role
        String id = safe(r.userId).trim();
        String app = safe(r.app).trim();
        String role = safe(r.role).trim();
        if (id.isBlank() || app.isBlank() || role.isBlank()) return "";
        return id.toLowerCase(Locale.ROOT) + "|" + app.toLowerCase(Locale.ROOT) + "|" + role.toLowerCase(Locale.ROOT);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
