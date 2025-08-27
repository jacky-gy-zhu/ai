package com.enable.ai.web.dto.response;

import com.enable.ai.rag.vo.RagChunk;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 检索文本块的响应DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetrieveChunksResponse {

    /**
     * 检索到的文本块列表
     */
    private List<RagChunk> chunks;

    /**
     * 实际返回的文本块数量
     */
    private int count;

    /**
     * 构造函数，自动计算数量
     */
    public RetrieveChunksResponse(List<RagChunk> chunks) {
        this.chunks = chunks;
        this.count = chunks != null ? chunks.size() : 0;
    }
}
