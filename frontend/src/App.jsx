import { useState } from "react";
import BugReportForm from "./components/BugReportForm";
import ResultPanel   from "./components/ResultPanel";
import { runPipeline } from "./api/pipelineApi";

export default function App() {
  const [result,  setResult]  = useState(null);
  const [error,   setError]   = useState(null);
  const [loading, setLoading] = useState(false);

  async function handleSubmit(values) {
    setLoading(true);
    setResult(null);
    setError(null);
    try {
      const data = await runPipeline(values);
      setResult(data);
    } catch (err) {
      setError(err.message ?? "An unexpected error occurred.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div style={styles.container}>
      <BugReportForm onSubmit={handleSubmit} loading={loading} />

      {error && (
        <div style={styles.errorBanner}>
          <strong>Error:</strong> {error}
        </div>
      )}

      <ResultPanel result={result} />
    </div>
  );
}

const styles = {
  container: {
    fontFamily: '-apple-system, "Segoe UI", system-ui, sans-serif',
    fontSize: 15,
    lineHeight: 1.6,
    color: "#1f2328",
    background: "#ffffff",
    minHeight: "100vh",
    padding: "32px 16px",
  },
  errorBanner: {
    maxWidth: 600,
    margin: "16px auto 0",
    padding: "12px 16px",
    background: "#fee2e2",
    border: "1px solid #fca5a5",
    borderRadius: 6,
    color: "#991b1b",
    fontSize: 14,
  },
};
