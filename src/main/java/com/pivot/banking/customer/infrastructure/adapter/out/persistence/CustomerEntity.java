package com.pivot.banking.customer.infrastructure.adapter.out.persistence;

import com.pivot.banking.customer.domain.Customer;
import com.pivot.banking.customer.domain.CustomerStatus;
import com.pivot.banking.customer.domain.CustomerType;
import com.pivot.banking.customer.domain.DocumentType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Relational persistence representation for a customer aggregate.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table("customers")
public class CustomerEntity {

    @Id
    private UUID id;
    private String customerType;
    private String documentType;
    private String documentNumber;
    private String name;
    private String email;
    private String phone;
    private String address;
    private String status;
    private Instant createdAt;

    /** Converts a domain customer to its persistence representation. */
    public static CustomerEntity fromDomain(Customer customer) {
        return new CustomerEntity(customer.id(), customer.customerType().name(), customer.documentType().name(),
                customer.documentNumber(), customer.name(), customer.email(), customer.phone(), customer.address(),
                customer.status().name(), customer.createdAt());
    }

    /** Reconstructs the domain customer from its persisted representation. */
    public Customer toDomain() {
        return new Customer(id, CustomerType.valueOf(customerType), DocumentType.valueOf(documentType),
                documentNumber, name, email, phone, address, CustomerStatus.valueOf(status), createdAt);
    }
}
