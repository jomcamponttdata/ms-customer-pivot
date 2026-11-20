package com.pivot.banking.customer.infrastructure.adapter.in;

import com.pivot.banking.customer.adapter.in.api.CustomersApi;
import com.pivot.banking.customer.adapter.in.dto.Customer;
import com.pivot.banking.customer.adapter.in.dto.CustomerRequest;
import com.pivot.banking.customer.adapter.in.dto.CustomerType;
import com.pivot.banking.customer.application.port.in.CustomerUseCases.GetCustomers;
import com.pivot.banking.customer.adapter.in.dto.CustomerUpdateRequest;
import com.pivot.banking.customer.application.port.in.CustomerUseCases.CreateCustomer;
import com.pivot.banking.customer.application.port.in.CustomerUseCases.ActivateCustomer;
import com.pivot.banking.customer.application.port.in.CustomerUseCases.GetCustomers;
import com.pivot.banking.customer.application.port.in.CustomerUseCases.DeleteCustomer;
import com.pivot.banking.customer.application.port.in.CustomerUseCases.GetCustomers;
import com.pivot.banking.customer.application.port.in.CustomerUseCases.UpdateCustomer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.adapter.rxjava.RxJava3Adapter;

import java.net.URI;
import java.util.UUID;

/** Implements the generated customer REST API using application use cases. */
@Controller
public class CustomerController implements CustomersApi {

    private final CreateCustomer createCustomer;
    private final UpdateCustomer updateCustomer;
    private final DeleteCustomer deleteCustomer;
    private final ActivateCustomer activateCustomer;
    private final GetCustomers getCustomers;
    private final CustomerRestMapper mapper;
    private final int defaultPage;
    private final int defaultPageSize;

    /** Creates the REST adapter with use cases, mapper and paging defaults. */
    public CustomerController(CreateCustomer createCustomer, UpdateCustomer updateCustomer,
                              DeleteCustomer deleteCustomer, GetCustomers getCustomers,
                              ActivateCustomer activateCustomer,
                              CustomerRestMapper mapper,
                              @Value("${customer.pagination.default-page}") int defaultPage,
                              @Value("${customer.pagination.default-size}") int defaultPageSize) {
        this.createCustomer = createCustomer;
        this.updateCustomer = updateCustomer;
        this.deleteCustomer = deleteCustomer;
        this.getCustomers = getCustomers;
        this.activateCustomer = activateCustomer;
        this.mapper = mapper;
        this.defaultPage = defaultPage;
        this.defaultPageSize = defaultPageSize;
    }

    /** Creates a customer and returns its resource URI. */
    @Override
    public Mono<ResponseEntity<Customer>> createCustomer(Mono<CustomerRequest> request,
                                                          ServerWebExchange exchange) {
        return request.flatMap(body -> RxJava3Adapter.singleToMono(
                        createCustomer.create(mapper.toCreateCommand(body))))
                .map(mapper::toResponse)
                .map(customer -> ResponseEntity.created(
                        URI.create("/api/v1/customers/" + customer.getId())).body(customer));
    }

    /** Marks a customer inactive and returns no content. */
    @Override
    public Mono<ResponseEntity<Void>> deleteCustomer(UUID customerId, ServerWebExchange exchange) {
        return RxJava3Adapter.completableToMono(deleteCustomer.delete(customerId))
                .thenReturn(ResponseEntity.noContent().build());
    }

    /** Reactivates an inactive customer and makes it available to integrations again. */
    @PatchMapping("/api/v1/customers/{customerId}/activate")
    public Mono<ResponseEntity<Customer>> activateCustomer(UUID customerId, ServerWebExchange exchange) {
        return RxJava3Adapter.singleToMono(activateCustomer.activate(customerId))
                .map(mapper::toResponse)
                .map(ResponseEntity::ok);
    }

    /** Returns a filtered and paginated customer page. */
    @Override
    public Mono<ResponseEntity<Flux<Customer>>> findAllCustomers(
            @Nullable CustomerType customerType, @Nullable String status, Integer page, Integer size,
            ServerWebExchange exchange) {
        var type = customerType == null ? null
                : com.pivot.banking.customer.domain.CustomerType.valueOf(customerType.name());
        var lifecycleStatus = status == null
                ? com.pivot.banking.customer.domain.CustomerStatus.ACTIVE
                : com.pivot.banking.customer.domain.CustomerStatus.valueOf(status);
        Flux<Customer> customers = RxJava3Adapter.flowableToFlux(getCustomers.findAll(type, lifecycleStatus,
                        page == null ? defaultPage : page,
                        size == null ? defaultPageSize : size))
                .map(mapper::toResponse);
        return Mono.just(ResponseEntity.ok(customers));
    }

    /** Returns one customer by identifier. */
    @Override
    public Mono<ResponseEntity<Customer>> findCustomerById(UUID customerId, ServerWebExchange exchange) {
        return RxJava3Adapter.singleToMono(getCustomers.findById(customerId))
                .map(mapper::toResponse)
                .map(ResponseEntity::ok);
    }

    /** Replaces editable customer fields. */
    @Override
    public Mono<ResponseEntity<Customer>> updateCustomer(UUID customerId,
                                                          Mono<CustomerUpdateRequest> request,
                                                          ServerWebExchange exchange) {
        return request.flatMap(body -> RxJava3Adapter.singleToMono(
                        updateCustomer.update(customerId, mapper.toUpdateCommand(body))))
                .map(mapper::toResponse)
                .map(ResponseEntity::ok);
    }
}
