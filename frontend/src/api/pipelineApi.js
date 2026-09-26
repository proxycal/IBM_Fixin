const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "";

/**
 * Calls POST /api/pipeline/run and returns the parsed PipelineResultDTO.
 *
 * @param {{ repoUrl: string, bugDescription: string, branch?: string }} request
 * @returns {Promise<object>}
 */
export async function runPipeline(request) {
  const response = await fetch(`${API_BASE}/api/pipeline/run`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    let errorBody;
    try {
      errorBody = await response.json();
    } catch {
      errorBody = { error: response.statusText };
    }
    const err = new Error(errorBody.error ?? "Pipeline failed");
    err.stage = errorBody.stage ?? "unknown";
    throw err;
  }

  return response.json();
}
