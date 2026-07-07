package com.revature.ers.soap.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

/**
 * Spring-WS wiring. SOAP is not REST-over-XML: requests are dispatched by PAYLOAD ROOT ELEMENT
 * (qname), not by URL + verb - every partner POSTs to /ws and the MessageDispatcherServlet
 * routes to the @Endpoint whose @PayloadRoot matches the body's root element.
 */
@Configuration
@EnableWs
public class WebServiceConfig {

    /** All SOAP traffic enters through /ws/* via Spring-WS's own dispatcher servlet. */
    @Bean
    public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(ApplicationContext context) {
        MessageDispatcherServlet servlet = new MessageDispatcherServlet();
        servlet.setApplicationContext(context);
        // rewrites the WSDL's soap:address to whatever host/port served it
        servlet.setTransformWsdlLocations(true);
        return new ServletRegistrationBean<>(servlet, "/ws/*");
    }

    /**
     * Serves the machine-readable contract at GET /ws/requests.wsdl, generated at runtime from
     * the SAME requests.xsd that xjc generated the payload classes from - contract-first means
     * one schema drives both sides.
     */
    @Bean(name = "requests")
    public DefaultWsdl11Definition requestsWsdl(XsdSchema requestsSchema) {
        DefaultWsdl11Definition wsdl = new DefaultWsdl11Definition();
        wsdl.setPortTypeName("ReimbursementRequestsPort");
        wsdl.setLocationUri("/ws");
        wsdl.setTargetNamespace("http://revature.com/ers/soap/requests");
        wsdl.setSchema(requestsSchema);
        return wsdl;
    }

    @Bean
    public XsdSchema requestsSchema() {
        return new SimpleXsdSchema(new ClassPathResource("requests.xsd"));
    }

    /** Declares the topic so KafkaAdmin creates it on first connect (idempotent if it exists). */
    @Bean
    public NewTopic requestSubmittedTopic(@Value("${ers.kafka.topic.request-submitted}") String topic) {
        return TopicBuilder.name(topic).partitions(1).replicas(1).build();
    }
}
