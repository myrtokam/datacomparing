package org.example.demolocaluam;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@RestController
public class MainController {

    private final ExcelParser parser;
    private final DiffService diff;
    private final ExportStore exportStore;

    public MainController(ExcelParser parser, DiffService diff, ExportStore exportStore) {
        this.parser = parser;
        this.diff = diff;
        this.exportStore = exportStore;
    }

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String home() {
        String inner = """
            <div class="wrap">
              <div class="hero">
                <div class="hero-card">
                  <div class="brand">
                    <div class="logo">AR</div>
                    <div>
                      <div class="title">Access Review Diff (Local)</div>
                      <div class="subtitle">Compare δύο Excel exports και πάρε τα differences σε CSV.</div>
                    </div>
                  </div>

                  <form class="form" action="/compare" method="post" enctype="multipart/form-data">
                    <div class="grid">
                      <div class="field">
                        <label>Old file (Excel)</label>
                        <input type="file" name="oldFile" accept=".xlsx,.xls" required />
                      </div>
                      <div class="field">
                        <label>New file (Excel)</label>
                        <input type="file" name="newFile" accept=".xlsx,.xls" required />
                      </div>
                    </div>

                    <button class="btn" type="submit">Compare</button>

                    <div class="note">
                      Τοπική χρήση: Δεν αποθηκεύονται αρχεία σε cloud/DB. Γίνεται επεξεργασία στη μνήμη
                      και παράγονται CSV για download.
                    </div>
                  </form>
                </div>
              </div>
            </div>
            """;

        return pageShell("Access Review Diff (Local)", inner);
    }

    @PostMapping(
            value = "/compare",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_HTML_VALUE
    )
    public String compare(@RequestParam("oldFile") MultipartFile oldFile,
                          @RequestParam("newFile") MultipartFile newFile) {

        if (oldFile == null || oldFile.isEmpty() || newFile == null || newFile.isEmpty()) {
            return pageShell("Σφάλμα", errorBox("Παρακαλώ ανέβασε και τα δύο αρχεία (old & new)."));
        }

        try {
            var oldRecs = parser.parseAllSheets(oldFile);
            var newRecs = parser.parseAllSheets(newFile);

            // Δεν “δένω” compile-time στο σχήμα του DiffResult για να δουλεύει είτε είναι record είτε class.
            Object res = diff.compare(oldRecs, newRecs);

            // Pull lists safely (method or field)
            List<?> usersAdded = getList(res, "usersAdded");
            List<?> usersRemoved = getList(res, "usersRemoved");
            List<?> userFieldChanges = getList(res, "userFieldChanges");
            List<?> entAdded = getList(res, "entAdded");
            List<?> entRemoved = getList(res, "entRemoved");

            // Prepare CSV exports (χωρίς opencsv dependency)
            String usersAddedToken = exportStore.put(csvUsers(usersAdded), "users_added.csv");
            String usersRemovedToken = exportStore.put(csvUsers(usersRemoved), "users_removed.csv");
            String userChangesToken = exportStore.put(csvUserChanges(userFieldChanges), "users_changes.csv");
            String entAddedToken = exportStore.put(csvEnt(entAdded), "entitlements_added.csv");
            String entRemovedToken = exportStore.put(csvEnt(entRemoved), "entitlements_removed.csv");

            String inner = """
                <div class="wrap">
                  <div class="results">
                    <div class="card">
                      <div class="card-title">Results</div>
                      %s
                      <div class="downloads">
                        <a class="chip" href="/download/%s">Download users_added.csv</a>
                        <a class="chip" href="/download/%s">Download users_removed.csv</a>
                        <a class="chip" href="/download/%s">Download users_changes.csv</a>
                        <a class="chip" href="/download/%s">Download entitlements_added.csv</a>
                        <a class="chip" href="/download/%s">Download entitlements_removed.csv</a>
                      </div>

                      <div style="margin-top:14px;">
                        <a class="link" href="/">← Back</a>
                      </div>
                    </div>
                  </div>
                </div>
                """.formatted(
                    renderSummary(res),
                    escape(usersAddedToken),
                    escape(usersRemovedToken),
                    escape(userChangesToken),
                    escape(entAddedToken),
                    escape(entRemovedToken)
            );

            return pageShell("Results", inner);

        } catch (Exception e) {
            return pageShell("Σφάλμα", errorBox("Κάτι πήγε στραβά στην επεξεργασία: " + escape(String.valueOf(e.getMessage()))));
        }
    }

