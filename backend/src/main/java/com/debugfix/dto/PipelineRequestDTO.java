package com.debugfix.dto;

/**
 * Inbound request DTO for {@code POST /api/pipeline/run}.
 */
public class PipelineRequestDTO {

    private String repoUrl;
    private String bugDescription;
    private String branch = "main";

    public String getRepoUrl()           { return repoUrl; }
    public void   setRepoUrl(String v)   { this.repoUrl = v; }

    public String getBugDescription()          { return bugDescription; }
    public void   setBugDescription(String v)  { this.bugDescription = v; }

    public String getBranch()            { return branch; }
    public void   setBranch(String v)    { this.branch = v; }
}
