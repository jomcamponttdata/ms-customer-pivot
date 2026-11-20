package com.pivot.banking.customer.infrastructure.adapter.out.persistence;

import com.pivot.banking.customer.application.port.out.CustomerRepositoryPort;
import com.pivot.banking.customer.application.exception.DuplicateCustomerDocumentException;
import com.pivot.banking.customer.domain.Customer;
import com.pivot.banking.customer.domain.CustomerType;
import com.pivot.banking.customer.domain.DocumentType;
import org.springframework.data.domain.Sort;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.adapter.rxjava.RxJava3Adapter;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import com.pivot.banking.customer.domain.CustomerStatus;

import java.util.UUID;

/** Implements customer persistence through Spring Data R2DBC. */
@Repository
public class R2dbcCustomerRepositoryAdapter implements CustomerRepositoryPort {

    private final SpringDataCustomerRepository repository;
    private final R2dbcEntityTemplate template;

    /** Creates the adapter with its Spring Data repository and entity template. */
    public R2dbcCustomerRepositoryAdapter(SpringDataCustomerRepository repository, R2dbcEntityTemplate template) {
        this.repository = repository;
        this.template = template;
    }

    /** Persists a customer and maps document uniqueness violations to the application error. */
    @Override
    public Single<Customer> save(Customer customer) {
        CustomerEntity entity = CustomerEntity.fromDomain(customer);
        Mono<Customer> result = repository.existsById(entity.getId())
                .flatMap(exists -> exists
                        ? template.update(entity)
                        : template.insert(entity))
                .map(CustomerEntity::toDomain)
                .onErrorMap(error -> error instanceof DuplicateKeyException
                                || error instanceof DataIntegrityViolationException,
                        exception -> isCustomerDocumentViolation(exception)
                                ? new DuplicateCustomerDocumentException()
                                : new IllegalArgumentException("Customer data violates database constraints"));
        return RxJava3Adapter.monoToSingle(result);
    }

    /** Loads a customer by its public UUID. */
    @Override
    public Maybe<Customer> findById(UUID id) {
        Mono<Customer> result = repository.findById(id).map(CustomerEntity::toDomain);
        return RxJava3Adapter.monoToMaybe(result);
    }

    /** Returns the requested page with a stable creation-time and identifier order. */
    @Override
    public Flowable<Customer> findAll(CustomerType customerType, int page, int size) {
        return findAll(customerType, CustomerStatus.ACTIVE, page, size);
    }

    /** Returns the requested page for one lifecycle status or all statuses. */
    @Override
    public Flowable<Customer> findAll(CustomerType customerType, CustomerStatus status, int page, int size) {
        Criteria criteria = customerType == null
                ? Criteria.empty()
                : Criteria.where("customer_type").is(customerType.name());
        if (status != null && status != CustomerStatus.ALL) {
            criteria = criteria.and("status").is(status.name());
        }
        Query query = Query.query(criteria)
                .sort(Sort.by(Sort.Order.asc("created_at"), Sort.Order.asc("id")))
                .limit(size)
                .offset((long) page * size);
        Flux<Customer> result = template.select(query, CustomerEntity.class).map(CustomerEntity::toDomain);
        return RxJava3Adapter.fluxToFlowable(result);
    }

    /** Checks whether a document identity is already registered. */
    @Override
    public Single<Boolean> existsByDocument(DocumentType documentType, String documentNumber) {
        Mono<Boolean> result =
                repository.existsByDocumentTypeAndDocumentNumber(documentType.name(), documentNumber);
        return RxJava3Adapter.monoToSingle(result);
    }

    /** Identifies a duplicate document constraint while preserving other database integrity failures. */
    private boolean isCustomerDocumentViolation(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null && (message.contains("uq_customers_document")
                    || message.toLowerCase().contains("duplicate"))) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
