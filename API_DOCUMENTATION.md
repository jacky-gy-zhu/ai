# RAG Service REST API 文档

这个文档描述了 RAG (Retrieval-Augmented Generation) 服务提供的所有 REST API 端点。

## 基础信息

- **基础URL**: `http://localhost:8080/api/v1/rag`
- **Content-Type**: `application/json`
- **响应格式**: 统一的 JSON 格式

## 统一响应格式

所有API响应都遵循以下格式：

```json
{
  "code": 200,
  "message": "Success",
  "data": {},
  "timestamp": "2023-12-01T10:30:00",
  "success": true
}
```

## API 端点

### 1. 健康检查

检查RAG服务状态。

**GET** `/health`

**响应示例:**
```json
{
  "code": 200,
  "message": "Success",
  "data": "RAG service is running",
  "timestamp": "2023-12-01T10:30:00",
  "success": true
}
```

### 2. 添加文本块

向指定集合添加单个文本块。如果集合不存在会自动创建。

**POST** `/collections/{collectionName}/chunks`

**路径参数:**
- `collectionName` (string): 集合名称

**请求体:**
```json
{
  "text": "这是一个测试文本块的内容"
}
```

**响应示例:**
```json
{
  "code": 200,
  "message": "Chunk added successfully",
  "data": null,
  "timestamp": "2023-12-01T10:30:00",
  "success": true
}
```

### 3. 添加上下文

向指定集合添加大段上下文，系统会自动将其分割成多个文本块。

**POST** `/collections/{collectionName}/context`

**路径参数:**
- `collectionName` (string): 集合名称

**请求体:**
```json
{
  "context": "这是一个很长的上下文内容，包含了大量的信息。系统会自动将其分割成适当大小的文本块..."
}
```

**响应示例:**
```json
{
  "code": 200,
  "message": "Context added successfully",
  "data": null,
  "timestamp": "2023-12-01T10:30:00",
  "success": true
}
```

### 4. 检索相关文本块

根据查询检索最相关的K个文本块。

**POST** `/collections/{collectionName}/retrieve/chunks`

**路径参数:**
- `collectionName` (string): 集合名称

**请求体:**
```json
{
  "query": "查询文本",
  "k": 5
}
```

**响应示例:**
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "chunks": [
      {
        "text": "相关的文本块1"
      },
      {
        "text": "相关的文本块2"
      }
    ],
    "count": 2
  },
  "timestamp": "2023-12-01T10:30:00",
  "success": true
}
```

### 5. 检索相关上下文

根据查询检索最相关的文本内容，以拼接字符串的形式返回。

**POST** `/collections/{collectionName}/retrieve/context`

**路径参数:**
- `collectionName` (string): 集合名称

**请求体:**
```json
{
  "query": "查询文本",
  "k": 3
}
```

**响应示例:**
```json
{
  "code": 200,
  "message": "Success",
  "data": "相关文本块1的内容\n\n相关文本块2的内容\n\n相关文本块3的内容",
  "timestamp": "2023-12-01T10:30:00",
  "success": true
}
```

### 6. 删除集合

删除指定集合及其所有内容。

**DELETE** `/collections/{collectionName}`

**路径参数:**
- `collectionName` (string): 集合名称

**响应示例:**
```json
{
  "code": 200,
  "message": "Collection deleted successfully",
  "data": null,
  "timestamp": "2023-12-01T10:30:00",
  "success": true
}
```

## 错误响应

当请求失败时，API会返回相应的错误信息：

### 参数验证失败 (400)
```json
{
  "code": 400,
  "message": "Validation failed",
  "data": {
    "text": "Text cannot be blank",
    "k": "K must be at least 1"
  },
  "timestamp": "2023-12-01T10:30:00",
  "success": false
}
```

### 服务器内部错误 (500)
```json
{
  "code": 500,
  "message": "Internal server error: Connection to Milvus failed",
  "data": null,
  "timestamp": "2023-12-01T10:30:00",
  "success": false
}
```

## 使用示例

### cURL 示例

1. **添加文本块:**
```bash
curl -X POST "http://localhost:8080/api/v1/rag/collections/my-knowledge-base/chunks" \
  -H "Content-Type: application/json" \
  -d '{"text": "Spring Boot是一个优秀的Java框架"}'
```

2. **检索相关内容:**
```bash
curl -X POST "http://localhost:8080/api/v1/rag/collections/my-knowledge-base/retrieve/context" \
  -H "Content-Type: application/json" \
  -d '{"query": "Spring Boot框架", "k": 3}'
```

3. **删除集合:**
```bash
curl -X DELETE "http://localhost:8080/api/v1/rag/collections/my-knowledge-base"
```

## 参数限制

- **文本块最大长度**: 10,000 字符
- **上下文最大长度**: 100,000 字符
- **查询最大长度**: 1,000 字符
- **K值范围**: 1-50
- **集合名称**: 非空字符串

## 注意事项

1. 第一次向集合添加数据时，系统会自动创建集合和相应的索引
2. 删除集合操作不可逆，请谨慎使用
3. 系统使用向量相似度搜索，查询结果按相关性排序
4. 大文本会自动分块，每个块大小约1000字符，块之间有200字符重叠
