package com.revature.ers.soap.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The partner-auth stack over a REAL TLS handshake: a throwaway CA + certs are generated at
 * runtime with the JDK's own keytool (nothing committed - private keys never enter git), the
 * server runs with client-auth=need, and the four outcomes are pinned:
 *
 *   no client cert          -> the HANDSHAKE fails; the request never reaches the app
 *   globex (authenticated,
 *     empty allowlist)      -> accepted=false: a valid cert proves WHO, never WHAT
 *   acme, user off its list -> accepted=false: per-user authorization
 *   acme, allowed user      -> accepted=true (through to the Kafka ack, embedded broker)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "ers.partners.allowed-user-ids.acme=5",
        "ers.partners.allowed-user-ids.globex="
})
@EmbeddedKafka(partitions = 1, topics = "reimbursement.request.submitted")
class MutualTlsPartnerAuthTest {

    private static final String PASS = "changeit";
    private static Path certDir;

    @LocalServerPort
    private int port;

    /** Runs during context configuration: mint the PKI, then point server.ssl at it. */
    @DynamicPropertySource
    static void mintCertificatesAndConfigureTls(DynamicPropertyRegistry registry) throws Exception {
        certDir = Files.createTempDirectory("ers-mtls-test");
        // the CA: a self-signed keypair marked as able to sign (BasicConstraints CA:true)
        keytool("-genkeypair", "-alias", "ca", "-dname", "CN=Test Partner CA", "-ext", "bc:c",
                "-keyalg", "RSA", "-validity", "1", "-keystore", store("ca.p12"));
        keytool("-exportcert", "-alias", "ca", "-keystore", store("ca.p12"), "-file", path("ca.crt"));
        // the server's identity, CA-signed, with the SANs clients actually verify
        issue("server", "CN=localhost", "san=dns:localhost,ip:127.0.0.1");
        // two partners: their CN is the name the allowlist keys on
        issue("acme", "CN=acme", null);
        issue("globex", "CN=globex", null);
        // the server's truststore holds ONLY the CA - that one entry defines who counts as a partner
        keytool("-importcert", "-noprompt", "-alias", "ca", "-file", path("ca.crt"),
                "-keystore", store("truststore.p12"));

        registry.add("server.ssl.enabled", () -> "true");
        registry.add("server.ssl.key-store", () -> path("server.p12"));
        registry.add("server.ssl.key-store-type", () -> "PKCS12");
        registry.add("server.ssl.key-store-password", () -> PASS);
        registry.add("server.ssl.client-auth", () -> "need");
        registry.add("server.ssl.trust-store", () -> path("truststore.p12"));
        registry.add("server.ssl.trust-store-type", () -> "PKCS12");
        registry.add("server.ssl.trust-store-password", () -> PASS);
    }

    /** keypair -> CSR -> CA signs -> import chain back: the same dance scripts/gen-partner-certs.sh does with openssl. */
    private static void issue(String alias, String dn, String san) throws Exception {
        keytool("-genkeypair", "-alias", alias, "-dname", dn, "-keyalg", "RSA", "-validity", "1",
                "-keystore", store(alias + ".p12"));
        keytool("-certreq", "-alias", alias, "-keystore", store(alias + ".p12"), "-file", path(alias + ".csr"));
        if (san != null) {
            keytool("-gencert", "-alias", "ca", "-keystore", store("ca.p12"), "-validity", "1",
                    "-ext", san, "-infile", path(alias + ".csr"), "-outfile", path(alias + ".crt"));
        } else {
            keytool("-gencert", "-alias", "ca", "-keystore", store("ca.p12"), "-validity", "1",
                    "-infile", path(alias + ".csr"), "-outfile", path(alias + ".crt"));
        }
        keytool("-importcert", "-noprompt", "-alias", "ca", "-file", path("ca.crt"),
                "-keystore", store(alias + ".p12"));
        keytool("-importcert", "-alias", alias, "-file", path(alias + ".crt"),
                "-keystore", store(alias + ".p12"));
    }

