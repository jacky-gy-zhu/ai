package com.enable.ai.rag;

import com.enable.ai.rag.vo.RagChunk;

import java.util.List;

public interface RagService {

    /**
     * 如果collection不存在，创建一个新的collection
     * 指定chunk添加到collection
     */
    void addChunkToCollection(String collectionName, RagChunk chunk);

    /**
     * 检索与query最相关的top k个chunk
     */
    List<RagChunk> retrieveTopKChunks(String collectionName, String query, int k);

    /**
     * 如果collection不存在，创建一个新的collection
     * 将context内容添加到collection中，自动进行分块
     */
    void addContextToCollection(String collectionName, String context);

    /**
     * 检索与query最相关的top k个chunk，并将它们拼接成一个字符串返回
     */
    String retrieveContext(String collectionName, String query, int k);

    /**
     * 删除collection及其所有内容
     */
    void deleteCollection(String collectionName);

}
