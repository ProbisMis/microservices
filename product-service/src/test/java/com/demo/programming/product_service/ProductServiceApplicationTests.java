package com.demo.programming.product_service;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.demo.programming.product_service.dto.ProductRequest;
import com.demo.programming.product_service.kafka.producer.ProductEventProducer;
import com.demo.programming.product_service.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@EnabledIf("isDockerAvailable")
class ProductServiceApplicationTests {

	static boolean isDockerAvailable() {
		try {
			return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
		} catch (Throwable ex) {
			return false;
		}
	}

	@Container
	static MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:4.4.2");
	
	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ProductRepository productRepository;

	@MockBean
	private ProductEventProducer productEventProducer;

	@DynamicPropertySource
	static void setProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
	}

	@Test
	void testCreateProduct() throws Exception {
		ProductRequest productRequest = getProductRequest();
		String productRequestJson = objectMapper.writeValueAsString(productRequest);
		// Test implementation for creating a product
		mockMvc.perform(MockMvcRequestBuilders.post("/api/product")
		.contentType(MediaType.APPLICATION_JSON)
		.content(productRequestJson))
		.andExpect(status().isCreated());
		assert(productRepository.findAll().size() == 1);
	}

	@Test
	void testGetAllProducts() throws Exception {
		// Test implementation for retrieving all products
		mockMvc.perform(MockMvcRequestBuilders.get("/api/product")
		.contentType(MediaType.APPLICATION_JSON))
		.andExpect(status().isOk());
	}

	private ProductRequest getProductRequest() {
		return ProductRequest.builder()
				.name("iPhone 13")
				.description("Latest Apple iPhone model")
				.price(BigDecimal.valueOf(999.99))
				.build();	
	}
	
	//TODO: Add more tests as needed

}
