/**
 * Displays the full PipelineResultDTO returned by the backend.
 *
 * Props:
 *   result — the deserialized PipelineResultDTO object
 */
export default function ResultPanel({ result }) {
  if (!result) return null;

  const { rootCause, fixDescription, patchedFiles, generatedTest, testVerification } = result;

  return (
    <div style={styles.panel}>
      <h2 style={styles.sectionTitle}>Pipeline Results</h2>

      {/* Root Cause */}
      <Section title="Root Cause">
        <p style={styles.prose}>{rootCause}</p>
      </Section>

      {/* Fix Description */}
      {fixDescription && fixDescription !== rootCause && (
        <Section title="Fix Description">
          <p style={styles.prose}>{fixDescription}</p>
        </Section>
      )}

      {/* Patched Files */}
      {patchedFiles?.length > 0 && (
        <Section title={`Patched Files (${patchedFiles.length})`}>
          {patchedFiles.map((f) => (
            <div key={f.relativePath} style={styles.fileBlock}>
              <div style={styles.filePath}>{f.relativePath}</div>
              <pre style={styles.code}>{f.content}</pre>
            </div>
          ))}
        </Section>
      )}

      {/* Generated Test */}
      {generatedTest && (
        <Section title="Generated Regression Test">
          <div style={styles.fileBlock}>
            <div style={styles.filePath}>{generatedTest.relativePath}</div>
            <pre style={styles.code}>{generatedTest.content}</pre>
          </div>
        </Section>
      )}

      {/* Test Verification */}
      {testVerification && (
        <Section title="Test Verification">
          <span style={{
            ...styles.badge,
            background: testVerification.passed ? "#d1fae5" : "#fee2e2",
            color:      testVerification.passed ? "#065f46" : "#991b1b",
          }}>
            {testVerification.passed ? "✓ PASSED" : "✗ FAILED"}
          </span>
          <pre style={{ ...styles.code, marginTop: 12 }}>{testVerification.output}</pre>
        </Section>
      )}
    </div>
  );
}

function Section({ title, children }) {
  return (
    <div style={styles.section}>
      <h3 style={styles.sectionHeading}>{title}</h3>
      {children}
    </div>
  );
}

const styles = {
  panel: {
    maxWidth: 760,
    margin: "24px auto 0",
    padding: 24,
    background: "#fff",
    border: "1px solid #e5e7eb",
    borderRadius: 8,
  },
  sectionTitle: { margin: "0 0 16px", fontSize: 18, color: "#1f2328" },
  section: { marginBottom: 24 },
  sectionHeading: {
    margin: "0 0 8px",
    fontSize: 14,
    fontWeight: 600,
    color: "#57606a",
    textTransform: "uppercase",
    letterSpacing: "0.05em",
  },
  prose: { margin: 0, lineHeight: 1.6, color: "#1f2328" },
  fileBlock: {
    marginBottom: 16,
    border: "1px solid #e5e7eb",
    borderRadius: 6,
    overflow: "hidden",
  },
  filePath: {
    padding: "6px 10px",
    background: "#f7f8fa",
    fontSize: 12,
    fontFamily: "monospace",
    borderBottom: "1px solid #e5e7eb",
    color: "#57606a",
  },
  code: {
    margin: 0,
    padding: "10px",
    fontSize: 12,
    fontFamily: "monospace",
    background: "#fff",
    overflowX: "auto",
    whiteSpace: "pre-wrap",
    wordBreak: "break-all",
    color: "#1f2328",
  },
  badge: {
    display: "inline-block",
    padding: "4px 10px",
    borderRadius: 9999,
    fontSize: 13,
    fontWeight: 600,
  },
};
