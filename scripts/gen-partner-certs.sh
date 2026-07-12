#!/usr/bin/env bash
# ============================================================================
# Generates the local mTLS material for the SOAP partner endpoint:
#
#   certs/ca.crt / ca.key             our own Certificate Authority - the trust ROOT.
#                                     "A partner is whoever holds a cert THIS key signed."
#   certs/server-keystore.p12         the adapter's identity (CN=localhost + SANs) - what
#                                     the SERVER presents so partners can verify who THEY
#                                     are talking to (the ordinary half of TLS).
#   certs/partner-truststore.p12      the CA cert alone. The server trusts client certs
#                                     that CHAIN to this - it never needs each partner's
#                                     cert individually; issuing a cert IS onboarding.
#   certs/acme.p12 / globex.p12       two partner identities (CN=acme, CN=globex). The CN
#                                     is the partner's NAME: the adapter reads it from the
#                                     handshake-verified cert and looks up the allowlist.
#   certs/acme.crt+key, globex.*      the same identities as PEM, for curl --cert/--key.
#
# EVERYTHING UNDER certs/ IS GITIGNORED - private keys never enter git history (we scrubbed
# this repo's history once; never again). Re-run this script anywhere, any time: the trust
# root is disposable and local. Production would use a real internal CA / ACME and rotate.
#
# Why mutual TLS at all: ordinary TLS authenticates only the SERVER. client-auth=need makes
# the server send a CertificateRequest during the handshake; a client that cannot present a
# cert signed by our CA never completes the connection - authentication happens BELOW the
# application, before one byte of SOAP is read.
# ============================================================================
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p certs && cd certs

PASS=changeit   # local dev material only - the keys themselves never leave this machine

# --- 1. The CA: a self-signed root. basicConstraints CA:TRUE marks it as ABLE to sign. ----
openssl req -x509 -newkey rsa:2048 -nodes -days 365 \
  -keyout ca.key -out ca.crt \
  -subj "/CN=ERS Partner CA" \
  -addext "basicConstraints=critical,CA:TRUE" \
  -addext "keyUsage=critical,keyCertSign"

# --- 2. The server: key + CSR, signed by the CA. SANs are what clients actually verify. ---
openssl req -newkey rsa:2048 -nodes -keyout server.key -out server.csr -subj "/CN=localhost"
openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial -days 365 \
  -out server.crt \
  -extfile <(printf "subjectAltName=DNS:localhost,DNS:soap-adapter,IP:127.0.0.1")
# Tomcat wants a keystore holding the private key + its chain:
openssl pkcs12 -export -inkey server.key -in server.crt -certfile ca.crt \
  -name server -out server-keystore.p12 -passout pass:$PASS

# --- 3. The truststore: just the CA. This defines WHO COUNTS as a partner. ----------------
keytool -importcert -noprompt -alias ers-partner-ca -file ca.crt \
  -keystore partner-truststore.p12 -storetype PKCS12 -storepass $PASS

# --- 4. Partner identities. The CN is the name the allowlist keys on. ---------------------
for partner in acme globex; do
  openssl req -newkey rsa:2048 -nodes -keyout $partner.key -out $partner.csr -subj "/CN=$partner"
  openssl x509 -req -in $partner.csr -CA ca.crt -CAkey ca.key -CAcreateserial -days 365 \
    -out $partner.crt
  openssl pkcs12 -export -inkey $partner.key -in $partner.crt -certfile ca.crt \
    -name $partner -out $partner.p12 -passout pass:$PASS
done

rm -f server.csr acme.csr globex.csr ca.srl
echo
echo "Done. Try the handshake yourself:"
echo "  run:    cd soap-adapter && SPRING_PROFILES_ACTIVE=mtls mvn spring-boot:run   (or java -jar with the profile)"
echo "  watch:  openssl s_client -connect localhost:8443 -CAfile certs/ca.crt        <- see the CertificateRequest"
echo "  call:   curl --cacert certs/ca.crt --cert certs/acme.crt --key certs/acme.key https://localhost:8443/ws/requests.wsdl"
