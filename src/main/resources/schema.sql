CREATE TABLE IF NOT EXISTS customers (
    id UUID PRIMARY KEY,
    customer_type VARCHAR(16) NOT NULL,
    document_type VARCHAR(16) NOT NULL,
    document_number VARCHAR(20) NOT NULL,
    name VARCHAR(150) NOT NULL,
    email VARCHAR(254),
    phone VARCHAR(32),
    address VARCHAR(500),
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_customers_document UNIQUE (document_type, document_number),
    CONSTRAINT ck_customers_type CHECK (customer_type IN ('PERSONAL', 'BUSINESS')),
    CONSTRAINT ck_customers_document_type CHECK (document_type IN ('DNI', 'CEX', 'PASSPORT', 'RUC')),
    CONSTRAINT ck_customers_document_number CHECK (
        document_number = BTRIM(document_number)
        AND document_number <> ''
        AND document_number ~ '^[A-Z0-9]{6,20}$'
    ),
    CONSTRAINT ck_customers_name CHECK (name = BTRIM(name) AND name <> '' AND CHAR_LENGTH(name) <= 150),
    CONSTRAINT ck_customers_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_customers_document_compatibility CHECK (
        (customer_type = 'PERSONAL' AND document_type IN ('DNI', 'CEX', 'PASSPORT'))
        OR (customer_type = 'BUSINESS' AND document_type = 'RUC')
    )
);

CREATE INDEX IF NOT EXISTS ix_customers_created_id ON customers (created_at, id);
