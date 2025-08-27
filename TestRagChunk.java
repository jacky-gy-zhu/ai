import com.enable.ai.rag.vo.RagChunk;

public class TestRagChunk {
    public static void main(String[] args) {
        // Test with your sample text
        String sampleText = "@RagChunk.java DefaultToolDefinition[name=AI_Agent_mcpFilesystem_read_text_file, description=Read the complete contents of a file from the file system as text. Handles various text encodings and provides detailed error messages if the file cannot be read.";
        
        RagChunk chunk = new RagChunk();
        chunk.setText(sampleText);
        
        String extractedName = chunk.getNameFromText();
        System.out.println("Extracted name: " + extractedName);
        
        // Test edge cases
        RagChunk chunk2 = new RagChunk();
        chunk2.setText("Some text without name pattern");
        System.out.println("No name pattern: " + chunk2.getNameFromText());
        
        RagChunk chunk3 = new RagChunk();
        chunk3.setText("name=SimpleToolName, other=value");
        System.out.println("Simple name: " + chunk3.getNameFromText());
        
        RagChunk chunk4 = new RagChunk();
        chunk4.setText(null);
        System.out.println("Null text: " + chunk4.getNameFromText());
    }
}
