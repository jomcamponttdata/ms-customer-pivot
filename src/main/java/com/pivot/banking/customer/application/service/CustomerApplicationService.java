package com.pivot.banking.customer.application.service;

import com.pivot.banking.customer.application.exception.CustomerNotFoundException;
import com.pivot.banking.customer.application.exception.DuplicateCustomerDocumentException;
import com.pivot.banking.customer.application.port.in.CustomerUseCases.CreateCustomer;
import com.pivot.banking.customer.application.port.in.CustomerUseCases.ActivateCustomer;
import com.pivot.banking.customer.application.port.in.CustomerUseCases.CreateCustomerCommand;
import com.pivot.banking.customer.application.port.in.CustomerUseCases.DeleteCustomer;
import com.pivot.banking.customer.application.port.in.CustomerUseCases.GetCustomers;
import com.pivot.banking.customer.application.port.in.CustomerUseCases.UpdateCustomer;
import com.pivot.banking.customer.application.port.in.CustomerUseCases.UpdateCustomerCommand;
import com.pivot.banking.customer.application.port.out.CustomerRepositoryPort;
import com.pivot.banking.customer.domain.Customer;
import com.pivot.banking.customer.domain.CustomerStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/** Coordinates customer use cases and delegates persistence through its output port. */
@Service
public class CustomerApplicationService implements CreateCustomer, UpdateCustomer, DeleteCustomer,
        ActivateCustomer, GetCustomers {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomerApplicationService.class);
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private final CustomerRepositoryPort repository;
    private final int maxPageSize;

    /** Creates the application service with its repository and configured page-size limit. */
    public CustomerApplicationService(CustomerRepositoryPort repository,
                                      @Value("${customer.pagination.max-size:100}") int maxPageSize) {
        this.repository = repository;
        this.maxPageSize = maxPageSize;
    }

    /** Creates a customer after checking that its document is not already registered. */
    @Override
    public Single<Customer> create(CreateCustomerCommand command) {
        validateCreateCommand(command);
        var normalizedDocumentNumber = normalizeDocumentNumber(command.documentNumber());
        var normalizedName = normalizeRequired(command.name(), "name");
        var normalizedEmail = normalizeOptional(command.email());
        var normalizedPhone = normalizeOptional(command.phone());
        var normalizedAddress = normalizeOptional(command.address());
        LOGGER.info("Creating customer");
        return repository.existsByDocument(command.documentType(), normalizedDocumentNumber)
                .flatMap(exists -> exists
                        ? Single.<Customer>error(new DuplicateCustomerDocumentException())
                        : repository.save(new Customer(UUID.randomUUID(), command.customerType(),
                                command.documentType(), normalizedDocumentNumber, normalizedName,
                                normalizedEmail, normalizedPhone, normalizedAddress,
                                CustomerStatus.ACTIVE, Instant.now())))
                .doOnSuccess(customer -> LOGGER.info("Customer created"))
                .doOnError(error -> LOGGER.warn("Customer creation failed errorType={}",
                        error.getClass().getSimpleName()));
    }

    /** Updates editable customer details while keeping the customer type immutable. */
    @Override
    public Single<Customer> update(UUID id, UpdateCustomerCommand command) {
        var normalizedDocumentNumber = normalizeDocumentNumber(command.documentNumber());
        var normalizedName = normalizeRequired(command.name(), "name");
        var normalizedEmail = normalizeOptional(command.email());
        var normalizedPhone = normalizeOptional(command.phone());
        var normalizedAddress = normalizeOptional(command.address());
        return findById(id).flatMap(customer -> {
            validateUpdateCommand(customer.customerType(), command);
            return repository.save(customer.update(command.documentType(),
                    normalizedDocumentNumber, normalizedName, normalizedEmail,
                    normalizedPhone, normalizedAddress));
        });
    }

    /** Performs the contract's logical deletion by marking the customer inactive. */
    @Override
    public Completable delete(UUID id) {
        LOGGER.info("Deactivating customer");
        return findById(id)
                .flatMap(customer -> repository.save(customer.deactivate()))
                .ignoreElement()
                .doOnComplete(() -> LOGGER.info("Customer deactivated"))
                .doOnError(error -> LOGGER.warn("Customer deactivation failed errorType={}",
                        error.getClass().getSimpleName()));
    }

    /** Reactivates an existing customer and makes it available to integrations again. */
    @Override
    public Single<Customer> activate(UUID id) {
        LOGGER.info("Activating customer");
        return repository.findById(id)
                .switchIfEmpty(Single.error(new CustomerNotFoundException(id)))
                .flatMap(customer -> repository.save(customer.activate()))
                .doOnSuccess(customer -> LOGGER.info("Customer activated"))
                .doOnError(error -> LOGGER.warn("Customer activation failed errorType={}",
                        error.getClass().getSimpleName()));
    }

    /** Lists customers with validated pagination and optional customer-type filtering. */
    @Override
    public Flowable<Customer> findAll(com.pivot.banking.customer.domain.CustomerType customerType, int page, int size) {
        return findAll(customerType, com.pivot.banking.customer.domain.CustomerStatus.ACTIVE, page, size);
    }

    /** Lists customers with optional status filtering. */
    @Override
    public Flowable<Customer> findAll(com.pivot.banking.customer.domain.CustomerType customerType,
                                      com.pivot.banking.customer.domain.CustomerStatus status,
                                      int page, int size) {
        if (page < 0 || size < 1 || size > maxPageSize) {
            return Flowable.error(new IllegalArgumentException(
                    "page must be non-negative and size must be between 1 and " + maxPageSize));
        }
        com.pivot.banking.customer.domain.CustomerStatus effectiveStatus =
                status == null ? com.pivot.banking.customer.domain.CustomerStatus.ACTIVE : status;
        return repository.findAll(customerType, effectiveStatus, page, size);
    }

    /** Retrieves a customer or signals that the requested resource does not exist. */
    @Override
    public Single<Customer> findById(UUID id) {
        return repository.findById(id)
                .switchIfEmpty(Single.error(new CustomerNotFoundException(id)))
                .flatMap(customer -> customer.status() == com.pivot.banking.customer.domain.CustomerStatus.ACTIVE
                        ? Single.just(customer)
                        : Single.error(new CustomerNotFoundException(id)))
                .doOnError(error -> LOGGER.warn("Customer lookup failed errorType={}",
                        error.getClass().getSimpleName()));
    }

    /** Validates fields that create a customer before any repository operation. */
    private void validateCreateCommand(com.pivot.banking.customer.application.port.in.CustomerUseCases.CreateCustomerCommand command) {
        if (command.customerType() == null || command.documentType() == null) {
            throw new IllegalArgumentException("customerType and documentType are required");
        }
        validateDocumentCompatibility(command.customerType(), command.documentType());
        validateDocumentAndName(command.documentType(), command.documentNumber(), command.name());
        validateOptionalFields(command.email(), command.phone());
    }

    /** Validates editable fields before updating a customer. */
    private void validateUpdateCommand(com.pivot.banking.customer.domain.CustomerType customerType,
                                       com.pivot.banking.customer.application.port.in.CustomerUseCases.UpdateCustomerCommand command) {
        if (command.documentType() == null) {
            throw new IllegalArgumentException("documentType is required");
        }
        validateDocumentCompatibility(customerType, command.documentType());
        validateDocumentAndName(command.documentType(), command.documentNumber(), command.name());
        validateOptionalFields(command.email(), command.phone());
    }

    private void validateDocumentAndName(com.pivot.banking.customer.domain.DocumentType documentType,
                                         String documentNumber, String name) {
        String document = normalizeDocumentNumber(documentNumber);
        if (documentNumber == null || document.isBlank()) {
            throw new IllegalArgumentException("documentNumber is required");
        }
        if (!document.matches("[A-Z0-9]{6,20}")) {
            throw new IllegalArgumentException("documentNumber must contain 6 to 20 alphanumeric characters");
        }
        if (documentType == com.pivot.banking.customer.domain.DocumentType.DNI && document.length() != 8) {
            throw new IllegalArgumentException("DNI must contain exactly 8 characters");
        }
        if (documentType == com.pivot.banking.customer.domain.DocumentType.RUC && document.length() != 11) {
            throw new IllegalArgumentException("RUC must contain exactly 11 characters");
        }
        normalizeRequired(name, "name");
    }

    private void validateDocumentCompatibility(com.pivot.banking.customer.domain.CustomerType customerType,
                                                com.pivot.banking.customer.domain.DocumentType documentType) {
        boolean personalDocument = documentType == com.pivot.banking.customer.domain.DocumentType.DNI
                || documentType == com.pivot.banking.customer.domain.DocumentType.CEX
                || documentType == com.pivot.banking.customer.domain.DocumentType.PASSPORT;
        boolean businessDocument = documentType == com.pivot.banking.customer.domain.DocumentType.RUC;
        if (customerType == com.pivot.banking.customer.domain.CustomerType.PERSONAL && !personalDocument) {
            throw new IllegalArgumentException("Personal customers require DNI, CEX or PASSPORT");
        }
        if (customerType == com.pivot.banking.customer.domain.CustomerType.BUSINESS && !businessDocument) {
            throw new IllegalArgumentException("Business customers require RUC");
        }
    }

    private void validateOptionalFields(String email, String phone) {
        if (email != null) {
            String value = email.trim();
            if (!value.isEmpty() && (!value.matches(".{1,254}") || !EMAIL_PATTERN.matcher(value).matches())) {
                throw new IllegalArgumentException("email format is invalid");
            }
        }
        if (phone != null) {
            String value = phone.trim();
            if (!value.isEmpty() && !value.matches("[0-9+() .-]{6,32}")) {
                throw new IllegalArgumentException("phone format is invalid");
            }
        }
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        if (value.trim().length() > 150) {
            throw new IllegalArgumentException(fieldName + " exceeds 150 characters");
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String normalizeDocumentNumber(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }
}
