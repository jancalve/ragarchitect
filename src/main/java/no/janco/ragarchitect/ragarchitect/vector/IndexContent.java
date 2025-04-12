package no.janco.ragarchitect.ragarchitect.vector;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class IndexContent {
    private String areaName;
    private String itemPath;
    private String chunkId;
    private String content;

    // Default constructor for Jackson
    public IndexContent() {}

    @JsonCreator
    public IndexContent(
            @JsonProperty("areaName") String areaName,
            @JsonProperty("itemPath") String itemPath,
            @JsonProperty("chunkId") String chunkId,
            @JsonProperty("content") String content) {
        this.areaName = areaName;
        this.itemPath = itemPath;
        this.chunkId = chunkId;
        this.content = content;
    }

    public String getAreaName() { return areaName; }
    public String getFilePath() { return itemPath; }
    public String getChunkId() { return chunkId; }
    public String getContent() { return content; }

    public void setAreaName(String projectName) { this.areaName = projectName; }
    public void setFilePath(String itemPath) { this.itemPath = itemPath; }
    public void setChunkId(String chunkId) { this.chunkId = chunkId; }
    public void setContent(String content) { this.content = content; }
}
