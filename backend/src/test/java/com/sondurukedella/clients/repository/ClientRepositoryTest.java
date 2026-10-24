package com.sondurukedella.clients.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sondurukedella.clients.entity.Client;
import com.sondurukedella.clients.entity.ClientKind;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

// Uses the real Postgres schema from Flyway, not H2. Point it at a throwaway DB -
// each test rolls back, but the assertions expect an empty clients table.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ClientRepositoryTest {

    @Autowired
    private ClientRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        save("Dilani Jayawardena", ClientKind.INDIVIDUAL, "071 438 2291", "Anuradhapura");
        save("Rajarata Educational Trust", ClientKind.BUSINESS, "025 226 4410", "Kekirawa");
        save("Wasantha Kumara", ClientKind.INDIVIDUAL, "072 845 0016", "Kekirawa");
        save("100% Builders_Ltd", ClientKind.BUSINESS, "011 000 0000", null);
    }

    @Test
    void savedClientsGetAuditColumnsAndVersionZero() {
        Client client = repository.saveAndFlush(new Client("New Client", ClientKind.INDIVIDUAL, "0710000000"));

        assertThat(client.getId()).isNotNull();
        assertThat(client.getVersion()).isZero();
        assertThat(client.getCreatedAt()).isNotNull();
        assertThat(client.getUpdatedAt()).isNotNull();
        assertThat(client.getCreatedBy()).isNull();
    }

    @Test
    void searchMatchesNameCityOrPhoneIgnoringCase() {
        assertThat(names("JAYAWARDENA", null)).containsExactly("Dilani Jayawardena");
        assertThat(names("kekirawa", null)).containsExactly("Rajarata Educational Trust", "Wasantha Kumara");
        assertThat(names("845 00", null)).containsExactly("Wasantha Kumara");
    }

    @Test
    void searchCanBeLimitedToOneClientType() {
        assertThat(names("kekirawa", ClientKind.BUSINESS)).containsExactly("Rajarata Educational Trust");
        assertThat(names(null, ClientKind.BUSINESS)).containsExactly("100% Builders_Ltd", "Rajarata Educational Trust");
    }

    @Test
    void likeWildcardsInTheSearchAreMatchedLiterally() {
        assertThat(names("%", null)).containsExactly("100% Builders_Ltd");
        assertThat(names("s_l", null)).containsExactly("100% Builders_Ltd");
        assertThat(names("a_a", null)).isEmpty();
    }

    @Test
    void theDatabaseRejectsAnUnknownClientType() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO clients (name, client_type, phone) VALUES ('X', 'PARTNER', '0710000000')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // Split into two tests because Postgres aborts the whole transaction after the first error.
    @Test
    void theDatabaseRequiresAPhone() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO clients (name, phone) VALUES ('X', NULL)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseRequiresAName() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO clients (name, phone) VALUES (NULL, '0710000000')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private List<String> names(String query, ClientKind kind) {
        return repository.findAll(ClientSpecifications.search(query, kind), Sort.by("name")).stream()
                .map(Client::getName)
                .toList();
    }

    private void save(String name, ClientKind kind, String phone, String city) {
        Client client = new Client(name, kind, phone);
        client.setCity(city);
        repository.saveAndFlush(client);
    }
}
