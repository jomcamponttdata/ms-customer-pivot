package com.pivot.banking.customer.application.port.in;

import com.pivot.banking.customer.domain.Customer;
import com.pivot.banking.customer.domain.CustomerType;
import com.pivot.banking.customer.domain.DocumentType;
import com.pivot.banking.customer.domain.CustomerStatus;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

import java.util.UUID;

/** Groups inbound customer use-case contracts and their command records. */
public final class CustomerUseCases {

    /** Prevents instantiation of this use-case namespace. */
    private CustomerUseCases() {
    }

    /** Input data required to create a customer. */
    public record CreateCustomerCommand(
            CustomerType customerType,
            DocumentType documentType,
            String documentNumber,
            String name,
            String email,
            String phone,
            String address) {
    }

    /** Editable customer data accepted by a full update request. */
    public record UpdateCustomerCommand(
            DocumentType documentType,
            String documentNumber,
            String name,
            String email,
            String phone,
            String address) {
    }

    /** Creates a customer. */
    public interface CreateCustomer {
        /** Executes customer creation. */
        Single<Customer> create(CreateCustomerCommand command);
    }

    /** Updates editable customer data. */
    public interface UpdateCustomer {
        /** Executes a customer update by identifier. */
        Single<Customer> update(UUID id, UpdateCustomerCommand command);
    }

    /** Logically deletes a customer. */
    public interface DeleteCustomer {
        /** Marks the identified customer inactive. */
        Completable delete(UUID id);
    }

    /** Reactivates an inactive customer. */
    public interface ActivateCustomer {
        /** Marks the identified customer active. */
        Single<Customer> activate(UUID id);
    }

    /** Queries customers. */
    public interface GetCustomers {
        /** Returns a page of customers, optionally filtered by type. */
        Flowable<Customer> findAll(CustomerType customerType, int page, int size);

        /** Returns a page of customers filtered by customer type and lifecycle status. */
        Flowable<Customer> findAll(CustomerType customerType, CustomerStatus status, int page, int size);

        /** Finds one customer by identifier. */
        Single<Customer> findById(UUID id);
    }
}