    @GetMapping("/download/{token}")
    public void download(@PathVariable String token, HttpServletResponse response) throws Exception {
        var stored = exportStore.get(token);
        if (stored == null) {
            response.setStatus(404);
            response.getWriter().write("Not found or expired");
            return;
        }

        // υποθέτουμε ότι ExportStore.get επιστρέφει object με filename() και bytes()
        String filename = safe(stringProp(stored, "filename"));
        byte[] bytes = bytesProp(stored, "bytes");

        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setContentType("text/csv; charset=utf-8");
        response.getOutputStream().write(bytes);
    }

    // -----------------------------
    // CSV helpers (no OpenCSV)
    // -----------------------------

    private byte[] csvUsers(List<?> rows) {
        return csvFromRows(
                new String[]{"UserID", "Name"},
                rows,
                new String[]{"userId", "name"}
        );
    }

    private byte[] csvUserChanges(List<?> rows) {
        return csvFromRows(
                new String[]{"UserID", "Name", "Field", "OldValue", "NewValue"},
                rows,
                new String[]{"userId", "name", "field", "oldValue", "newValue"}
        );
    }

    private byte[] csvEnt(List<?> rows) {
        return csvFromRows(
                new String[]{"UserID", "Name", "Application", "Role"},
                rows,
                new String[]{"userId", "name", "app", "role"}
        );
    }

