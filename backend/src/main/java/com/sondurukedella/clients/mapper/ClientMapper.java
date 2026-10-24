package com.sondurukedella.clients.mapper;

import com.sondurukedella.clients.dto.ClientResponse;
import com.sondurukedella.clients.entity.Client;
import org.springframework.stereotype.Component;

@Component
public class ClientMapper {

    public ClientResponse toResponse(Client client) {
        return new ClientResponse(
                client.getId(),
                client.getName(),
                client.getKind(),
                client.getPhone(),
                client.getEmail(),
                client.getAddress(),
                client.getCity(),
                client.getNotes(),
                client.getCreatedAt(),
                client.getUpdatedAt(),
                client.getVersion());
    }
}
