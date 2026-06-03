package com.example.pet.rag;

import com.example.pet.crud.PetInfo;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class PetRagService {

    private final VectorStore vectorStore;

    public PetRagService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public void indexPet(PetInfo pet) {
        Document doc = new Document(
                pet.toDocument(),
                Map.of("petId", pet.getId().toString(), "name", pet.getName(), "species", pet.getSpecies())
        );
        vectorStore.add(List.of(doc));
    }

    public void removePet(Long petId) {
        vectorStore.delete(List.of("pet-" + petId));
    }

    public List<Document> search(String query, int topK) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(0.3)
                .build();
        return vectorStore.similaritySearch(request);
    }
}
