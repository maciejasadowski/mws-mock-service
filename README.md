# MWS Mock SOAP Service

A fully functional Spring Boot + JAXB mock SOAP service for testing applications that consume the `MWSProcessServiceBasic` WSDL.

## Features

? **All 49 SOAP Operations** - Complete implementation of MWSProcessServiceBasic interface  
? **JAXB Code Generation** - Automatic Java class generation from WSDL  
? **Spring Boot** - Lightweight, production-ready framework  
? **WSDL Serving** - Clients can download and generate code from the mock service  
? **Request Logging** - All SOAP requests logged automatically  
? **Easy Customization** - Simple to modify responses for specific test scenarios

## Quick Start

### Prerequisites
- Java 11+
- Maven 3.6+

### 1. Generate Java Classes from WSDL

```bash
mvn clean generate-sources