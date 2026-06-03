package com.example.pet.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/rag")
public class RagController {

    private final ChatClient chatClient;

    private final PetRagService ragService;

    public RagController(ChatClient.Builder chatClientBuilder, PetRagService ragService) {
        this.chatClient = chatClientBuilder.build();
        this.ragService = ragService;
    }

    @GetMapping("/ask")
    public String ask(@RequestParam String question,
                      @RequestParam(defaultValue = "3") int topK) {
        List<Document> relevant = ragService.search(question, topK);

        if (relevant.isEmpty()) {
            return "No relevant pet information found in the knowledge base for your question. "
                    + "Try adding more pets with descriptions, or switch to Direct Chat mode.";
        }

        String context = relevant.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n"));

        String prompt = String.format("""
                Based on the following pet information, answer the user's question.
                If the information is not sufficient, say so.

                Context:
                %s

                Question: %s
                """, context, question);

        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    @GetMapping("/search")
    public List<String> search(@RequestParam String query,
                               @RequestParam(defaultValue = "3") int topK) {
        return ragService.search(query, topK).stream()
                .map(Document::getText)
                .toList();
    }
}
