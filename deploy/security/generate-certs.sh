#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CERT_DIR="${CERT_DIR:-$SCRIPT_DIR/certs}"
DAYS_VALID="${DAYS_VALID:-3650}"

generate_ca() {
    local ca_key="$CERT_DIR/ca-key.pem"
    local ca_cert="$CERT_DIR/ca-cert.pem"

    echo "=== Generating CA Certificate ==="
    mkdir -p "$CERT_DIR"

    openssl genrsa -out "$ca_key" 4096 2>/dev/null
    openssl req -x509 -new -nodes -key "$ca_key -sha256 -days $DAYS_VALID -out $ca_cert" \
        -subj "/CN=FIS-Engine CA/O=Bracit/OU=Security" 2>/dev/null

    echo "CA generated: $ca_cert"
}

generate_server_cert() {
    local service_name="$1"
    local cn="$2"
    local alt_names="${3:-}"

    local key="$CERT_DIR/${service_name}-key.pem"
    local csr="$CERT_DIR/${service_name}.csr"
    local cert="$CERT_DIR/${service_name}-cert.pem"

    echo "=== Generating Server Certificate for $service_name ==="

    openssl genrsa -out "$key" 2048 2>/dev/null

    local ext_file="$CERT_DIR/${service_name}.ext"
    cat > "$ext_file" <<EOF
[v3_req]
keyUsage = keyEncipherment, dataEncipherment, digitalSignature
extendedKeyUsage = serverAuth
basicConstraints = CA:FALSE
subjectAltName = ${alt_names}
EOF

    openssl req -new -key "$key" -out "$csr" \
        -subj "/CN=${cn}/O=Bracit/OU=${service_name}" 2>/dev/null

    openssl x509 -req -in "$csr" -CA "$CERT_DIR/ca-cert.pem" -CAkey "$CERT_DIR/ca-key.pem" \
        -CAcreateserial -out "$cert" -days $DAYS_VALID -sha256 \
        -extfile "$ext_file" 2>/dev/null

    rm -f "$csr" "$ext_file"

    echo "Server certificate generated: $cert"
}

generate_client_cert() {
    local client_name="$1"

    local key="$CERT_DIR/${client_name}-key.pem"
    local cert="$CERT_DIR/${client_name}-cert.pem"

    echo "=== Generating Client Certificate for $client_name ==="

    openssl genrsa -out "$key" 2048 2>/dev/null

    openssl req -new -key "$key" -out "$CERT_DIR/${client_name}.csr" \
        -subj "/CN=${client_name}/O=Bracit/OU=Clients" 2>/dev/null

    openssl x509 -req -in "$CERT_DIR/${client_name}.csr" \
        -CA "$CERT_DIR/ca-cert.pem" -CAkey "$CERT_DIR/ca-key.pem" \
        -CAcreateserial -out "$cert" -days $DAYS_VALID -sha256 \
        -extfile <(cat <<EOF
[v3_req]
keyUsage = digitalSignature
extendedKeyUsage = clientAuth
basicConstraints = CA:FALSE
EOF
) 2>/dev/null

    rm -f "$CERT_DIR/${client_name}.csr"

    echo "Client certificate generated: $cert"
}

create_keystore() {
    local service_name="$1"
    local keystore="$CERT_DIR/${service_name}.p12"

    echo "=== Creating PKCS12 Keystore for $service_name ==="

    openssl pkcs12 -export -out "$keystore" \
        -inkey "$CERT_DIR/${service_name}-key.pem" \
        -in "$CERT_DIR/${service_name}-cert.pem" \
        -certfile "$CERT_DIR/ca-cert.pem" \
        -password pass:changeit 2>/dev/null

    echo "Keystore created: $keystore"
}

create_truststore() {
    local truststore="$CERT_DIR/truststore.p12"

    echo "=== Creating Truststore ==="

    keytool -importcert -alias ca -file "$CERT_DIR/ca-cert.pem" \
        -keystore "$truststore" -storepass changeit -noprompt 2>/dev/null

    echo "Truststore created: $truststore"
}

if [ "${1:-}" == "generate" ]; then
    mkdir -p "$CERT_DIR"

    generate_ca
    generate_server_cert "fis-app" "fis-app" "DNS:localhost,IP:127.0.0.1"
    generate_server_cert "postgres" "postgres" "DNS:localhost,IP:127.0.0.1"
    generate_server_cert "redis" "redis" "DNS:localhost,IP:127.0.0.1"
    generate_server_cert "rabbitmq" "rabbitmq" "DNS:localhost,IP:127.0.0.1"

    generate_client_cert "fis-client"

    create_truststore

    create_keystore "fis-app"
    create_keystore "postgres"

    echo ""
    echo "=== Certificate Generation Complete ==="
    echo "CA Certificate: $CERT_DIR/ca-cert.pem"
    echo "App Keystore: $CERT_DIR/fis-app.p12"
    echo "Truststore: $CERT_DIR/truststore.p12"
    echo ""
    echo "To configure mTLS in application.yml:"
    echo "  server.ssl.enabled: true"
    echo "  server.ssl.key-store: $CERT_DIR/fis-app.p12"
    echo "  server.ssl.trust-store: $CERT_DIR/truststore.p12"
fi

if [ "${1:-}" == "verify" ]; then
    echo "=== Verifying Certificates ==="
    for cert in "$CERT_DIR"/*-cert.pem; do
        if [ -f "$cert" ]; then
            echo "--- $cert ---"
            openssl x509 -in "$cert" -noout -subject -issuer -dates 2>/dev/null || true
            echo ""
        fi
    done
fi