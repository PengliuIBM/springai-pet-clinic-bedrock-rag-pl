package com.example.pet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.ChatClient.StreamResponseSpec;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class StreamChatControllerTest {

    private ChatClient chatClient;
    private StreamChatController controller;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(chatClient);
        controller = new StreamChatController(builder);
    }

    @Test
    void streamChat_returnsFluxOfTokens() {
        ChatClientRequestSpec requestSpec = mock(ChatClientRequestSpec.class);
        StreamResponseSpec streamSpec = mock(StreamResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.just("Hello", " ", "world"));

        Flux<String> result = controller.streamChat("hi");

        StepVerifier.create(result)
                .expectNext("Hello")
                .expectNext(" ")
                .expectNext("world")
                .verifyComplete();

        verify(requestSpec).user("hi");
    }

    @Test
    void streamChat_handlesEmptyResponse() {
        ChatClientRequestSpec requestSpec = mock(ChatClientRequestSpec.class);
        StreamResponseSpec streamSpec = mock(StreamResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.empty());

        Flux<String> result = controller.streamChat("hi");

        StepVerifier.create(result)
                .verifyComplete();
    }
}
