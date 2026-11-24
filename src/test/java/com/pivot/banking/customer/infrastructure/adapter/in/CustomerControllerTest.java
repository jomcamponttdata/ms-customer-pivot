package com.pivot.banking.customer.infrastructure.adapter.in;

import com.pivot.banking.customer.application.port.in.CustomerUseCases.CreateCustomer;
import com.pivot.banking.customer.application.port.in.CustomerUseCases.ActivateCustomer;
import com.pivot.banking.customer.application.port.in.CustomerUseCases.CreateCustomerCommand;
import com.pivot.banking.customer.application.port.in.CustomerUseCases.DeleteCustomer;
import com.pivot.banking.customer.application.port.in.CustomerUseCases.GetCustomers;
import com.pivot.banking.customer.application.port.in.CustomerUseCases.UpdateCustomer;
import com.pivot.banking.customer.application.port.in.CustomerUseCases.UpdateCustomerCommand;
import com.pivot.banking.customer.domain.Customer;
import com.pivot.banking.customer.domain.CustomerStatus;
import com.pivot.banking.customer.domain.CustomerType;
import com.pivot.banking.customer.domain.DocumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.adapter.rxjava.RxJava3Adapter;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerControllerTest {

    @Mock
    private CreateCustomer createCustomer;
    @Mock
    private UpdateCustomer updateCustomer;
    @Mock
    private DeleteCustomer deleteCustomer;
    @Mock
    private GetCustomers getCustomers;
    @Mock
    private ActivateCustomer activateCustomer;
    @InjectMocks
    private CustomerController customerController = new CustomerController(
            null, null, null, null, null, new CustomerRestMapper(), 0, 20);

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        customerController = new CustomerController(
                createCustomer, updateCustomer, deleteCustomer, getCustomers, activateCustomer,
                new CustomerRestMapper(), 0, 20);
        webTestClient = WebTestClient.bindToController(customerController).build();
    }

    @Test
    void shouldDelegateCreateRequestAndReturnCreatedCustomer() {
        // Arrange
        UUID id = UUID.randomUUID();
        Customer customer = personalCustomer(id);
        when(createCustomer.create(any(CreateCustomerCommand.class))).thenReturn(Single.just(customer));

        // Act
        webTestClient.post()
                .uri("/api/v1/customers")
                .header("Content-Type", "application/json")
                .bodyValue("""
                        {
                          "customerType": "PERSONAL",
                          "documentType": "DNI",
                          "documentNumber": "12345678",
                          "name": "Ana Perez",
                          "email": "ana@example.com",
                          "phone": "987654321",
                          "address": "Lima"
                        }
                        """)
                .exchange()

                // Assert
                .expectStatus().isCreated()
                .expectHeader().valueEquals("Location", "/api/v1/customers/" + id)
                .expectBody()
                .jsonPath("$.id").isEqualTo(id.toString())
                .jsonPath("$.customerType").isEqualTo("PERSONAL")
                .jsonPath("$.documentType").isEqualTo("DNI")
                .jsonPath("$.documentNumber").isEqualTo("12345678")
                .jsonPath("$.name").isEqualTo("Ana Perez")
                .jsonPath("$.status").isEqualTo("ACTIVE");

        ArgumentCaptor<CreateCustomerCommand> captor = ArgumentCaptor.forClass(CreateCustomerCommand.class);
        verify(createCustomer, times(1)).create(captor.capture());
        assertEquals(CustomerType.PERSONAL, captor.getValue().customerType());
        assertEquals(DocumentType.DNI, captor.getValue().documentType());
        assertEquals("12345678", captor.getValue().documentNumber());
        assertEquals("Ana Perez", captor.getValue().name());
    }

    @Test
    void shouldDelegateFindByIdAndReturnOkCustomer() {
        // Arrange
        UUID id = UUID.randomUUID();
        Customer customer = personalCustomer(id);
        when(getCustomers.findById(id)).thenReturn(Single.just(customer));

        // Act
        webTestClient.get()
                .uri("/api/v1/customers/{customerId}", id)
                .exchange()

                // Assert
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(id.toString())
                .jsonPath("$.name").isEqualTo("Ana Perez");

        verify(getCustomers, times(1)).findById(id);
    }

    @Test
    void shouldDelegateFindAllWithDefaultPageAndSize() {
        // Arrange
        UUID id = UUID.randomUUID();
        when(getCustomers.findAll(null, CustomerStatus.ACTIVE, 0, 20))
                .thenReturn(Flowable.just(personalCustomer(id)));

        // Act
        webTestClient.get()
                .uri("/api/v1/customers")
                .exchange()

                // Assert
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo(id.toString());

        verify(getCustomers, times(1)).findAll(null, CustomerStatus.ACTIVE, 0, 20);
    }

    @Test
    void defaultsMissingStatusAndSupportsAllStatus() {
        // Arrange
        when(getCustomers.findAll(null, CustomerStatus.ACTIVE, 0, 20)).thenReturn(Flowable.empty());
        when(getCustomers.findAll(null, CustomerStatus.ALL, 0, 20)).thenReturn(Flowable.empty());

        // Act
        var active = customerController.findAllCustomers(null, null, 0, 20, null);
        var all = customerController.findAllCustomers(null, "ALL", 0, 20, null);

        // Assert
        RxJava3Adapter.monoToCompletable(active).test().assertComplete();
        RxJava3Adapter.monoToCompletable(all).test().assertComplete();
        verify(getCustomers).findAll(null, CustomerStatus.ACTIVE, 0, 20);
        verify(getCustomers).findAll(null, CustomerStatus.ALL, 0, 20);
    }

    @Test
    void defaultsMissingStatusToActiveAndAcceptsAllStatus() {
        // Arrange
        when(getCustomers.findAll(null, CustomerStatus.ACTIVE, 0, 20)).thenReturn(Flowable.empty());
        when(getCustomers.findAll(null, CustomerStatus.ALL, 0, 20)).thenReturn(Flowable.empty());

        // Act
        var defaultStatus = customerController.findAllCustomers(null, null, 0, 20, null);
        var allStatuses = customerController.findAllCustomers(null, "ALL", 0, 20, null);

        // Assert
        RxJava3Adapter.monoToCompletable(defaultStatus).test().assertComplete();
        RxJava3Adapter.monoToCompletable(allStatuses).test().assertComplete();
        verify(getCustomers).findAll(null, CustomerStatus.ACTIVE, 0, 20);
        verify(getCustomers).findAll(null, CustomerStatus.ALL, 0, 20);
    }

    @Test
    void shouldDelegateFindAllWithTypeAndExplicitPagination() {
        // Arrange
        when(getCustomers.findAll(CustomerType.PERSONAL, CustomerStatus.ACTIVE, 2, 10))
                .thenReturn(Flowable.empty());

        // Act
        webTestClient.get()
                .uri("/api/v1/customers?customerType=PERSONAL&page=2&size=10")
                .exchange()

                // Assert
                .expectStatus().isOk()
                .expectBody().json("[]");

        verify(getCustomers, times(1)).findAll(CustomerType.PERSONAL, CustomerStatus.ACTIVE, 2, 10);
    }

    @Test
    void shouldDelegateFindAllWithExplicitPageAndDefaultSize() {
        // Arrange
        when(getCustomers.findAll(null, CustomerStatus.ACTIVE, 3, 20)).thenReturn(Flowable.empty());

        // Act
        webTestClient.get()
                .uri("/api/v1/customers?page=3")
                .exchange()

                // Assert
                .expectStatus().isOk()
                .expectBody().json("[]");

        verify(getCustomers, times(1)).findAll(null, CustomerStatus.ACTIVE, 3, 20);
    }

    @Test
    void shouldDelegateFindAllWithExplicitSizeAndDefaultPage() {
        // Arrange
        when(getCustomers.findAll(null, CustomerStatus.ACTIVE, 0, 15)).thenReturn(Flowable.empty());

        // Act
        webTestClient.get()
                .uri("/api/v1/customers?size=15")
                .exchange()

                // Assert
                .expectStatus().isOk()
                .expectBody().json("[]");

        verify(getCustomers, times(1)).findAll(null, CustomerStatus.ACTIVE, 0, 15);
    }

    @Test
    void shouldCoverPartialFilterAndPaginationCombinations() {
        // Arrange
        when(getCustomers.findAll(CustomerType.PERSONAL, CustomerStatus.ACTIVE, 0, 20)).thenReturn(Flowable.empty());
        when(getCustomers.findAll(null, CustomerStatus.ACTIVE, 3, 20)).thenReturn(Flowable.empty());
        when(getCustomers.findAll(null, CustomerStatus.ACTIVE, 0, 15)).thenReturn(Flowable.empty());

        // Act
        var filtered = customerController.findAllCustomers(
                com.pivot.banking.customer.adapter.in.dto.CustomerType.PERSONAL,
                "ACTIVE", null, null, null);
        var paged = customerController.findAllCustomers(null, "ACTIVE", 3, null, null);
        var sized = customerController.findAllCustomers(null, "ACTIVE", null, 15, null);

        // Assert
        RxJava3Adapter.monoToCompletable(filtered).test().assertComplete();
        RxJava3Adapter.monoToCompletable(paged).test().assertComplete();
        RxJava3Adapter.monoToCompletable(sized).test().assertComplete();
        verify(getCustomers).findAll(CustomerType.PERSONAL, CustomerStatus.ACTIVE, 0, 20);
        verify(getCustomers).findAll(null, CustomerStatus.ACTIVE, 3, 20);
        verify(getCustomers).findAll(null, CustomerStatus.ACTIVE, 0, 15);
    }

    @Test
    void shouldDelegateUpdateRequestAndReturnUpdatedCustomer() {
        // Arrange
        UUID id = UUID.randomUUID();
        Customer customer = personalCustomer(id).update(DocumentType.DNI, "12345678",
                "Ana Gomez", "new@example.com", "999888777", "Cusco");
        when(updateCustomer.update(any(UUID.class), any(UpdateCustomerCommand.class)))
                .thenReturn(Single.just(customer));

        // Act
        webTestClient.put()
                .uri("/api/v1/customers/{customerId}", id)
                .header("Content-Type", "application/json")
                .bodyValue("""
                        {
                          "documentType": "DNI",
                          "documentNumber": "12345678",
                          "name": "Ana Gomez",
                          "email": "new@example.com",
                          "phone": "999888777",
                          "address": "Cusco"
                        }
                        """)
                .exchange()

                // Assert
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Ana Gomez")
                .jsonPath("$.address").isEqualTo("Cusco");

        ArgumentCaptor<UpdateCustomerCommand> captor = ArgumentCaptor.forClass(UpdateCustomerCommand.class);
        verify(updateCustomer, times(1)).update(any(UUID.class), captor.capture());
        assertEquals("Ana Gomez", captor.getValue().name());
        assertEquals("Cusco", captor.getValue().address());
    }

    @Test
    void shouldDelegateActivateRequestAndReturnActiveCustomer() {
        // Arrange
        UUID id = UUID.randomUUID();
        Customer customer = personalCustomer(id).activate();
        when(activateCustomer.activate(id)).thenReturn(Single.just(customer));

        // Act
        webTestClient.patch()
                .uri("/api/v1/customers/{customerId}/activate", id)
                .exchange()

                // Assert
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(id.toString())
                .jsonPath("$.status").isEqualTo("ACTIVE");

        verify(activateCustomer, times(1)).activate(id);
    }

    @Test
    void shouldDelegateDeleteAndReturnNoContent() {
        // Arrange
        UUID id = UUID.randomUUID();
        when(deleteCustomer.delete(id)).thenReturn(Completable.complete());

        // Act
        webTestClient.delete()
                .uri("/api/v1/customers/{customerId}", id)
                .exchange()

                // Assert
                .expectStatus().isNoContent();

        verify(deleteCustomer, times(1)).delete(id);
    }

    private Customer personalCustomer(UUID id) {
        return new Customer(id, CustomerType.PERSONAL, DocumentType.DNI, "12345678",
                "Ana Perez", "ana@example.com", "987654321", "Lima", CustomerStatus.ACTIVE,
                Instant.parse("2025-01-01T10:00:00Z"));
    }
}
