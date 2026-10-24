package com.sondurukedella.clients.service;

import com.sondurukedella.clients.dto.ClientCreateRequest;
import com.sondurukedella.clients.dto.ClientResponse;
import com.sondurukedella.clients.dto.ClientUpdateRequest;
import com.sondurukedella.clients.entity.Client;
import com.sondurukedella.clients.entity.ClientKind;
import com.sondurukedella.clients.mapper.ClientMapper;
import com.sondurukedella.clients.repository.ClientRepository;
import com.sondurukedella.clients.repository.ClientSpecifications;
import com.sondurukedella.clients.validation.ClientText;
import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.common.web.PageResponse;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// No duplicate check on purpose - there's no business key for clients, and the same
// name or phone can legitimately belong to two different clients.
@Service
@RequiredArgsConstructor
public class ClientService {

    public static final int MAX_PAGE_SIZE = 100;
    public static final Set<String> SORTABLE_PROPERTIES = Set.of("name", "city", "createdAt", "id");

    private final ClientRepository repository;
    private final ClientMapper mapper;

    @Transactional(readOnly = true)
    public PageResponse<ClientResponse> search(String query, ClientKind kind, Pageable pageable) {
        return PageResponse.of(
                repository.findAll(ClientSpecifications.search(query, kind), stablePage(pageable)),
                mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ClientResponse get(long id) {
        return mapper.toResponse(load(id));
    }

    @Transactional
    public ClientResponse create(ClientCreateRequest request) {
        Client client = new Client(request.name().strip(), request.kind(), request.phone().strip());
        client.setEmail(ClientText.trimToNull(request.email()));
        client.setAddress(ClientText.trimToNull(request.address()));
        client.setCity(ClientText.trimToNull(request.city()));
        client.setNotes(ClientText.trimToNull(request.notes()));
        return mapper.toResponse(repository.saveAndFlush(client));
    }

    @Transactional
    public ClientResponse update(long id, ClientUpdateRequest request) {
        Client client = load(id);
        ConflictException.requireCurrentVersion(request.version(), client.getVersion());
        client.setName(request.name().strip());
        client.setKind(request.kind());
        client.setPhone(request.phone().strip());
        client.setEmail(ClientText.trimToNull(request.email()));
        client.setAddress(ClientText.trimToNull(request.address()));
        client.setCity(ClientText.trimToNull(request.city()));
        client.setNotes(ClientText.trimToNull(request.notes()));
        return mapper.toResponse(repository.saveAndFlush(client));
    }

    private Client load(long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Client not found."));
    }

    // Only allow known sort fields - an unknown one would blow up as a 500 inside Spring Data.
    // Sorting by id last stops rows with the same name from jumping between pages.
    private static Pageable stablePage(Pageable pageable) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("The page size can be at most " + MAX_PAGE_SIZE + ".");
        }
        Sort sort = pageable.getSort();
        for (Sort.Order order : sort) {
            if (!SORTABLE_PROPERTIES.contains(order.getProperty())) {
                throw new InvalidRequestException("Clients cannot be sorted by \"" + order.getProperty()
                        + "\". Use one of: name, city, createdAt, id.");
            }
        }
        if (sort.isUnsorted()) {
            sort = Sort.by("name");
        }
        if (sort.getOrderFor("id") == null) {
            sort = sort.and(Sort.by("id"));
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}
