package com.digitalgrader.feedback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class ChatHintProviderTest {
    @ParameterizedTest
    @CsvSource({"ollama,max_tokens,max_completion_tokens", "openai,max_completion_tokens,max_tokens"})
    void usesCompatibleChatContractWithBoundedGuidance(String kind,String tokenField,String absentField) throws Exception {
        var body=new AtomicReference<String>();
        var auth=new AtomicReference<String>();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/chat/completions",exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response="{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"Check the failing assertion and compare the expected value.\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response);exchange.close();
        });
        server.start();
        try {
            var json=new ObjectMapper();
            var provider=new ChatHintProvider(json,true,"http://127.0.0.1:"+server.getAddress().getPort()+"/v1","fixture-key","fixture-model",kind);
            assertThat(provider.generate("{\"failingTests\":[]}").model()).isEqualTo("fixture-model");
            var request=json.readTree(body.get());
            assertThat(request.path("messages").get(1).path("content").asText()).isEqualTo("{\"failingTests\":[]}");
            assertThat(request.path(tokenField).asInt()).isEqualTo(400);
            assertThat(request.has(absentField)).isFalse();
            assertThat(auth.get()).isEqualTo("Bearer fixture-key");
        } finally { server.stop(0); }
    }
    @Test void disabledOrUnavailableProviderFailsWithoutLeakingDetails() {
        var disabled=new ChatHintProvider(new ObjectMapper(),false,"http://127.0.0.1:1/v1","","fixture","ollama");
        assertThatThrownBy(() -> disabled.generate("{}")).hasMessage("Hint service is unavailable; try again later");
        var offline=new ChatHintProvider(new ObjectMapper(),true,"http://127.0.0.1:1/v1","fixture-secret","fixture","ollama");
        assertThatThrownBy(() -> offline.generate("{}")).hasMessage("Hint service is unavailable; try again later");
    }
    @Test void rejectsUnsafeConfigurationAndFullCodeResponses() {
        assertThatThrownBy(() -> new ChatHintProvider(new ObjectMapper(),true,"http://external.example/v1","","fixture","ollama")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChatHintProvider.validate("public void solve() { return; }")).isInstanceOf(com.digitalgrader.common.ApiException.class);
        assertThatThrownBy(() -> ChatHintProvider.validate("word ".repeat(201))).isInstanceOf(com.digitalgrader.common.ApiException.class);
        assertThatThrownBy(() -> ChatHintProvider.validate("```java\nsolution\n```")).isInstanceOf(com.digitalgrader.common.ApiException.class);
    }
}