    private byte[] csvFromRows(String[] header, List<?> rows, String[] props) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OutputStreamWriter out = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {

            out.write(csvLine(header));
            out.write("\n");

            for (Object r : rows) {
                String[] cols = new String[props.length];
                for (int i = 0; i < props.length; i++) {
                    cols[i] = safe(stringProp(r, props[i]));
                }
                out.write(csvLine(cols));
                out.write("\n");
            }
            out.flush();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String csvLine(String[] cols) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(csvCell(cols[i]));
        }
        return sb.toString();
    }

    private String csvCell(String s) {
        if (s == null) return "";
        boolean needsQuotes = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        String v = s.replace("\"", "\"\"");
        return needsQuotes ? "\"" + v + "\"" : v;
    }

    // -----------------------------
    // Rendering helpers
    // -----------------------------

    private String renderSummary(Object res) {
        int usersAdded = sizeOf(getList(res, "usersAdded"));
        int usersRemoved = sizeOf(getList(res, "usersRemoved"));
        int userChanges = sizeOf(getList(res, "userFieldChanges"));
        int entAdded = sizeOf(getList(res, "entAdded"));
        int entRemoved = sizeOf(getList(res, "entRemoved"));

        return """
            <div class="summary">
              <div class="kpi"><div class="k">Users Added</div><div class="v">%d</div></div>
              <div class="kpi"><div class="k">Users Removed</div><div class="v">%d</div></div>
              <div class="kpi"><div class="k">User Field Changes</div><div class="v">%d</div></div>
              <div class="kpi"><div class="k">Entitlements Added</div><div class="v">%d</div></div>
              <div class="kpi"><div class="k">Entitlements Removed</div><div class="v">%d</div></div>
            </div>
            """.formatted(usersAdded, usersRemoved, userChanges, entAdded, entRemoved);
    }

    private int sizeOf(List<?> list) {
        return list == null ? 0 : list.size();
    }

    private String errorBox(String msg) {
        return """
            <div class="wrap">
              <div class="results">
                <div class="card">
                  <div class="card-title">Σφάλμα</div>
                  <div class="error">%s</div>
                  <div style="margin-top:14px;">
                    <a class="link" href="/">← Back</a>
                  </div>
                </div>
              </div>
            </div>
            """.formatted(msg);
    }

    private String pageShell(String title, String innerHtml) {
        return """
            <!doctype html>
            <html lang="el">
            <head>
              <meta charset="utf-8"/>
              <meta name="viewport" content="width=device-width, initial-scale=1"/>
              <title>%s</title>
              <style>
                :root{
                  --bg:#0b1020;
                  --card:#101a33;
                  --card2:#0f1930;
                  --text:#e8eefc;
                  --muted:#a9b6d3;
                  --accent:#7aa2ff;
                  --accent2:#8ef0d5;
                  --danger:#ff6b6b;
                  --border: rgba(255,255,255,.10);
                }
                *{box-sizing:border-box}
                body{
                  margin:0;
                  font-family: ui-sans-serif, system-ui, -apple-system, Segoe UI, Roboto, Helvetica, Arial;
                  background:
                    radial-gradient(900px 500px at 15% 10%, rgba(122,162,255,.25), transparent 60%),
                    radial-gradient(700px 500px at 80% 20%, rgba(142,240,213,.18), transparent 55%),
                    var(--bg);
                  color:var(--text);
                }
                .wrap{max-width:980px;margin:0 auto;padding:28px 18px 60px}
                .hero{margin-top:18px}
                .hero-card{
                  background: linear-gradient(180deg, rgba(255,255,255,.06), rgba(255,255,255,.03));
                  border:1px solid var(--border);
                  border-radius:18px;
                  padding:18px;
                  box-shadow: 0 18px 60px rgba(0,0,0,.35);
                }
                .brand{display:flex;gap:12px;align-items:center;margin-bottom:14px}
                .logo{
                  width:42px;height:42px;border-radius:12px;
                  display:grid;place-items:center;
                  background: linear-gradient(135deg, rgba(122,162,255,.35), rgba(142,240,213,.25));
                  border:1px solid var(--border);
                  font-weight:800;
                }
                .title{font-size:18px;font-weight:750;letter-spacing:.2px}
                .subtitle{font-size:13px;color:var(--muted);margin-top:2px}
                .form{margin-top:10px}
                .grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}
                @media (max-width: 760px){ .grid{grid-template-columns:1fr} }
                .field{
                  background: rgba(0,0,0,.18);
                  border:1px solid var(--border);
                  border-radius:14px;
                  padding:12px;
                }
                label{display:block;font-size:12px;color:var(--muted);margin-bottom:8px}
                input[type="file"]{width:100%;color:var(--text)}
                .btn{
                  margin-top:12px;
                  border:0;
                  cursor:pointer;
                  font-weight:700;
                  color:#071025;
                  background: linear-gradient(135deg, var(--accent), var(--accent2));
                  padding:11px 14px;
                  border-radius:12px;
                }
                .note{
                  margin-top:12px;
                  font-size:12px;
                  color:var(--muted);
                  line-height:1.45;
                  border-top:1px dashed rgba(255,255,255,.18);
                  padding-top:12px;
                }
                .results{margin-top:18px}
                .card{
                  background: rgba(16,26,51,.70);
                  border:1px solid var(--border);
                  border-radius:18px;
                  padding:16px;
                }
                .card-title{font-weight:800;margin-bottom:10px}
                .summary{
                  display:grid;
                  grid-template-columns: repeat(5, minmax(0,1fr));
                  gap:10px;
                  margin-bottom:14px;
                }
                @media (max-width: 900px){ .summary{grid-template-columns: repeat(2, minmax(0,1fr));} }
                .kpi{
                  background: rgba(0,0,0,.18);
                  border:1px solid var(--border);
                  border-radius:14px;
                  padding:12px;
                }
                .k{font-size:12px;color:var(--muted)}
                .v{font-size:20px;font-weight:850;margin-top:4px}
                .downloads{display:flex;flex-wrap:wrap;gap:10px;margin-top:12px}
                .chip{
                  display:inline-block;
                  padding:9px 11px;
                  border-radius:999px;
                  border:1px solid var(--border);
                  background: rgba(0,0,0,.18);
                  color:var(--text);
                  text-decoration:none;
                  font-size:13px;
                }
                .chip:hover{border-color: rgba(122,162,255,.55)}
                .error{
                  color: #ffd6d6;
                  background: rgba(255,107,107,.10);
                  border: 1px solid rgba(255,107,107,.35);
                  padding:10px 12px;
                  border-radius:12px;
                }
                .link{color:var(--accent);text-decoration:none}
                .link:hover{text-decoration:underline}
              </style>
            </head>
            <body>
              %s
            </body>
            </html>
            """.formatted(escape(title), innerHtml);
    }

    // -----------------------------
    // Safe string helpers
    // -----------------------------

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    // -----------------------------
    // Reflection helpers: works for record accessors OR fields
    // -----------------------------

    @SuppressWarnings("unchecked")
    private List<?> getList(Object obj, String name) {
        if (obj == null) return List.of();
        Object v = prop(obj, name);
        if (v instanceof List<?> list) return list;
        return List.of();
    }

    private String stringProp(Object obj, String name) {
        Object v = prop(obj, name);
        return v == null ? "" : String.valueOf(v);
    }

    private byte[] bytesProp(Object obj, String name) {
        Object v = prop(obj, name);
        if (v instanceof byte[] b) return b;
        return new byte[0];
    }

    private Object prop(Object obj, String name) {
        try {
            // 1) record accessor / method with same name: userId()
            var m1 = obj.getClass().getMethod(name);
            return m1.invoke(obj);
        } catch (Exception ignored) { }

        try {
            // 2) getter: getUserId()
            String getter = "get" + name.substring(0,1).toUpperCase(Locale.ROOT) + name.substring(1);
            var m2 = obj.getClass().getMethod(getter);
            return m2.invoke(obj);
        } catch (Exception ignored) { }

        try {
            // 3) public field: userId
            var f = obj.getClass().getField(name);
            return f.get(obj);
        } catch (Exception ignored) { }

        return null;
    }
}
