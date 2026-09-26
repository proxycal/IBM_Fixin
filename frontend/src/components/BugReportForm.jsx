import { useState } from "react";

/**
 * Form that captures the user's repo URL, bug description, and optional branch,
 * then calls the onSubmit callback with the gathered values.
 *
 * Props:
 *   onSubmit(values)  — called with { repoUrl, bugDescription, branch }
 *   loading           — boolean; when true the submit button shows a spinner
 */
export default function BugReportForm({ onSubmit, loading }) {
  const [repoUrl, setRepoUrl]             = useState("");
  const [bugDescription, setBugDescription] = useState("");
  const [branch, setBranch]               = useState("main");

  function handleSubmit(e) {
    e.preventDefault();
    if (!repoUrl.trim() || !bugDescription.trim()) return;
    onSubmit({ repoUrl: repoUrl.trim(), bugDescription: bugDescription.trim(), branch: branch.trim() || "main" });
  }

  return (
    <form onSubmit={handleSubmit} style={styles.form}>
      <h2 style={styles.heading}>Debug-to-Fix Agent</h2>

      <label style={styles.label}>
        Repository URL
        <input
          style={styles.input}
          type="url"
          placeholder="https://github.com/org/repo.git"
          value={repoUrl}
          onChange={(e) => setRepoUrl(e.target.value)}
          required
        />
      </label>

      <label style={styles.label}>
        Bug Description
        <textarea
          style={{ ...styles.input, minHeight: 100, resize: "vertical" }}
          placeholder="Describe the bug in detail…"
          value={bugDescription}
          onChange={(e) => setBugDescription(e.target.value)}
          required
        />
      </label>

      <label style={styles.label}>
        Branch
        <input
          style={styles.input}
          type="text"
          placeholder="main"
          value={branch}
          onChange={(e) => setBranch(e.target.value)}
        />
      </label>

      <button type="submit" style={styles.button} disabled={loading}>
        {loading ? "Running pipeline…" : "Run Pipeline"}
      </button>
    </form>
  );
}

const styles = {
  form: {
    display: "flex",
    flexDirection: "column",
    gap: 12,
    maxWidth: 600,
    margin: "0 auto",
    padding: 24,
    background: "#f7f8fa",
    borderRadius: 8,
    border: "1px solid #e5e7eb",
  },
  heading: { margin: 0, fontSize: 20, color: "#1f2328" },
  label: {
    display: "flex",
    flexDirection: "column",
    gap: 4,
    fontSize: 14,
    color: "#1f2328",
    fontWeight: 500,
  },
  input: {
    padding: "8px 10px",
    fontSize: 14,
    border: "1px solid #d0d7de",
    borderRadius: 6,
    outline: "none",
    fontFamily: "inherit",
  },
  button: {
    padding: "10px 20px",
    fontSize: 14,
    background: "#3b82d4",
    color: "#fff",
    border: "none",
    borderRadius: 6,
    cursor: "pointer",
    alignSelf: "flex-start",
  },
};
