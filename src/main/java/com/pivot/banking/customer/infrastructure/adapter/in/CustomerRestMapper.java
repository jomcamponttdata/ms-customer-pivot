package com.pivot.banking.customer.infrastructure.adapter.in;

import com.pivot.banking.customer.adapter.in.dto.Customer;
import com.pivot.banking.customer.adapter.in.dto.CustomerRequest;
import com.pivot.banking.customer.adapter.in.dto.CustomerUpdateRequest;
import com.pivot.banking.customer.application.port.in.CustomerUseCases.CreateCustomerCommand;
import com.pivot.banking.customer.application.port.in.CustomerUseCases.UpdateCustomerCommand;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

/** Converts between generated REST DTOs and customer application/domain types. */
@Component
public class CustomerRestMapper {

  /** Converts a create request into an application command. */
  public CreateCustomerCommand toCreateCommand(CustomerRequest request) {
    return new CreateCustomerCommand(
        com.pivot.banking.customer.domain.CustomerType.valueOf(request.getCustomerType().name()),
        request.getCustomerProfile() == null
            ? null
            : com.pivot.banking.customer.domain.CustomerProfile.valueOf(
                request.getCustomerProfile().name()),
        com.pivot.banking.customer.domain.DocumentType.valueOf(request.getDocumentType().name()),
        request.getDocumentNumber(),
        request.getName(),
        request.getEmail(),
        request.getPhone(),
        request.getAddress());
  }

  /** Converts an update request into an application command. */
  public UpdateCustomerCommand toUpdateCommand(CustomerUpdateRequest request) {
    return new UpdateCustomerCommand(
        com.pivot.banking.customer.domain.DocumentType.valueOf(request.getDocumentType().name()),
        request.getDocumentNumber(),
        request.getName(),
        request.getEmail(),
        request.getPhone(),
        request.getAddress());
  }

  /** Converts a domain customer into the generated REST response DTO. */
  public Customer toResponse(com.pivot.banking.customer.domain.Customer customer) {
    return new Customer(
            customer.id(),
            com.pivot.banking.customer.adapter.in.dto.CustomerType.valueOf(
                customer.customerType().name()),
            com.pivot.banking.customer.adapter.in.dto.DocumentType.valueOf(
                customer.documentType().name()),
            customer.documentNumber(),
            customer.name(),
            com.pivot.banking.customer.adapter.in.dto.CustomerStatus.valueOf(
                customer.status().name()),
            customer.createdAt().atOffset(ZoneOffset.UTC))
        .customerProfile(
            customer.customerProfile() == null
                ? null
                : com.pivot.banking.customer.adapter.in.dto.CustomerProfile.valueOf(
                    customer.customerProfile().name()))
        .email(customer.email())
        .phone(customer.phone())
        .address(customer.address());
  }
}
