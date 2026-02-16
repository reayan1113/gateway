package com.example.api_gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApiGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiGatewayApplication.class, args);
	}

	@org.springframework.context.annotation.Bean
	public org.springframework.boot.CommandLineRunner logRoutes(
			org.springframework.cloud.gateway.route.RouteLocator routeLocator) {
		return args -> {
			System.out.println(
					"========================================================================================");
			System.out.println(
					"                            LOADED GATEWAY ROUTES                                       ");
			System.out.println(
					"========================================================================================");
			routeLocator.getRoutes().subscribe(route -> {
				System.out.println("Route ID: " + route.getId());
				System.out.println("   URI: " + route.getUri());
				System.out.println("   Predicate: " + route.getPredicate());
				System.out.println("   Filters: " + route.getFilters());
				System.out.println(
						"----------------------------------------------------------------------------------------");
			});
		};
	}

}
