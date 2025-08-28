package com.enable.ai.rag;

import com.enable.ai.rag.vo.PromptWithScore;
import com.enable.ai.rag.vo.SortType;
import com.enable.ai.service.EmbeddingService;
import com.enable.ai.service.PromptRagService;
import com.enable.ai.util.Constants;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.QueryResults;
import io.milvus.grpc.SearchResults;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.*;
import io.milvus.param.dml.*;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.response.SearchResultsWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MilvusPromptRagService implements PromptRagService {

    private final MilvusServiceClient milvusClient;
    private final EmbeddingService embeddingService;

    private static final String ID_FIELD = "id";
    private static final String USER_ID_FIELD = "user_id";
    private static final String PROMPT_TEXT_FIELD = "prompt_text";
    private static final String VECTOR_FIELD = "embedding";
    private static final String TIMESTAMP_FIELD = "timestamp";
    private static final String VECTOR_INDEX_NAME = "vector_index";
    private static final String USER_ID_INDEX_NAME = "user_id_index";
    private static final String TIMESTAMP_INDEX_NAME = "timestamp_index";

    @Override
    public void addUserPromptToCollection(String collectionName, long userId, String prompt) {
        try {
            // Ensure collection exists
            ensureCollectionExists(collectionName);

            // Generate embedding for the prompt
            List<Float> embedding = embeddingService.generateEmbedding(prompt);

            // Prepare data for insertion
            List<Long> userIds = List.of(userId);
            List<String> prompts = List.of(prompt);
            List<List<Float>> embeddings = List.of(embedding);
            List<Long> timestamps = List.of(System.currentTimeMillis());

            List<InsertParam.Field> fields = Arrays.asList(
                    new InsertParam.Field(USER_ID_FIELD, userIds),
                    new InsertParam.Field(PROMPT_TEXT_FIELD, prompts),
                    new InsertParam.Field(VECTOR_FIELD, embeddings),
                    new InsertParam.Field(TIMESTAMP_FIELD, timestamps)
            );

            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFields(fields)
                    .build();

            R<MutationResult> response = milvusClient.insert(insertParam);
            handleResponse(response, "Insert prompt for user " + userId);

            // Flush to ensure data persistence
            FlushParam flushParam = FlushParam.newBuilder()
                    .addCollectionName(collectionName)
                    .build();
            milvusClient.flush(flushParam);

            log.info("Successfully added prompt for user {} to collection: {}", userId, collectionName);
        } catch (Exception e) {
            log.error("Error adding prompt for user {} to collection {}: {}",
                    userId, collectionName, e.getMessage(), e);
            throw new RuntimeException("Failed to add user prompt to collection", e);
        }
    }

    @Override
    public List<String> findRelatedUserPrompts(String collectionName, long userId, String query, int k) {
        return findRelatedUserPrompts(collectionName, userId, query, k, SortType.SIMILARITY);
    }

    /**
     * 查找相关的用户prompts，支持选择排序方式
     */
    public List<String> findRelatedUserPrompts(String collectionName, long userId,
                                               String query, int k, SortType sortType) {
        try {
            // Check if collection exists
            if (!collectionExists(collectionName)) {
                log.warn("Collection {} does not exist", collectionName);
                return new ArrayList<>();
            }

            // Load collection if not loaded
            loadCollection(collectionName);

            // Generate embedding for the query
            List<Float> queryEmbedding = embeddingService.generateEmbedding(query);

            // Build expression for filtering by user_id
            String expr = String.format("user_id == %d", userId);

            // Prepare search parameters
            List<String> searchOutputFields = Arrays.asList(PROMPT_TEXT_FIELD, TIMESTAMP_FIELD);
            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withMetricType(io.milvus.param.MetricType.L2)
                    .withOutFields(searchOutputFields)
                    .withTopK(k)
                    .withFloatVectors(List.of(queryEmbedding))
                    .withVectorFieldName(VECTOR_FIELD)
                    .withExpr(expr)
                    .withParams("{\"nprobe\":10}")
                    .build();

            R<SearchResults> response = milvusClient.search(searchParam);
            handleResponse(response, "Search related prompts for user " + userId);

            SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());
            List<PromptWithScore> promptsWithScore = new ArrayList<>();

            // Extract prompt texts, timestamps and scores from search results
            if (wrapper.getRowRecords() != null && !wrapper.getRowRecords().isEmpty()) {
                List<SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);
                List<QueryResultsWrapper.RowRecord> records = wrapper.getRowRecords();

                for (int i = 0; i < Math.min(records.size(), k); i++) {
                    QueryResultsWrapper.RowRecord record = records.get(i);
                    String promptText = (String) record.get(PROMPT_TEXT_FIELD);
                    Long timestamp = (Long) record.get(TIMESTAMP_FIELD);

                    if (promptText != null && timestamp != null) {
                        float score = i < scores.size() ? scores.get(i).getScore() : 0f;
                        promptsWithScore.add(new PromptWithScore(promptText, timestamp, score));
                    }
                }
            }

            // 根据选择的排序方式排序
            List<String> relatedPrompts;
            if (sortType == SortType.TIMESTAMP) {
                // 按时间戳排序（升序，最早的在前）
                relatedPrompts = promptsWithScore.stream()
                        .sorted(Comparator.comparing(PromptWithScore::getTimestamp))
                        .map(PromptWithScore::getPromptText)
                        .collect(Collectors.toList());
            } else {
                // 按相似度排序（已经是按相似度排序的，直接提取）
                relatedPrompts = promptsWithScore.stream()
                        .map(PromptWithScore::getPromptText)
                        .collect(Collectors.toList());
            }

            log.info("Found {} related prompts for user {} in collection: {} (sorted by {})",
                    relatedPrompts.size(), userId, collectionName, sortType);
            return relatedPrompts;
        } catch (Exception e) {
            log.error("Error finding related prompts for user {} in collection {}: {}",
                    userId, collectionName, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<String> findAllUserPrompts(String collectionName, long userId) {
        try {
            // Check if collection exists
            if (!collectionExists(collectionName)) {
                log.warn("Collection {} does not exist", collectionName);
                return new ArrayList<>();
            }

            // Load collection if not loaded
            loadCollection(collectionName);

            // Build expression for filtering by user_id
            String expr = String.format("user_id == %d", userId);

            // Query all prompts for the user
            QueryParam queryParam = QueryParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withExpr(expr)
                    .withOutFields(Arrays.asList(PROMPT_TEXT_FIELD, TIMESTAMP_FIELD))
                    .withLimit(Constants.LIMIT_CHUNK_TOKEN_SIZE)  // Set limit within Milvus constraint (max 16384)
                    .build();

            R<QueryResults> response = milvusClient.query(queryParam);
            handleResponse(response, "Query all prompts for user " + userId);

            QueryResultsWrapper wrapper = new QueryResultsWrapper(response.getData());
            List<String> prompts = new ArrayList<>();

            // Extract and sort prompts by timestamp
            if (wrapper.getRowRecords() != null) {
                List<QueryResultsWrapper.RowRecord> records = wrapper.getRowRecords();

                // Create a list of prompt-timestamp pairs for sorting
                List<PromptWithTimestamp> promptsWithTime = new ArrayList<>();
                for (QueryResultsWrapper.RowRecord record : records) {
                    String promptText = (String) record.get(PROMPT_TEXT_FIELD);
                    Long timestamp = (Long) record.get(TIMESTAMP_FIELD);
                    if (promptText != null && timestamp != null) {
                        promptsWithTime.add(new PromptWithTimestamp(promptText, timestamp));
                    }
                }

                // Sort by timestamp and extract prompt texts
                prompts = promptsWithTime.stream()
                        .sorted(Comparator.comparing(PromptWithTimestamp::getTimestamp))
                        .map(PromptWithTimestamp::getPromptText)
                        .collect(Collectors.toList());
            }

            log.info("Found {} prompts for user {} in collection: {}",
                    prompts.size(), userId, collectionName);
            return prompts;
        } catch (Exception e) {
            log.error("Error finding all prompts for user {} in collection {}: {}",
                    userId, collectionName, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    @Override
    public void deleteUserPromptsCollection(String collectionName, long userId) {
        try {
            // Check if collection exists
            if (!collectionExists(collectionName)) {
                log.warn("Collection {} does not exist, nothing to delete", collectionName);
                return;
            }

            // Load collection if not loaded
            loadCollection(collectionName);

            // Use batch deletion to avoid hitting Milvus query limits
            deleteUserPromptsBatch(collectionName, userId);

            log.info("Successfully deleted all prompts for user {} from collection: {}",
                    userId, collectionName);
        } catch (Exception e) {
            log.error("Error deleting prompts for user {} from collection {}: {}",
                    userId, collectionName, e.getMessage(), e);
            throw new RuntimeException("Failed to delete user prompts from collection", e);
        }
    }

    /**
     * Delete user prompts in batches to avoid hitting Milvus query limits
     */
    private void deleteUserPromptsBatch(String collectionName, long userId) {
        int batchSize = 1000; // Small batch size to avoid limits
        int totalDeleted = 0;
        
        while (true) {
            try {
                // Query a batch of IDs to delete (using small limit)
                String expr = String.format("user_id == %d", userId);
                QueryParam queryParam = QueryParam.newBuilder()
                        .withCollectionName(collectionName)
                        .withExpr(expr)
                        .withOutFields(List.of(ID_FIELD))
                        .withLimit((long) batchSize)
                        .build();

                R<QueryResults> queryResponse = milvusClient.query(queryParam);
                if (queryResponse.getStatus() != R.Status.Success.getCode()) {
                    log.warn("Query failed during batch deletion: {}", queryResponse.getMessage());
                    break;
                }

                QueryResultsWrapper wrapper = new QueryResultsWrapper(queryResponse.getData());
                if (wrapper.getRowRecords() == null || wrapper.getRowRecords().isEmpty()) {
                    // No more records to delete
                    break;
                }

                // Extract IDs and delete them
                List<Long> idsToDelete = new ArrayList<>();
                for (QueryResultsWrapper.RowRecord record : wrapper.getRowRecords()) {
                    Long id = (Long) record.get(ID_FIELD);
                    if (id != null) {
                        idsToDelete.add(id);
                    }
                }

                if (idsToDelete.isEmpty()) {
                    break;
                }

                // Delete by IDs
                String deleteExpr = String.format("id in %s", idsToDelete.toString().replace('[', '[').replace(']', ']'));
                DeleteParam deleteParam = DeleteParam.newBuilder()
                        .withCollectionName(collectionName)
                        .withExpr(deleteExpr)
                        .build();

                R<MutationResult> deleteResponse = milvusClient.delete(deleteParam);
                handleResponse(deleteResponse, "Batch delete prompts for user " + userId);

                totalDeleted += idsToDelete.size();
                log.debug("Deleted batch of {} records for user {}, total deleted: {}", 
                         idsToDelete.size(), userId, totalDeleted);

                // If we got fewer records than batch size, we're done
                if (idsToDelete.size() < batchSize) {
                    break;
                }

            } catch (Exception e) {
                log.error("Error in batch deletion for user {}: {}", userId, e.getMessage(), e);
                break;
            }
        }

        // Flush to ensure deletion is persisted
        FlushParam flushParam = FlushParam.newBuilder()
                .addCollectionName(collectionName)
                .build();
        milvusClient.flush(flushParam);

        log.info("Batch deletion completed for user {}, total deleted: {}", userId, totalDeleted);
    }

    /**
     * Ensure that a collection exists, create it if it doesn't
     */
    private void ensureCollectionExists(String collectionName) {
        if (!collectionExists(collectionName)) {
            createCollection(collectionName);
        }
    }

    /**
     * Check if a collection exists
     */
    private boolean collectionExists(String collectionName) {
        try {
            HasCollectionParam hasCollectionParam = HasCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build();

            R<Boolean> response = milvusClient.hasCollection(hasCollectionParam);
            handleResponse(response, "Check collection existence: " + collectionName);

            return response.getData();
        } catch (Exception e) {
            log.error("Error checking collection existence {}: {}", collectionName, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Create a new collection with vector field and user_id field
     */
    private void createCollection(String collectionName) {
        try {
            // Define collection schema
            List<FieldType> fields = Arrays.asList(
                    FieldType.newBuilder()
                            .withName(ID_FIELD)
                            .withDataType(DataType.Int64)
                            .withPrimaryKey(true)
                            .withAutoID(true)
                            .build(),
                    FieldType.newBuilder()
                            .withName(USER_ID_FIELD)
                            .withDataType(DataType.Int64)
                            .build(),
                    FieldType.newBuilder()
                            .withName(PROMPT_TEXT_FIELD)
                            .withDataType(DataType.VarChar)
                            .withMaxLength(65535)
                            .build(),
                    FieldType.newBuilder()
                            .withName(VECTOR_FIELD)
                            .withDataType(DataType.FloatVector)
                            .withDimension(embeddingService.getEmbeddingDimension())
                            .build(),
                    FieldType.newBuilder()
                            .withName(TIMESTAMP_FIELD)
                            .withDataType(DataType.Int64)
                            .build()
            );

            CollectionSchemaParam schema = CollectionSchemaParam.newBuilder()
                    .withFieldTypes(fields)
                    .build();

            CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withSchema(schema)
                    .build();

            R<RpcStatus> response = milvusClient.createCollection(createParam);
            handleResponse(response, "Create collection " + collectionName);

            // Create indexes
            createIndexes(collectionName);

            log.info("Successfully created collection: {}", collectionName);
        } catch (Exception e) {
            log.error("Error creating collection {}: {}", collectionName, e.getMessage(), e);
            throw new RuntimeException("Failed to create collection", e);
        }
    }

    /**
     * Create indexes for the collection
     */
    private void createIndexes(String collectionName) {
        try {
            // Create vector index
            CreateIndexParam vectorIndexParam = CreateIndexParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFieldName(VECTOR_FIELD)
                    .withIndexName(VECTOR_INDEX_NAME)
                    .withIndexType(io.milvus.param.IndexType.IVF_FLAT)
                    .withMetricType(io.milvus.param.MetricType.L2)
                    .withExtraParam("{\"nlist\":1024}")
                    .build();

            R<RpcStatus> vectorIndexResponse = milvusClient.createIndex(vectorIndexParam);
            handleResponse(vectorIndexResponse, "Create vector index for collection " + collectionName);

            // Create index for user_id field to improve query performance
            CreateIndexParam userIdIndexParam = CreateIndexParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFieldName(USER_ID_FIELD)
                    .withIndexName(USER_ID_INDEX_NAME)
                    .build();

            R<RpcStatus> userIdIndexResponse = milvusClient.createIndex(userIdIndexParam);
            handleResponse(userIdIndexResponse, "Create user_id index for collection " + collectionName);

            // Create index for timestamp field for sorting
            CreateIndexParam timestampIndexParam = CreateIndexParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFieldName(TIMESTAMP_FIELD)
                    .withIndexName(TIMESTAMP_INDEX_NAME)
                    .build();

            R<RpcStatus> timestampIndexResponse = milvusClient.createIndex(timestampIndexParam);
            handleResponse(timestampIndexResponse, "Create timestamp index for collection " + collectionName);

            log.info("Successfully created indexes for collection: {}", collectionName);
        } catch (Exception e) {
            log.error("Error creating indexes for collection {}: {}", collectionName, e.getMessage(), e);
            throw new RuntimeException("Failed to create indexes for collection", e);
        }
    }

    /**
     * Load collection into memory
     */
    private void loadCollection(String collectionName) {
        try {
            // Check if collection is already loaded
            GetLoadStateParam getLoadStateParam = GetLoadStateParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build();

            R<io.milvus.grpc.GetLoadStateResponse> stateResponse = milvusClient.getLoadState(getLoadStateParam);
            if (stateResponse.getStatus() == R.Status.Success.getCode() &&
                    stateResponse.getData().getState() == io.milvus.grpc.LoadState.LoadStateLoaded) {
                return; // Collection is already loaded
            }

            // Load collection
            LoadCollectionParam loadParam = LoadCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .build();

            R<RpcStatus> response = milvusClient.loadCollection(loadParam);
            handleResponse(response, "Load collection " + collectionName);

            // Wait for collection to be loaded
            waitForCollectionLoad(collectionName);

            log.debug("Successfully loaded collection: {}", collectionName);
        } catch (Exception e) {
            log.error("Error loading collection {}: {}", collectionName, e.getMessage(), e);
            throw new RuntimeException("Failed to load collection", e);
        }
    }

    /**
     * Wait for collection to be fully loaded
     */
    private void waitForCollectionLoad(String collectionName) {
        try {
            for (int i = 0; i < 30; i++) { // Wait up to 30 seconds
                GetLoadStateParam getLoadStateParam = GetLoadStateParam.newBuilder()
                        .withCollectionName(collectionName)
                        .build();

                R<io.milvus.grpc.GetLoadStateResponse> response = milvusClient.getLoadState(getLoadStateParam);
                if (response.getStatus() == R.Status.Success.getCode() &&
                        response.getData().getState() == io.milvus.grpc.LoadState.LoadStateLoaded) {
                    return;
                }

                TimeUnit.SECONDS.sleep(1);
            }
            log.warn("Collection {} may not be fully loaded yet", collectionName);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for collection {} to load", collectionName);
        } catch (Exception e) {
            log.warn("Error checking load state for collection {}: {}", collectionName, e.getMessage());
        }
    }

    /**
     * Handle Milvus API response and throw exception if failed
     */
    private <T> void handleResponse(R<T> response, String operation) {
        if (response.getStatus() != R.Status.Success.getCode()) {
            String errorMsg = String.format("%s failed: %s", operation, response.getMessage());
            log.error(errorMsg);
            throw new RuntimeException(errorMsg);
        }
    }

    /**
     * Inner class to hold prompt with timestamp for sorting
     */
    private static class PromptWithTimestamp {
        private final String promptText;
        private final Long timestamp;

        public PromptWithTimestamp(String promptText, Long timestamp) {
            this.promptText = promptText;
            this.timestamp = timestamp;
        }

        public String getPromptText() {
            return promptText;
        }

        public Long getTimestamp() {
            return timestamp;
        }
    }
}