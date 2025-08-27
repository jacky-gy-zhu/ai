package com.enable.ai.rag;

import com.enable.ai.rag.vo.RagChunk;
import com.enable.ai.service.EmbeddingService;
import com.enable.ai.service.RagService;
import com.enable.ai.util.TextChunker;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.SearchResults;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.*;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MilvusRagService implements RagService {

    private final MilvusServiceClient milvusClient;
    private final EmbeddingService embeddingService;
    
    private static final String ID_FIELD = "id";
    private static final String TEXT_FIELD = "text";
    private static final String VECTOR_FIELD = "vector";
    private static final String INDEX_NAME = "vector_index";


    @Override
    public void addChunkToCollection(String collectionName, RagChunk chunk) {
        try {
            // Ensure collection exists
            ensureCollectionExists(collectionName);
            
            // Generate embedding for the chunk text
            List<Float> embedding = embeddingService.generateEmbedding(chunk.getText());
            
            // Generate a unique ID for this chunk
            String chunkId = UUID.randomUUID().toString();
            
            // Prepare data for insertion
            List<String> ids = List.of(chunkId);
            List<String> texts = List.of(chunk.getText());
            List<List<Float>> vectors = List.of(embedding);
            
            List<InsertParam.Field> fields = Arrays.asList(
                new InsertParam.Field(ID_FIELD, ids),
                new InsertParam.Field(TEXT_FIELD, texts),
                new InsertParam.Field(VECTOR_FIELD, vectors)
            );
            
            InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(collectionName)
                .withFields(fields)
                .build();
            
            R<io.milvus.grpc.MutationResult> response = milvusClient.insert(insertParam);
            handleResponse(response, "Insert chunk to collection " + collectionName);
            
            log.info("Successfully added chunk to collection: {}", collectionName);
        } catch (Exception e) {
            log.error("Error adding chunk to collection {}: {}", collectionName, e.getMessage(), e);
            throw new RuntimeException("Failed to add chunk to collection", e);
        }
    }

    @Override
    public List<RagChunk> retrieveTopKChunks(String collectionName, String query, int k) {
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
            
            // Prepare search parameters
            List<String> searchOutputFields = Arrays.asList(ID_FIELD, TEXT_FIELD);
            SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(collectionName)
                .withMetricType(io.milvus.param.MetricType.L2)
                .withOutFields(searchOutputFields)
                .withTopK(k)
                .withFloatVectors(List.of(queryEmbedding))
                .withVectorFieldName(VECTOR_FIELD)
                .withParams("{\"nprobe\":10}")
                .build();
            
            R<SearchResults> response = milvusClient.search(searchParam);
            handleResponse(response, "Search in collection " + collectionName);
            
            SearchResults results = response.getData();
            List<RagChunk> chunks = new ArrayList<>();
            
            if (results.getResults().getTopK() > 0) {
                for (int i = 0; i < Math.min(results.getResults().getTopK(), k); i++) {
                    String text = results.getResults().getFieldsData(1).getScalars().getStringData().getData(i);
                    chunks.add(new RagChunk(text));
                }
            }
            
            log.info("Retrieved {} chunks from collection: {}", chunks.size(), collectionName);
            return chunks;
        } catch (Exception e) {
            log.error("Error retrieving chunks from collection {}: {}", collectionName, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    @Override
    public void addContextToCollection(String collectionName, String context) {
        try {
            // Ensure collection exists
            ensureCollectionExists(collectionName);
            
            // Split context into chunks
            List<String> textChunks = TextChunker.chunkText(context);
            
            if (textChunks.isEmpty()) {
                log.warn("No chunks generated from context for collection: {}", collectionName);
                return;
            }
            
            // Prepare data for batch insertion
            List<String> ids = new ArrayList<>();
            List<String> texts = new ArrayList<>();
            List<List<Float>> vectors = new ArrayList<>();
            
            for (String chunk : textChunks) {
                ids.add(UUID.randomUUID().toString());
                texts.add(chunk);
                vectors.add(embeddingService.generateEmbedding(chunk));
            }
            
            List<InsertParam.Field> fields = Arrays.asList(
                new InsertParam.Field(ID_FIELD, ids),
                new InsertParam.Field(TEXT_FIELD, texts),
                new InsertParam.Field(VECTOR_FIELD, vectors)
            );
            
            InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(collectionName)
                .withFields(fields)
                .build();
            
            R<io.milvus.grpc.MutationResult> response = milvusClient.insert(insertParam);
            handleResponse(response, "Insert context chunks to collection " + collectionName);
            
            log.info("Successfully added {} chunks from context to collection: {}", textChunks.size(), collectionName);
        } catch (Exception e) {
            log.error("Error adding context to collection {}: {}", collectionName, e.getMessage(), e);
            throw new RuntimeException("Failed to add context to collection", e);
        }
    }

    @Override
    public String retrieveContext(String collectionName, String query, int k) {
        try {
            List<RagChunk> chunks = retrieveTopKChunks(collectionName, query, k);
            
            if (chunks.isEmpty()) {
                return "";
            }
            
            // Concatenate chunk texts with newlines
            StringBuilder contextBuilder = new StringBuilder();
            for (int i = 0; i < chunks.size(); i++) {
                if (i > 0) {
                    contextBuilder.append("\n\n");
                }
                contextBuilder.append(chunks.get(i).getText());
            }
            
            String result = contextBuilder.toString();
            log.info("Retrieved context of {} characters from {} chunks in collection: {}", 
                    result.length(), chunks.size(), collectionName);
            
            return result;
        } catch (Exception e) {
            log.error("Error retrieving context from collection {}: {}", collectionName, e.getMessage(), e);
            return "";
        }
    }

    @Override
    public void deleteCollection(String collectionName) {
        try {
            if (!collectionExists(collectionName)) {
                log.warn("Collection {} does not exist, nothing to delete", collectionName);
                return;
            }
            
            DropCollectionParam dropParam = DropCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build();
            
            R<RpcStatus> response = milvusClient.dropCollection(dropParam);
            handleResponse(response, "Delete collection " + collectionName);
            
            log.info("Successfully deleted collection: {}", collectionName);
        } catch (Exception e) {
            log.error("Error deleting collection {}: {}", collectionName, e.getMessage(), e);
            throw new RuntimeException("Failed to delete collection", e);
        }
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
     * Create a new collection with vector field
     */
    private void createCollection(String collectionName) {
        try {
            // Define collection schema
            List<FieldType> fields = Arrays.asList(
                FieldType.newBuilder()
                    .withName(ID_FIELD)
                    .withDataType(DataType.VarChar)
                    .withMaxLength(36)
                    .withPrimaryKey(true)
                    .build(),
                FieldType.newBuilder()
                    .withName(TEXT_FIELD)
                    .withDataType(DataType.VarChar)
                    .withMaxLength(65535)
                    .build(),
                FieldType.newBuilder()
                    .withName(VECTOR_FIELD)
                    .withDataType(DataType.FloatVector)
                    .withDimension(embeddingService.getEmbeddingDimension())
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
            
            // Create index for vector field
            createVectorIndex(collectionName);
            
            log.info("Successfully created collection: {}", collectionName);
        } catch (Exception e) {
            log.error("Error creating collection {}: {}", collectionName, e.getMessage(), e);
            throw new RuntimeException("Failed to create collection", e);
        }
    }
    
    /**
     * Create vector index for the collection
     */
    private void createVectorIndex(String collectionName) {
        try {
            CreateIndexParam indexParam = CreateIndexParam.newBuilder()
                .withCollectionName(collectionName)
                .withFieldName(VECTOR_FIELD)
                .withIndexName(INDEX_NAME)
                .withIndexType(io.milvus.param.IndexType.IVF_FLAT)
                .withMetricType(io.milvus.param.MetricType.L2)
                .withExtraParam("{\"nlist\":1024}")
                .build();
            
            R<RpcStatus> response = milvusClient.createIndex(indexParam);
            handleResponse(response, "Create index for collection " + collectionName);
            
            log.info("Successfully created index for collection: {}", collectionName);
        } catch (Exception e) {
            log.error("Error creating index for collection {}: {}", collectionName, e.getMessage(), e);
            throw new RuntimeException("Failed to create index for collection", e);
        }
    }
    
    /**
     * Load collection into memory
     */
    private void loadCollection(String collectionName) {
        try {
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
}