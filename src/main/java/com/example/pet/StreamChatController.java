package com.example.pet;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
public class StreamChatController {

    private final ChatClient chatClient;

    public StreamChatController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @GetMapping(value = "/chat/stream", produces = "text/event-stream")
    public Flux<String> streamChat(@RequestParam(defaultValue = "Tell me a joke") String message) {
        return chatClient.prompt()
                .user(message)
                .stream()
                .content();
    }
}
