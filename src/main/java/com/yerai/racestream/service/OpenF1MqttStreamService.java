/**
 * @author Yerai Pinto
 * @since 1.0
 * @version 1.0.0
 * @created 24-05-2026
 * @modified 24-05-2026
 * @description Cliente MQTT backend para recibir datos OpenF1 en directo y
 *              reenviarlos a la web mediante Server-Sent Events
 */
package com.yerai.racestream.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class OpenF1MqttStreamService {

    private static final Logger log = LoggerFactory.getLogger(OpenF1MqttStreamService.class);
    private static final String MQTT_URI = "ssl://mqtt.openf1.org:8883";
    private static final String[] TOPICS = {
            "v1/sessions",
            "v1/drivers",
            "v1/position",
            "v1/intervals",
            "v1/laps",
            "v1/stints",
            "v1/pit",
            "v1/race_control",
            "v1/team_radio",
            "v1/overtakes",
            "v1/weather",
            "v1/car_data",
            "v1/location",
            "v1/session_result"
    };

    private final OpenF1Service openF1Service;
    private final LiveDataStreamState streamState;
    private final ObjectMapper objectMapper;
    private final boolean streamEnabled;
    private final List<StreamClient> clients = new CopyOnWriteArrayList<>();
    private volatile MqttAsyncClient client;

    public OpenF1MqttStreamService(
            OpenF1Service openF1Service,
            LiveDataStreamState streamState,
            ObjectMapper objectMapper,
            @Value("${openf1.stream.enabled:true}") boolean streamEnabled) {
        this.openF1Service = openF1Service;
        this.streamState = streamState;
        this.objectMapper = objectMapper;
        this.streamEnabled = streamEnabled;
    }

    /**
     * @author Yerai Pinto
     * @since 1.0
     * @version 1.0.0
     * @created 24-05-2026
     * @modified 24-05-2026
     * @description Abre un stream SSE para una sesión y activa MQTT si todavía no
     *              está conectado
     * @param sessionKey Clave OpenF1 de sesión
     * @return Emisor SSE listo para el navegador
     */
    public SseEmitter subscribe(String sessionKey) {
        SseEmitter emitter = new SseEmitter(0L);
        StreamClient streamClient = new StreamClient(clean(sessionKey), emitter);
        clients.add(streamClient);
        emitter.onCompletion(() -> clients.remove(streamClient));
        emitter.onTimeout(() -> clients.remove(streamClient));
        emitter.onError(error -> clients.remove(streamClient));

        sendStatus(streamClient, "Conectando con streaming OpenF1.");
        ensureConnected();
        return emitter;
    }

    private synchronized void ensureConnected() {
        if (!streamEnabled) {
            emitStatusToAll("Streaming OpenF1 desactivado por configuración.");
            return;
        }
        try {
            if (client != null && client.isConnected()) {
                return;
            }
            String token = openF1Service.resolveStreamingAccessToken();
            if (token.isBlank()) {
                emitStatusToAll("No hay token válido para streaming OpenF1.");
                return;
            }
            MqttAsyncClient mqttClient = new MqttAsyncClient(
                    MQTT_URI,
                    "racestream-" + MqttAsyncClient.generateClientId(),
                    new MemoryPersistence());
            mqttClient.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    subscribeTopics(mqttClient);
                    emitStatusToAll(reconnect
                            ? "Streaming OpenF1 reconectado."
                            : "Streaming OpenF1 conectado.");
                }

                @Override
                public void connectionLost(Throwable cause) {
                    emitStatusToAll("Streaming OpenF1 desconectado. Se reintentará al actualizar la página.");
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    handleMessage(topic, message);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // Sin acción: RaceStream solo consume mensajes de OpenF1.
                }
            });
            mqttClient.connect(options(token)).waitForCompletion(10000L);
            client = mqttClient;
        } catch (MqttException ex) {
            log.warn("No se pudo conectar al streaming MQTT de OpenF1");
            emitStatusToAll("No se pudo conectar con streaming OpenF1.");
        }
    }

    private MqttConnectOptions options(String token) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setUserName("racestream");
        options.setPassword(token.toCharArray());
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(20);
        return options;
    }

    private void subscribeTopics(MqttAsyncClient mqttClient) {
        try {
            int[] qos = new int[TOPICS.length];
            mqttClient.subscribe(TOPICS, qos);
        } catch (MqttException ex) {
            log.warn("No se pudieron suscribir los topics MQTT de OpenF1");
            emitStatusToAll("No se pudieron suscribir los topics live de OpenF1.");
        }
    }

    private void handleMessage(String topic, MqttMessage message) {
        try {
            JsonNode payload = objectMapper.readTree(new String(message.getPayload(), StandardCharsets.UTF_8));
            ObjectNode event = streamState.update(topic, payload);
            if (event == null) {
                return;
            }
            clients.stream()
                    .filter(client -> client.accepts(event.path("sessionKey").asText("")))
                    .forEach(client -> sendEvent(client, "live-event", event));
        } catch (Exception ex) {
            log.warn("Mensaje MQTT de OpenF1 descartado por formato no válido");
        }
    }

    private void emitStatusToAll(String message) {
        clients.forEach(client -> sendStatus(client, message));
    }

    private void sendStatus(StreamClient client, String message) {
        ObjectNode event = objectMapper.createObjectNode();
        event.put("message", message);
        event.put("version", streamState.version());
        event.put("receivedAt", Instant.now().toString());
        sendEvent(client, "stream-status", event);
    }

    private void sendEvent(StreamClient client, String name, ObjectNode event) {
        try {
            client.emitter().send(SseEmitter.event()
                    .name(name)
                    .data(event.toString()));
        } catch (Exception ex) {
            clients.remove(client);
            client.emitter().complete();
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    @PreDestroy
    private void shutdown() {
        try {
            if (client != null && client.isConnected()) {
                client.disconnectForcibly(1000L, 1000L);
            }
            clients.forEach(streamClient -> streamClient.emitter().complete());
            clients.clear();
        } catch (MqttException ex) {
            log.warn("No se pudo cerrar limpiamente el streaming MQTT de OpenF1");
        }
    }

    private record StreamClient(String sessionKey, SseEmitter emitter) {
        private boolean accepts(String incomingSessionKey) {
            return sessionKey.isBlank() || sessionKey.equals(incomingSessionKey);
        }
    }
}
