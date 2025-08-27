package com.enable.ai.web.controller;

import com.enable.ai.rag.RagService;
import com.enable.ai.rag.vo.RagChunk;
import com.enable.ai.web.dto.request.AddChunkRequest;
import com.enable.ai.web.dto.request.AddContextRequest;
import com.enable.ai.web.dto.request.RetrieveRequest;
import com.enable.ai.web.dto.response.ApiResponse;
import com.enable.ai.web.dto.response.RetrieveChunksResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * RAG (Retrieval-Augmented Generation) RESTful API Controller
 * 提供知识库操作的HTTP接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;

    /**
     * 添加单个文本块到指定集合
     * POST /api/v1/rag/collections/{collectionName}/chunks
     */
    @PostMapping("/collections/{collectionName}/chunks")
    public ResponseEntity<ApiResponse<Void>> addChunk(
            @PathVariable("collectionName") @NotBlank String collectionName,
            @RequestBody @Valid AddChunkRequest request) {

        try {
            log.info("Adding chunk to collection: {}, text length: {}",
                    collectionName, request.getText().length());

            RagChunk chunk = new RagChunk(request.getText());
            ragService.addChunkToCollection(collectionName, chunk);

            return ResponseEntity.ok(ApiResponse.success("Chunk added successfully"));
        } catch (Exception e) {
            log.error("Error adding chunk to collection {}: {}", collectionName, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to add chunk: " + e.getMessage()));
        }
    }

    /**
     * 批量添加上下文文本到指定集合（自动分块）
     * POST /api/v1/rag/collections/{collectionName}/context
     */
    @PostMapping("/collections/{collectionName}/context")
    public ResponseEntity<ApiResponse<Void>> addContext(
            @PathVariable("collectionName") @NotBlank String collectionName,
            @RequestBody @Valid AddContextRequest request) {

        try {
            log.info("Adding context to collection: {}, text length: {}",
                    collectionName, request.getContext().length());

            ragService.addContextToCollection(collectionName, request.getContext());

            return ResponseEntity.ok(ApiResponse.success("Context added successfully"));
        } catch (Exception e) {
            log.error("Error adding context to collection {}: {}", collectionName, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to add context: " + e.getMessage()));
        }
    }

    /**
     * 检索与查询最相关的top k个文本块
     * POST /api/v1/rag/collections/{collectionName}/retrieve/chunks
     */
    @PostMapping("/collections/{collectionName}/retrieve/chunks")
    public ResponseEntity<ApiResponse<RetrieveChunksResponse>> retrieveTopKChunks(
            @PathVariable("collectionName") @NotBlank String collectionName,
            @RequestBody @Valid RetrieveRequest request) {

        try {
            log.info("Retrieving top {} chunks from collection: {} for query: {}",
                    request.getK(), collectionName, request.getQuery());

            List<RagChunk> chunks = ragService.retrieveTopKChunks(
                    collectionName, request.getQuery(), request.getK());

            RetrieveChunksResponse response = new RetrieveChunksResponse(chunks);
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            log.error("Error retrieving chunks from collection {}: {}", collectionName, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to retrieve chunks: " + e.getMessage()));
        }
    }

    /**
     * 检索与查询最相关的文本内容（拼接格式）
     * POST /api/v1/rag/collections/{collectionName}/retrieve/context
     */
    @PostMapping("/collections/{collectionName}/retrieve/context")
    public ResponseEntity<ApiResponse<String>> retrieveContext(
            @PathVariable("collectionName") @NotBlank String collectionName,
            @RequestBody @Valid RetrieveRequest request) {

        try {
            log.info("Retrieving context from collection: {} for query: {}",
                    collectionName, request.getQuery());

            String context = ragService.retrieveContext(
                    collectionName, request.getQuery(), request.getK());

            return ResponseEntity.ok(ApiResponse.success(context));
        } catch (Exception e) {
            log.error("Error retrieving context from collection {}: {}", collectionName, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to retrieve context: " + e.getMessage()));
        }
    }

    /**
     * 删除指定集合及其所有内容
     * DELETE /api/v1/rag/collections/{collectionName}
     */
    @DeleteMapping("/collections/{collectionName}")
    public ResponseEntity<ApiResponse<Void>> deleteCollection(
            @PathVariable("collectionName") @NotBlank String collectionName) {

        try {
            log.info("Deleting collection: {}", collectionName);

            ragService.deleteCollection(collectionName);

            return ResponseEntity.ok(ApiResponse.success("Collection deleted successfully"));
        } catch (Exception e) {
            log.error("Error deleting collection {}: {}", collectionName, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Failed to delete collection: " + e.getMessage()));
        }
    }

    /**
     * 健康检查接口
     * GET /api/v1/rag/health
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> healthCheck() {
        return ResponseEntity.ok(ApiResponse.success("RAG service is running"));
    }
}