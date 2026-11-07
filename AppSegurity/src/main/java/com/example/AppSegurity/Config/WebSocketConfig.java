package com.example.AppSegurity.Config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @org.springframework.beans.factory.annotation.Autowired
    private StompAutorizacionInterceptor stompAutorizacion;

    // Cada mensaje que envía un cliente (conectar, suscribirse, publicar) pasa antes por la autorización por propiedad
    @Override
    public void configureClientInboundChannel(org.springframework.messaging.simp.config.ChannelRegistration registration) {
        registration.interceptors(stompAutorizacion);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Por aquí viajaran las alertas hacia el profesor en tiempo real (Ej: /topic/alertas)
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // A esta ruta URL se conectará el PyQt6 del profesor para abrir el túnel
        registry.addEndpoint("/ws-supervision")
                .setAllowedOriginPatterns("*"); // Permite conexión externa
    }

    @Override
    public void configureWebSocketTransport(org.springframework.web.socket.config.annotation.WebSocketTransportRegistration registration) {
        // Aumentamos el límite para soportar fotogramas (frames) de cámara en Base64
        registration.setMessageSizeLimit(5 * 1024 * 1024); // 5 MB
        registration.setSendBufferSizeLimit(5 * 1024 * 1024); // 5 MB
        registration.setSendTimeLimit(20000); // 20 segundos
    }

    @org.springframework.context.annotation.Bean
    public org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean createWebSocketContainer() {
        org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean container = new org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean();
        // Tomcat por defecto tiene 8KB. Necesitamos aumentarlo para que quepa el JSON con Base64.
        container.setMaxTextMessageBufferSize(5 * 1024 * 1024); // 5 MB
        container.setMaxBinaryMessageBufferSize(5 * 1024 * 1024); // 5 MB
        return container;
    }
}