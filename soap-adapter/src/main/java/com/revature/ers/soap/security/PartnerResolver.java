package com.revature.ers.soap.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.ws.transport.context.TransportContext;
import org.springframework.ws.transport.context.TransportContextHolder;
import org.springframework.ws.transport.http.HttpServletConnection;

import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.security.cert.X509Certificate;
import java.util.Optional;

/**
 * Answers "which partner is calling?" from the mTLS handshake - not from anything in the
 * message. By the time this code runs, Tomcat's JSSE layer has already VERIFIED the client
 * certificate chains to our CA (server.ssl.client-auth=need); the servlet container exposes
 * the verified chain as a request attribute, and the partner's name is the leaf cert's CN.
 * The application never checks a password or parses a token: possession of a CA-signed
 * private key, proven cryptographically during the handshake, IS the authentication.
 *
 * Empty means "no client certificate on this connection" - true on the plain-HTTP dev
 * profile, impossible on the mtls profile (the handshake would have failed first).
 */
@Component
public class PartnerResolver {

    /** The Servlet-spec attribute where the container parks the verified client chain. */
    private static final String CLIENT_CERT_ATTRIBUTE = "jakarta.servlet.request.X509Certificate";

    public Optional<String> currentPartner() {
        TransportContext context = TransportContextHolder.getTransportContext();
        if (context == null || !(context.getConnection() instanceof HttpServletConnection connection)) {
            return Optional.empty();   // not an HTTP transport (e.g. MockWebServiceClient in tests)
        }
        HttpServletRequest request = connection.getHttpServletRequest();
        if (!(request.getAttribute(CLIENT_CERT_ATTRIBUTE) instanceof X509Certificate[] chain) || chain.length == 0) {
            return Optional.empty();   // plain HTTP - no handshake identity exists
        }
        return commonNameOf(chain[0]); // chain[0] is the LEAF (the partner); [1..] its issuers
    }

    /** Pull CN out of an X.500 subject like "CN=acme" (RDN parsing, not string splitting). */
    private static Optional<String> commonNameOf(X509Certificate certificate) {
        try {
            LdapName subject = new LdapName(certificate.getSubjectX500Principal().getName());
            return subject.getRdns().stream()
                    .filter(rdn -> "CN".equalsIgnoreCase(rdn.getType()))
                    .map(Rdn::getValue).map(Object::toString)
                    .findFirst();
        } catch (javax.naming.InvalidNameException e) {
            return Optional.empty();
        }
    }
}