    private static void keytool(String... args) throws Exception {
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        // Every store op needs a password; without -storepass/-keypass keytool BLOCKS on an
        // interactive stdin prompt (the whole store is passphrase-protected). -keypass is
        // harmless on ops that ignore it, so pass both unconditionally.
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add(keytool);
        command.addAll(java.util.List.of(args));
        command.addAll(java.util.List.of("-storepass", PASS, "-keypass", PASS));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        process.getOutputStream().close();   // never wait on our input
        String output = new String(process.getInputStream().readAllBytes());
        if (process.waitFor() != 0) {
            throw new IllegalStateException("keytool failed: " + String.join(" ", args) + "\n" + output);
        }
    }

    private static String path(String name) {
        return certDir.resolve(name).toString();
    }

    /** -keystore path plus the passwords every store operation needs, in one place. */
    private static String store(String name) {
        return path(name);
    }

    // -------------------------------------------------------------------------------------

    private HttpClient clientWith(String partnerKeystore) throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        KeyStore trust = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(path("truststore.p12"))) {
            trust.load(in, PASS.toCharArray());
        }
        tmf.init(trust);

        KeyManagerFactory kmf = null;
        if (partnerKeystore != null) {
            KeyStore identity = KeyStore.getInstance("PKCS12");
            try (FileInputStream in = new FileInputStream(path(partnerKeystore))) {
                identity.load(in, PASS.toCharArray());
            }
            kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(identity, PASS.toCharArray());
        }

        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(kmf == null ? null : kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        return HttpClient.newBuilder().sslContext(ssl).build();
    }

    private HttpResponse<String> submitAs(String partnerKeystore, int requesterUserId) throws Exception {
        String envelope = """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                                  xmlns:req="http://revature.com/ers/soap/requests">
                  <soapenv:Body>
                    <req:submitReimbursementRequest>
                      <req:requesterUserId>%d</req:requesterUserId>
                      <req:amount>12.34</req:amount>
                      <req:eventDate>2026-07-01</req:eventDate>
                      <req:eventLocationId>1</req:eventLocationId>
                      <req:requestedEvent>mTLS Auth Test</req:requestedEvent>
                    </req:submitReimbursementRequest>
                  </soapenv:Body>
                </soapenv:Envelope>""".formatted(requesterUserId);
        return clientWith(partnerKeystore).send(
                HttpRequest.newBuilder(URI.create("https://localhost:" + port + "/ws"))
                        .header("Content-Type", "text/xml")
                        .POST(HttpRequest.BodyPublishers.ofString(envelope)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void withoutAClientCertificate_theHandshakeItselfIsRefused() {
        // client-auth=need: Tomcat aborts the connection during the handshake - the request
        // never reaches a single line of application code
        assertThrows(IOException.class, () -> submitAs(null, 5));
    }

    @Test
    void authenticatedPartnerWithEmptyAllowlist_isRejected() throws Exception {
        HttpResponse<String> response = submitAs("globex.p12", 5);
        assertEquals(200, response.statusCode());   // the SOAP answer carries the rejection
        assertTrue(response.body().contains("<ns2:accepted>false</ns2:accepted>"),
                "a valid certificate proves WHO, never WHAT: " + response.body());
        assertTrue(response.body().contains("not permitted for partner globex"));
    }

    @Test
    void allowedPartner_submittingForAUserOffItsList_isRejected() throws Exception {
        HttpResponse<String> response = submitAs("acme.p12", 3);   // acme's list is only [5]
        assertTrue(response.body().contains("<ns2:accepted>false</ns2:accepted>"));
        assertTrue(response.body().contains("requester 3 is not permitted for partner acme"));
    }

    @Test
    void allowedPartnerAndUser_isAcceptedEndToEnd() throws Exception {
        HttpResponse<String> response = submitAs("acme.p12", 5);
        assertTrue(response.body().contains("<ns2:accepted>true</ns2:accepted>"),
                "handshake + allowlist passed -> queued: " + response.body());
    }
}
