package com.pivot.banking.customer.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pivot.banking.customer.application.exception.CustomerNotFoundException;
import com.pivot.banking.customer.application.exception.DuplicateCustomerDocumentException;
import com.pivot.banking.customer.application.port.in.CustomerUseCases.CreateCustomerCommand;
import com.pivot.banking.customer.application.port.in.CustomerUseCases.UpdateCustomerCommand;
import com.pivot.banking.customer.application.port.out.CustomerRepositoryPort;
import com.pivot.banking.customer.domain.Customer;
import com.pivot.banking.customer.domain.CustomerProfile;
import com.pivot.banking.customer.domain.CustomerStatus;
import com.pivot.banking.customer.domain.CustomerType;
import com.pivot.banking.customer.domain.DocumentType;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerApplicationServiceTest {

  @Mock private CustomerRepositoryPort repository;

  private CustomerApplicationService service;

  @BeforeEach
  void setUp() {
    service = new CustomerApplicationService(repository, 100);
  }

  @Test
  void createStoresAnActiveCustomerWhenDocumentIsAvailable() {
    // Arrange
    var command =
        new CreateCustomerCommand(
            CustomerType.PERSONAL,
            DocumentType.DNI,
            "12345678",
            "Ana Perez",
            "ana@example.com",
            "987654321",
            "Lima");
    when(repository.existsByDocument(DocumentType.DNI, "12345678")).thenReturn(Single.just(false));
    when(repository.save(any(Customer.class)))
        .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

    // Act
    var result = service.create(command);

    // Assert
    TestObserver<Customer> observer = result.test();
    observer.assertComplete();
    Customer customer = observer.values().get(0);
    assertNotNull(customer.id());
    assertEquals(CustomerType.PERSONAL, customer.customerType());
    assertNull(customer.customerProfile());
    assertEquals(CustomerStatus.ACTIVE, customer.status());
    assertEquals("12345678", customer.documentNumber());
    assertNotNull(customer.createdAt());

    verify(repository).save(any(Customer.class));
  }

  @Test
  void createRejectsARegisteredDocumentWithoutSaving() {
    // Arrange
    var command =
        new CreateCustomerCommand(
            CustomerType.PERSONAL, DocumentType.DNI, "12345678", "Ana Perez", null, null, null);
    when(repository.existsByDocument(DocumentType.DNI, "12345678")).thenReturn(Single.just(true));

    // Act
    var result = service.create(command);

    // Assert
    result.test().assertError(DuplicateCustomerDocumentException.class);

    verify(repository, never()).save(any(Customer.class));
  }

  @Test
  void createRejectsPersonalCustomerWithRucDocument() {
    // Arrange
    var command =
        new CreateCustomerCommand(
            CustomerType.PERSONAL, DocumentType.RUC, "20123456789", "Ana Perez", null, null, null);

    // Act
    var exception = assertThrows(IllegalArgumentException.class, () -> service.create(command));

    // Assert
    assertEquals("Personal customers require DNI, CEX or PASSPORT", exception.getMessage());
    verify(repository, never()).existsByDocument(any(), any());
    verify(repository, never()).save(any(Customer.class));
  }

  @Test
  void createRejectsInvalidDocumentNameAndEmailBeforeRepositoryAccess() {
    // Arrange
    var invalidDni =
        new CreateCustomerCommand(
            CustomerType.PERSONAL,
            DocumentType.DNI,
            "123",
            "Ana Perez",
            "invalid-email",
            null,
            null);

    // Act
    var documentException =
        assertThrows(IllegalArgumentException.class, () -> service.create(invalidDni));
    var nameException =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                service.create(
                    new CreateCustomerCommand(
                        CustomerType.PERSONAL,
                        DocumentType.DNI,
                        "12345678",
                        " ",
                        null,
                        null,
                        null)));

    // Assert
    assertNotNull(documentException.getMessage());
    assertEquals("name is required", nameException.getMessage());
    verify(repository, never()).existsByDocument(any(), any());
    verify(repository, never()).save(any(Customer.class));
  }

  @Test
  void acceptsAnEmailAtTheMaximumSupportedLength() {
    // Arrange
    String email = "a".repeat(242) + "@example.com";
    var command =
        new CreateCustomerCommand(
            CustomerType.PERSONAL, DocumentType.DNI, "12345678", "Ana Perez", email, null, null);
    when(repository.existsByDocument(DocumentType.DNI, "12345678")).thenReturn(Single.just(false));
    when(repository.save(any(Customer.class)))
        .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

    // Act
    var result = service.create(command);

    // Assert
    result.test().assertComplete().assertValue(customer -> email.equals(customer.email()));
  }

  @Test
  void createValidatesDocumentLengthsNameAndOptionalFields() {
    // Arrange
    var dniShort =
        new CreateCustomerCommand(
            CustomerType.PERSONAL, DocumentType.DNI, "123", "Ana Perez", null, null, null);
    var rucShort =
        new CreateCustomerCommand(
            CustomerType.BUSINESS, DocumentType.RUC, "20123", "Empresa", null, null, null);
    var nameTooLong =
        new CreateCustomerCommand(
            CustomerType.PERSONAL, DocumentType.DNI, "12345678", "A".repeat(151), null, null, null);
    var invalidPhone =
        new CreateCustomerCommand(
            CustomerType.PERSONAL, DocumentType.DNI, "12345678", "Ana Perez", null, "abc", null);

    // Act
    var dniException = assertThrows(IllegalArgumentException.class, () -> service.create(dniShort));
    var rucException = assertThrows(IllegalArgumentException.class, () -> service.create(rucShort));
    var nameException =
        assertThrows(IllegalArgumentException.class, () -> service.create(nameTooLong));
    var phoneException =
        assertThrows(IllegalArgumentException.class, () -> service.create(invalidPhone));

    // Assert
    assertNotNull(dniException.getMessage());
    assertNotNull(rucException.getMessage());
    assertNotNull(nameException.getMessage());
    assertNotNull(phoneException.getMessage());
    verify(repository, never()).existsByDocument(any(), any());
    verify(repository, never()).save(any(Customer.class));
  }

  @Test
  void createAcceptsPassportAndBusinessRuc() {
    // Arrange
    var passport =
        new CreateCustomerCommand(
            CustomerType.PERSONAL,
            DocumentType.PASSPORT,
            "A1234567",
            "Ana Perez",
            null,
            null,
            null);
    var ruc =
        new CreateCustomerCommand(
            CustomerType.BUSINESS,
            DocumentType.RUC,
            "20123456789",
            "Empresa SAC",
            "empresa@example.com",
            "+51 999",
            "Lima");
    when(repository.existsByDocument(DocumentType.PASSPORT, "A1234567"))
        .thenReturn(Single.just(false));
    when(repository.existsByDocument(DocumentType.RUC, "20123456789"))
        .thenReturn(Single.just(false));
    when(repository.save(any(Customer.class)))
        .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

    // Act
    var passportObserver = service.create(passport).test();
    var rucObserver = service.create(ruc).test();

    // Assert
    passportObserver.assertComplete();
    rucObserver.assertComplete();
    verify(repository, org.mockito.Mockito.times(2)).save(any(Customer.class));
  }

  @Test
  void createAcceptsOnlyProfilesCompatibleWithCustomerType() {
    var vip =
        new CreateCustomerCommand(
            CustomerType.PERSONAL,
            CustomerProfile.VIP,
            DocumentType.DNI,
            "12345678",
            "Ana Perez",
            null,
            null,
            null);
    var pyme =
        new CreateCustomerCommand(
            CustomerType.BUSINESS,
            CustomerProfile.PYME,
            DocumentType.RUC,
            "20123456789",
            "Empresa SAC",
            null,
            null,
            null);
    when(repository.existsByDocument(DocumentType.DNI, "12345678")).thenReturn(Single.just(false));
    when(repository.existsByDocument(DocumentType.RUC, "20123456789"))
        .thenReturn(Single.just(false));
    when(repository.save(any(Customer.class)))
        .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

    service
        .create(vip)
        .test()
        .assertComplete()
        .assertValue(customer -> customer.customerProfile() == CustomerProfile.VIP);
    service
        .create(pyme)
        .test()
        .assertComplete()
        .assertValue(customer -> customer.customerProfile() == CustomerProfile.PYME);
  }

  @Test
  void createRejectsProfilesIncompatibleWithCustomerTypeBeforeRepositoryAccess() {
    var vipForBusiness =
        new CreateCustomerCommand(
            CustomerType.BUSINESS,
            CustomerProfile.VIP,
            DocumentType.RUC,
            "20123456789",
            "Empresa SAC",
            null,
            null,
            null);
    var pymeForPersonal =
        new CreateCustomerCommand(
            CustomerType.PERSONAL,
            CustomerProfile.PYME,
            DocumentType.DNI,
            "12345678",
            "Ana Perez",
            null,
            null,
            null);

    assertEquals(
        "VIP profile requires PERSONAL customer type",
        assertThrows(IllegalArgumentException.class, () -> service.create(vipForBusiness))
            .getMessage());
    assertEquals(
        "PYME profile requires BUSINESS customer type",
        assertThrows(IllegalArgumentException.class, () -> service.create(pymeForPersonal))
            .getMessage());

    verify(repository, never()).existsByDocument(any(), any());
    verify(repository, never()).save(any(Customer.class));
  }

  @Test
  void updateKeepsCustomerTypeAndLifecycleFields() {
    // Arrange
    UUID id = UUID.randomUUID();
    Instant createdAt = Instant.parse("2025-01-01T10:00:00Z");
    var existing =
        new Customer(
            id,
            CustomerType.BUSINESS,
            CustomerProfile.PYME,
            DocumentType.RUC,
            "20123456789",
            "Empresa",
            null,
            null,
            null,
            CustomerStatus.ACTIVE,
            createdAt);
    var command =
        new UpdateCustomerCommand(
            DocumentType.RUC,
            "20123456789",
            "Empresa Nueva",
            "contacto@example.com",
            "999999999",
            "Arequipa");
    when(repository.findById(id)).thenReturn(Maybe.just(existing));
    when(repository.save(any(Customer.class)))
        .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

    // Act
    var result = service.update(id, command);

    // Assert
    TestObserver<Customer> observer = result.test();
    observer.assertComplete();
    Customer updated = observer.values().get(0);
    assertEquals(CustomerType.BUSINESS, updated.customerType());
    assertEquals(CustomerProfile.PYME, updated.customerProfile());
    assertEquals(CustomerStatus.ACTIVE, updated.status());
    assertEquals(createdAt, updated.createdAt());
    assertEquals("Empresa Nueva", updated.name());
  }

  @Test
  void updateRejectsDocumentThatDoesNotMatchExistingCustomerType() {
    // Arrange
    UUID id = UUID.randomUUID();
    var existing =
        new Customer(
            id,
            CustomerType.PERSONAL,
            DocumentType.DNI,
            "12345678",
            "Ana Perez",
            null,
            null,
            null,
            CustomerStatus.ACTIVE,
            Instant.now());
    var command =
        new UpdateCustomerCommand(DocumentType.RUC, "20123456789", "Ana Perez", null, null, null);
    when(repository.findById(id)).thenReturn(Maybe.just(existing));

    // Act
    var result = service.update(id, command);

    // Assert
    result.test().assertError(IllegalArgumentException.class);
    verify(repository, never()).save(any(Customer.class));
  }

  @Test
  void createRejectsMissingTypeOrDocumentAndNormalizesOptionalValues() {
    // Arrange
    var missingType =
        new CreateCustomerCommand(
            null, DocumentType.DNI, "12345678", "Ana Perez", null, null, null);
    var missingDocumentType =
        new CreateCustomerCommand(
            CustomerType.PERSONAL, null, "12345678", "Ana Perez", null, null, null);
    var blankOptionalValues =
        new CreateCustomerCommand(
            CustomerType.PERSONAL, DocumentType.CEX, "ABC12345", "Ana Perez", " ", "", " ");
    when(repository.existsByDocument(DocumentType.CEX, "ABC12345")).thenReturn(Single.just(false));
    when(repository.save(any(Customer.class)))
        .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

    // Act
    var missingTypeException =
        assertThrows(IllegalArgumentException.class, () -> service.create(missingType));
    var missingDocumentException =
        assertThrows(IllegalArgumentException.class, () -> service.create(missingDocumentType));
    var cex = service.create(blankOptionalValues);

    // Assert
    assertNotNull(missingTypeException.getMessage());
    assertNotNull(missingDocumentException.getMessage());
    cex.test().assertComplete();
    verify(repository).existsByDocument(DocumentType.CEX, "ABC12345");
    verify(repository).save(any(Customer.class));
  }

  @Test
  void updateRejectsMissingDocumentTypeAndAcceptsCompatibleCex() {
    // Arrange
    UUID personalId = UUID.randomUUID();
    var personal =
        new Customer(
            personalId,
            CustomerType.PERSONAL,
            DocumentType.DNI,
            "12345678",
            "Ana Perez",
            null,
            null,
            null,
            CustomerStatus.ACTIVE,
            Instant.now());
    when(repository.findById(personalId)).thenReturn(Maybe.just(personal));
    var missingDocumentType =
        new UpdateCustomerCommand(null, "12345678", "Ana Perez", null, null, null);
    var compatibleCex =
        new UpdateCustomerCommand(DocumentType.CEX, "ABC12345", "Ana Perez", "", "", "");
    when(repository.save(any(Customer.class)))
        .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

    // Act
    var missingTypeResult = service.update(personalId, missingDocumentType);
    var cexResult = service.update(personalId, compatibleCex);

    // Assert
    missingTypeResult.test().assertError(IllegalArgumentException.class);
    cexResult.test().assertComplete();
    verify(repository).save(any(Customer.class));
  }

  @Test
  void rejectsMissingDocumentEmailLengthAndBusinessDni() {
    // Arrange
    var missingDocument =
        new CreateCustomerCommand(
            CustomerType.PERSONAL, DocumentType.DNI, null, "Ana Perez", null, null, null);
    var longEmail =
        new CreateCustomerCommand(
            CustomerType.PERSONAL,
            DocumentType.DNI,
            "12345678",
            "Ana Perez",
            "a".repeat(255) + "@example.com",
            null,
            null);
    var businessDni =
        new CreateCustomerCommand(
            CustomerType.BUSINESS, DocumentType.DNI, "12345678", "Empresa SAC", null, null, null);
    var missingName =
        new UpdateCustomerCommand(DocumentType.DNI, "12345678", null, null, null, null);

    // Act
    var missingDocumentException =
        assertThrows(IllegalArgumentException.class, () -> service.create(missingDocument));
    var longEmailException =
        assertThrows(IllegalArgumentException.class, () -> service.create(longEmail));
    var businessDniException =
        assertThrows(IllegalArgumentException.class, () -> service.create(businessDni));
    var missingNameException =
        assertThrows(
            IllegalArgumentException.class, () -> service.update(UUID.randomUUID(), missingName));

    // Assert
    assertNotNull(missingDocumentException.getMessage());
    assertNotNull(longEmailException.getMessage());
    assertNotNull(businessDniException.getMessage());
    assertEquals("name is required", missingNameException.getMessage());
    verify(repository, never()).existsByDocument(any(), any());
    verify(repository, never()).save(any(Customer.class));
  }

  @Test
  void rejectsInvalidDocumentCharactersAndAcceptsValidOptionalFields() {
    // Arrange
    var invalidCharacters =
        new CreateCustomerCommand(
            CustomerType.PERSONAL,
            DocumentType.CEX,
            "ABC-123",
            "Ana Perez",
            "ana@example.com",
            "+51 987654321",
            "Lima");
    var validOptionalFields =
        new CreateCustomerCommand(
            CustomerType.PERSONAL, DocumentType.DNI, "12345678", "Ana Perez", "", "", "");
    when(repository.existsByDocument(DocumentType.DNI, "12345678")).thenReturn(Single.just(false));
    when(repository.save(any(Customer.class)))
        .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

    // Act
    var invalidException =
        assertThrows(IllegalArgumentException.class, () -> service.create(invalidCharacters));
    var validOptionalObserver = service.create(validOptionalFields);

    // Assert
    assertNotNull(invalidException.getMessage());
    validOptionalObserver.test().assertComplete();
    verify(repository).existsByDocument(DocumentType.DNI, "12345678");
    verify(repository).save(any(Customer.class));
  }

  @Test
  void rejectsBlankDocumentAndInvalidPhoneAndDocumentLengths() {
    // Arrange
    var blankDocument =
        new CreateCustomerCommand(
            CustomerType.PERSONAL, DocumentType.DNI, " ", "Ana Perez", null, null, null);
    var shortDni =
        new CreateCustomerCommand(
            CustomerType.PERSONAL, DocumentType.DNI, "1234567", "Ana Perez", null, null, null);
    var shortRuc =
        new CreateCustomerCommand(
            CustomerType.BUSINESS, DocumentType.RUC, "201234", "Empresa SAC", null, null, null);
    var invalidPhone =
        new CreateCustomerCommand(
            CustomerType.PERSONAL, DocumentType.DNI, "12345678", "Ana Perez", null, "123", null);

    // Act
    assertThrows(IllegalArgumentException.class, () -> service.create(blankDocument));
    assertThrows(IllegalArgumentException.class, () -> service.create(shortDni));
    assertThrows(IllegalArgumentException.class, () -> service.create(shortRuc));
    assertThrows(IllegalArgumentException.class, () -> service.create(invalidPhone));

    // Assert
    verify(repository, never()).existsByDocument(any(), any());
    verify(repository, never()).save(any(Customer.class));
  }

  @Test
  void deleteMarksTheCustomerInactive() {
    // Arrange
    UUID id = UUID.randomUUID();
    var existing =
        new Customer(
            id,
            CustomerType.PERSONAL,
            DocumentType.DNI,
            "12345678",
            "Ana Perez",
            null,
            null,
            null,
            CustomerStatus.ACTIVE,
            Instant.now());
    when(repository.findById(id)).thenReturn(Maybe.just(existing));
    when(repository.save(any(Customer.class)))
        .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

    // Act
    var result = service.delete(id);

    // Assert
    result.test().assertComplete();

    var savedCustomer = ArgumentCaptor.forClass(Customer.class);
    verify(repository).save(savedCustomer.capture());
    assertEquals(CustomerStatus.INACTIVE, savedCustomer.getValue().status());
  }

  @Test
  void findByIdReturnsNotFoundWhenCustomerDoesNotExist() {
    // Arrange
    UUID id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Maybe.empty());

    // Act
    var result = service.findById(id);

    // Assert
    result.test().assertError(CustomerNotFoundException.class);
  }

  @Test
  void findByIdReturnsNotFoundForInactiveCustomer() {
    // Arrange
    UUID id = UUID.randomUUID();
    var inactive =
        new Customer(
            id,
            CustomerType.PERSONAL,
            DocumentType.DNI,
            "12345678",
            "Ana Perez",
            null,
            null,
            null,
            CustomerStatus.INACTIVE,
            Instant.now());
    when(repository.findById(id)).thenReturn(Maybe.just(inactive));

    // Act
    var result = service.findById(id);

    // Assert
    result.test().assertError(CustomerNotFoundException.class);
  }

  @Test
  void activateReactivatesInactiveCustomer() {
    // Arrange
    UUID id = UUID.randomUUID();
    var inactive =
        new Customer(
            id,
            CustomerType.PERSONAL,
            DocumentType.DNI,
            "12345678",
            "Ana Perez",
            null,
            null,
            null,
            CustomerStatus.INACTIVE,
            Instant.now());
    when(repository.findById(id)).thenReturn(Maybe.just(inactive));
    when(repository.save(any(Customer.class)))
        .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

    // Act
    var result = service.activate(id);

    // Assert
    var observer = result.test();
    observer.assertComplete();
    assertEquals(CustomerStatus.ACTIVE, observer.values().get(0).status());
    verify(repository).save(any(Customer.class));
  }

  @Test
  void findAllRejectsPageSizesOutsideTheConfiguredRange() {
    // Arrange
    // Act
    var result = service.findAll(null, 0, 101);

    // Assert
    var observer = ((io.reactivex.rxjava3.core.Flowable<Customer>) result).test();
    observer.assertError(IllegalArgumentException.class);

    verify(repository, never())
        .findAll(any(), any(CustomerStatus.class), any(Integer.class), any(Integer.class));
  }

  @Test
  void findAllRejectsNegativePageIndex() {
    // Arrange
    // Act
    var result = service.findAll(null, -1, 20);

    // Assert
    var observer = ((Flowable<Customer>) result).test();
    observer.assertError(IllegalArgumentException.class);
    verify(repository, never())
        .findAll(any(), any(CustomerStatus.class), any(Integer.class), any(Integer.class));
  }

  @Test
  void findAllRejectsZeroPageSize() {
    // Arrange
    // Act
    var result = service.findAll(null, 0, 0);

    // Assert
    ((Flowable<Customer>) result).test().assertError(IllegalArgumentException.class);
    verify(repository, never())
        .findAll(any(), any(CustomerStatus.class), any(Integer.class), any(Integer.class));
  }

  @Test
  void findAllAcceptsMaximumConfiguredPageSize() {
    // Arrange
    when(repository.findAll(null, CustomerStatus.ACTIVE, 0, 100)).thenReturn(Flowable.empty());

    // Act
    var result = service.findAll(null, 0, 100);

    // Assert
    ((Flowable<Customer>) result).test().assertComplete();
    verify(repository).findAll(null, CustomerStatus.ACTIVE, 0, 100);
  }

  @Test
  void findAllDelegatesValidPaginationAndFilter() {
    // Arrange
    when(repository.findAll(CustomerType.PERSONAL, CustomerStatus.ACTIVE, 1, 25))
        .thenReturn(Flowable.empty());

    // Act
    var result = service.findAll(CustomerType.PERSONAL, 1, 25);

    // Assert
    ((Flowable<Customer>) result).test().assertComplete();

    verify(repository).findAll(CustomerType.PERSONAL, CustomerStatus.ACTIVE, 1, 25);
  }

  @Test
  void findAllDefaultsToActiveWhenStatusIsNull() {
    // Arrange
    when(repository.findAll(null, CustomerStatus.ACTIVE, 0, 20)).thenReturn(Flowable.empty());

    // Act
    var result = service.findAll(null, null, 0, 20);

    // Assert
    ((Flowable<Customer>) result).test().assertComplete();
    verify(repository).findAll(null, CustomerStatus.ACTIVE, 0, 20);
  }

  @Test
  void findAllCanFilterInactiveAndAllCustomers() {
    // Arrange
    when(repository.findAll(null, CustomerStatus.INACTIVE, 0, 20)).thenReturn(Flowable.empty());
    when(repository.findAll(null, CustomerStatus.ALL, 0, 20)).thenReturn(Flowable.empty());

    // Act
    var inactive = service.findAll(null, CustomerStatus.INACTIVE, 0, 20);
    var all = service.findAll(null, CustomerStatus.ALL, 0, 20);

    // Assert
    ((Flowable<Customer>) inactive).test().assertComplete();
    ((Flowable<Customer>) all).test().assertComplete();
    verify(repository).findAll(null, CustomerStatus.INACTIVE, 0, 20);
    verify(repository).findAll(null, CustomerStatus.ALL, 0, 20);
  }

  @Test
  void acceptsEmailAtMaximumSupportedLength() {
    // Arrange
    String email = "a".repeat(242) + "@example.com";
    var command =
        new CreateCustomerCommand(
            CustomerType.PERSONAL, DocumentType.DNI, "12345678", "Ana Perez", email, null, null);
    when(repository.existsByDocument(DocumentType.DNI, "12345678")).thenReturn(Single.just(false));
    when(repository.save(any(Customer.class)))
        .thenAnswer(invocation -> Single.just(invocation.getArgument(0)));

    // Act
    var result = service.create(command);

    // Assert
    result.test().assertComplete().assertValue(customer -> email.equals(customer.email()));
  }

  @Test
  void deletePropagatesRepositoryFailure() {
    // Arrange
    UUID id = UUID.randomUUID();
    var customer =
        new Customer(
            id,
            CustomerType.PERSONAL,
            DocumentType.DNI,
            "12345678",
            "Ana Perez",
            null,
            null,
            null,
            CustomerStatus.ACTIVE,
            Instant.now());
    when(repository.findById(id)).thenReturn(Maybe.just(customer));
    when(repository.save(any(Customer.class)))
        .thenReturn(Single.error(new RuntimeException("database failed")));

    // Act
    var result = service.delete(id);

    // Assert
    result.test().assertError(RuntimeException.class);
  }

  @Test
  void activatePropagatesRepositoryFailure() {
    // Arrange
    UUID id = UUID.randomUUID();
    var customer =
        new Customer(
            id,
            CustomerType.PERSONAL,
            DocumentType.DNI,
            "12345678",
            "Ana Perez",
            null,
            null,
            null,
            CustomerStatus.INACTIVE,
            Instant.now());
    when(repository.findById(id)).thenReturn(Maybe.just(customer));
    when(repository.save(any(Customer.class)))
        .thenReturn(Single.error(new RuntimeException("database failed")));

    // Act
    var result = service.activate(id);

    // Assert
    result.test().assertError(RuntimeException.class);
  }
}
