package com.sondurukedella.clients.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.sondurukedella.clients.dto.ClientCreateRequest;
import com.sondurukedella.clients.dto.ClientResponse;
import com.sondurukedella.clients.dto.ClientUpdateRequest;
import com.sondurukedella.clients.entity.Client;
import com.sondurukedella.clients.entity.ClientKind;
import com.sondurukedella.clients.mapper.ClientMapper;
import com.sondurukedella.clients.repository.ClientRepository;
import com.sondurukedella.common.exception.ConflictException;
import com.sondurukedella.common.exception.InvalidRequestException;
import com.sondurukedella.common.exception.NotFoundException;
import com.sondurukedella.common.web.PageResponse;
import com.sondurukedella.support.TestEntities;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock
    private ClientRepository repository;

    private ClientService service;

    @BeforeEach
    void setUp() {
        service = new ClientService(repository, new ClientMapper());
    }

    @Test
    void createTrimsTextAndStoresBlankOptionalFieldsAsNull() {
        when(repository.saveAndFlush(any(Client.class))).thenAnswer(call -> {
            Client saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 11L); // assigned by the database identity column
            ReflectionTestUtils.setField(saved, "version", 0L);
            return saved;
        });

        ClientResponse created = service.create(new ClientCreateRequest(
                "  Dilani Jayawardena ", ClientKind.INDIVIDUAL, " 071 438 2291 ", "   ", "  ", " Anuradhapura ", "\n"));

        assertThat(created.id()).isEqualTo(11L);
        assertThat(created.name()).isEqualTo("Dilani Jayawardena");
        assertThat(created.kind()).isEqualTo(ClientKind.INDIVIDUAL);
        assertThat(created.phone()).isEqualTo("071 438 2291");
        assertThat(created.email()).isNull();
        assertThat(created.address()).isNull();
        assertThat(created.city()).isEqualTo("Anuradhapura");
        assertThat(created.notes()).isNull();
        assertThat(created.version()).isZero();
    }

    @Test
    void createDoesNotRejectAClientWithTheSameNameOrPhone() {
        // same name/phone is allowed, so create shouldn't query for duplicates at all
        when(repository.saveAndFlush(any(Client.class))).thenAnswer(call -> withId(call.getArgument(0), 12L));

        service.create(new ClientCreateRequest("K. M. Bandara", ClientKind.INDIVIDUAL, "077 512 9043",
                null, null, null, null));

        verify(repository).saveAndFlush(any(Client.class));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void updateReplacesAllEditableFields() {
        Client client = client(5L, 3L);
        when(repository.findById(5L)).thenReturn(Optional.of(client));
        when(repository.saveAndFlush(client)).thenReturn(client);

        ClientResponse updated = service.update(5L, new ClientUpdateRequest(
                " Rajarata Educational Trust ", ClientKind.BUSINESS, "025 226 4410", " admin@rajaratatrust.lk ",
                "Main Street", "Kekirawa", " Pays by cheque. ", 3L));

        assertThat(updated.name()).isEqualTo("Rajarata Educational Trust");
        assertThat(updated.kind()).isEqualTo(ClientKind.BUSINESS);
        assertThat(updated.phone()).isEqualTo("025 226 4410");
        assertThat(updated.email()).isEqualTo("admin@rajaratatrust.lk");
        assertThat(updated.address()).isEqualTo("Main Street");
        assertThat(updated.city()).isEqualTo("Kekirawa");
        assertThat(updated.notes()).isEqualTo("Pays by cheque.");
    }

    @Test
    void updateCanClearOptionalFields() {
        Client client = client(5L, 0L);
        client.setEmail("old@example.com");
        client.setNotes("Old note");
        when(repository.findById(5L)).thenReturn(Optional.of(client));
        when(repository.saveAndFlush(client)).thenReturn(client);

        ClientResponse updated = service.update(5L, new ClientUpdateRequest(
                "Client", ClientKind.INDIVIDUAL, "0712345678", "", null, null, " ", 0L));

        assertThat(updated.email()).isNull();
        assertThat(updated.notes()).isNull();
    }

    @Test
    void updateWithAStaleVersionIsRejectedWithoutSaving() {
        Client client = client(5L, 4L);
        when(repository.findById(5L)).thenReturn(Optional.of(client));

        assertThatThrownBy(() -> service.update(5L, new ClientUpdateRequest(
                "Changed", ClientKind.INDIVIDUAL, "0712345678", null, null, null, null, 3L)))
                .isInstanceOf(ConflictException.class);
        assertThat(client.getName()).isEqualTo("Original");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void unknownClientsAreNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.update(99L, new ClientUpdateRequest(
                "Name", ClientKind.INDIVIDUAL, "0712345678", null, null, null, null, 0L)))
                .isInstanceOf(NotFoundException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchSortsByNameThenIdByDefault() {
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        when(repository.findAll(any(Specification.class), page.capture()))
                .thenReturn(new PageImpl<>(List.of(client(1L, 0L)), PageRequest.of(0, 20), 1));

        PageResponse<ClientResponse> result = service.search("anu", null, PageRequest.of(0, 20));

        assertThat(page.getValue().getSort()).containsExactly(Sort.Order.asc("name"), Sort.Order.asc("id"));
        assertThat(result.content()).hasSize(1);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.page()).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchKeepsTheRequestedSortAndAddsTheIdTieBreaker() {
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        when(repository.findAll(any(Specification.class), page.capture())).thenReturn(new PageImpl<>(List.of()));

        service.search(null, ClientKind.BUSINESS, PageRequest.of(2, 10, Sort.by(Sort.Order.desc("createdAt"))));

        assertThat(page.getValue().getPageNumber()).isEqualTo(2);
        assertThat(page.getValue().getSort()).containsExactly(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
    }

    @Test
    void searchRejectsUnsupportedSortPropertiesAndLargePages() {
        assertThatThrownBy(() -> service.search(null, null, PageRequest.of(0, 20, Sort.by("notes"))))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.search(null, null, PageRequest.of(0, ClientService.MAX_PAGE_SIZE + 1)))
                .isInstanceOf(InvalidRequestException.class);
    }

    private static Client client(long id, long version) {
        return TestEntities.create(Client.class, Map.of(
                "id", id, "name", "Original", "kind", ClientKind.INDIVIDUAL, "phone", "0710000000",
                "version", version));
    }

    private static Client withId(Client client, long id) {
        ReflectionTestUtils.setField(client, "id", id);
        ReflectionTestUtils.setField(client, "version", 0L);
        return client;
    }
}
