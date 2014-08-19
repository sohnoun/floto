package io.github.floto.server.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import io.github.floto.util.task.TaskInfo;
import io.github.floto.util.task.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

@ClientEndpoint
@ServerEndpoint(value = "/tasks/_websocket")
public class TasksWebSocket {
    private final Logger log = LoggerFactory.getLogger(TasksWebSocket.class);

    private Session session;

    private TaskService taskService;

    private ObjectMapper objectMapper = new ObjectMapper();


    public TasksWebSocket(TaskService taskService) {
        this.taskService = taskService;
    }

    @OnOpen
    public void onWebSocketConnect(Session sess) {
        this.session = sess;
    }

    @OnMessage
    public void onWebSocketText(String message) {
        try {
            JsonNode jsonNode = objectMapper.reader().readTree(message);
            String command = jsonNode.get("command").asText();
            String taskId = jsonNode.get("taskId").asText();
            if ("registerCompletionListener".equals(command)) {
                TaskInfo taskInfo = taskService.getTaskInfo(taskId);
                taskInfo.getCompletionStage().whenCompleteAsync((BiConsumer<Object, Throwable>) (a, error) -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("type", "taskComplete");
                    result.put("taskId", taskId);
                    result.put("taskTitle", taskInfo.getTitle());
                    result.put("status", error == null ? "success" : "error");
                    if (error != null) {
                        result.put("errorMessage", error.getMessage());
                    }
                    sendMessage(result);
                });
            } else if ("registerLogListener".equals(command)) {
                String streamId = jsonNode.get("streamId").asText();
                LogPusher logPusher = new LogPusher(taskService.getLogStream(taskId), streamId, (messageString) -> {
                    sendTextMessage(messageString);
                });
                logPusher.start();
            } else {
                log.error("Unknown command {}", command);
            }

        } catch (IOException e) {
            Throwables.propagate(e);
        }
    }

    private void sendMessage(Object message) {
        try {
            session.getAsyncRemote().sendText(objectMapper.writeValueAsString(message));
        } catch (Throwable throwable) {
            log.error("Unable to send message {}", message, throwable);
        }
    }

    private void sendTextMessage(String textMessage) {
        try {
            session.getAsyncRemote().sendText(textMessage);
        } catch (Throwable throwable) {
            log.error("Unable to send message {}", textMessage, throwable);
        }
    }

    @OnClose
    public void onWebSocketClose(CloseReason reason) {
        System.out.println("Socket Closed: " + reason);
    }

    @OnError
    public void onWebSocketError(Throwable cause) {
        log.error("WebSocket error", cause);
    }
}