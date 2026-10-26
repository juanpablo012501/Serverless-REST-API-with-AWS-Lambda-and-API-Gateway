# Orders API — Serverless REST API con AWS Lambda y API Gateway

API REST de gestión de órdenes construida con **Java 17**, **Maven**, **AWS Lambda** y **Amazon API Gateway**.

El objetivo del taller no es solo obtener endpoints funcionando, sino analizar **dónde debe vivir cada responsabilidad**: en API Gateway, en el adaptador Lambda/HTTP o en la lógica de aplicación.
 
---

## Tabla de contenido

1. [Diseño de la API](#1-diseño-de-la-api)
2. [Parte I — Aplicación en Java puro](#2-parte-i--aplicación-en-java-puro)
3. [Parte II — Despliegue en AWS Lambda](#3-parte-ii--despliegue-en-aws-lambda)
4. [Parte III y IV — API Gateway con mapping templates](#4-parte-iii-y-iv--api-gateway-con-mapping-templates)
5. [Parte V — Lambda proxy integration](#5-parte-v--lambda-proxy-integration)
6. [Comparación de arquitecturas](#6-comparación-de-arquitecturas)
7. [Desarrollo asistido por IA](#7-desarrollo-asistido-por-ia)
8. [Extensión: DELETE /orders/{id}](#8-extensión-delete-ordersid)
9. [Arquitectura final](#9-arquitectura-final)
10. [Cómo compilar y probar](#10-cómo-compilar-y-probar)
---

## 1. Diseño de la API

### Modelo

```json
{
  "id": "ORD-001",
  "customerId": "CUST-42",
  "product": "MacBook Air",
  "quantity": 1
}
```

### Contrato

| Operación | Método y URL | Path params | Query params | Body de entrada | Éxito | Errores |
|---|---|---|---|---|---|---|
| Crear orden | `POST /orders` | — | — | `{customerId, product, quantity}` | **201 Created** + header `Location: /orders/{id}` + orden creada | **400** datos inválidos |
| Obtener orden | `GET /orders/{id}` | `id` | — | — | **200 OK** + orden | **404** no existe |
| Buscar por cliente | `GET /orders?customerId={customerId}` | — | `customerId` (obligatorio) | — | **200 OK** + lista de órdenes (puede ser `[]`) | **400** falta `customerId` |
| Eliminar orden | `DELETE /orders/{id}` | `id` | — | — | **204 No Content** (sin body) | **404** no existe |
| Cualquiera | — | — | — | — | — | **500** error inesperado |

### Formato de error

Todas las respuestas de error usan la misma estructura:

```json
{
  "error": "ORDER_NOT_FOUND",
  "message": "Order ORD-999 does not exist"
}
```

| Código `error` | HTTP | Cuándo |
|---|---|---|
| `INVALID_REQUEST` | 400 | Falta un campo obligatorio, `quantity <= 0`, body mal formado o falta `customerId` en la búsqueda |
| `ORDER_NOT_FOUND` | 404 | El `id` solicitado no existe |
| `INTERNAL_ERROR` | 500 | Cualquier error no previsto (sin exponer detalles internos) |

### Preguntas de diseño

**¿Cuáles son los recursos de la API?**
Hay un recurso principal, la orden, expuesto de dos formas: la **colección** de órdenes (`/orders`) y cada **orden individual** (`/orders/{id}`). Las operaciones se expresan con métodos HTTP sobre esos recursos, no con verbos en la URL (no existe `/createOrder` ni `/getOrder`).

**¿Por qué `/orders/{id}` para obtener una orden?**
Porque el `id` identifica de forma única a un recurso. La ruta se lee como "la orden `{id}` dentro de la colección de órdenes", y esa misma URL sirve para operar sobre ese recurso con otros métodos (`DELETE /orders/{id}`). Además, es la URL que se devuelve en el header `Location` al crear una orden.

**¿Por qué `customerId` es un query parameter en la búsqueda?**
Porque no identifica un recurso nuevo: **filtra** la colección `/orders`. El resultado sigue siendo una colección de órdenes. Los query parameters son la forma natural de expresar criterios de filtrado opcionales o combinables (por ejemplo, en el futuro `?customerId=CUST-42&product=Mouse`). Una ruta como `/customers/{id}/orders` también sería válida, pero implicaría modelar al cliente como recurso, lo cual no es necesario en este alcance.

**¿Qué debe devolver `POST /orders` cuando la orden se crea correctamente?**
`201 Created`, el body con la orden creada (incluyendo el `id` generado por el servidor) y el header `Location: /orders/{id}` indicando dónde quedó el nuevo recurso.

**¿Qué debe devolver la API cuando una orden no existe?**
`404 Not Found` con el JSON de error (`ORDER_NOT_FOUND`) y un mensaje que indique qué `id` no se encontró. No se debe responder `200` con body vacío ni `null`, porque el cliente no podría distinguir el error usando HTTP.

**¿Qué información debe contener una respuesta de error?**
- Un **código estable** (`error`) que un programa cliente pueda interpretar sin depender del texto.
- Un **mensaje legible** (`message`) para humanos que explique el problema.
- **No** debe contener stack traces, nombres de clases ni detalles internos de la infraestructura.
### Nota sobre persistencia

No se usa base de datos. Las órdenes se guardan en una colección en memoria (`InMemoryOrderRepository`), precargada con datos de ejemplo:

| id | customerId | product | quantity |
|---|---|---|---|
| ORD-001 | CUST-42 | MacBook Air | 1 |
| ORD-002 | CUST-42 | Mouse | 2 |
| ORD-003 | CUST-7 | Keyboard | 2 |

Los entornos de ejecución de Lambda son **efímeros**: la memoria se pierde cuando AWS recicla el entorno, y cada función Lambda tiene su propia memoria. Por eso una orden creada con `POST` puede no ser visible desde la Lambda que atiende `GET`. Los datos precargados permiten demostrar el comportamiento de lectura de forma predecible. En un sistema real, el repositorio en memoria se reemplazaría por una implementación persistente (por ejemplo DynamoDB) sin cambiar `OrderService`.
 
---

## 2. Parte I — Aplicación en Java puro

### Estructura

```
orders-api/
├── pom.xml
├── README.md
└── src/
    ├── main/java/com/workshop/orders/
    │   ├── domain/
    │   │   ├── Order.java
    │   │   ├── CreateOrderRequest.java
    │   │   ├── OrderNotFoundException.java
    │   │   └── InvalidOrderException.java
    │   └── application/
    │       ├── OrderRepository.java
    │       ├── InMemoryOrderRepository.java
    │       └── OrderService.java
    └── test/java/com/workshop/orders/application/
        └── OrderServiceTest.java
```

### Responsabilidades

| Clase | Responsabilidad |
|---|---|
| `Order`, `CreateOrderRequest` | Representan los conceptos del negocio |
| `OrderNotFoundException`, `InvalidOrderException` | Expresan fallos **de negocio** ("la orden no existe", "datos inválidos"), no códigos HTTP |
| `OrderRepository` | Abstracción del almacenamiento |
| `InMemoryOrderRepository` | Implementación en memoria del almacenamiento |
| `OrderService` | Reglas de negocio: validación, generación de ids, creación, consulta y búsqueda |

### Decisiones de diseño

- **La validación vive en `OrderService`.** Que una orden necesite cliente, producto y cantidad positiva es una regla de negocio: debe cumplirse sin importar si la petición llega por HTTP, por una invocación directa de Lambda o desde un test.
- **Los errores se modelan con excepciones de dominio.** La traducción a `400` o `404` es responsabilidad de una capa externa (API Gateway o el adaptador HTTP), no de la lógica de negocio.
- **Repositorio detrás de una interfaz.** Permite cambiar el almacenamiento en memoria por uno persistente sin tocar `OrderService`, y facilita las pruebas.
- **POJOs clásicos** (constructor vacío + getters/setters) en lugar de `record`, para que el serializador del runtime de Lambda pueda convertirlos desde y hacia JSON sin configuración adicional.
### Pruebas

`OrderServiceTest` cubre:

- creación de una orden válida;
- consulta de una orden existente;
- consulta de una orden inexistente (`OrderNotFoundException`);
- búsqueda de órdenes por cliente;
- búsqueda de un cliente sin órdenes (lista vacía);
- entradas inválidas: `customerId` nulo, `product` vacío, `quantity` en 0, request nulo y `customerId` vacío en la búsqueda.
  Resultado de `mvn test`:

```
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Checkpoint 1

**¿Se puede probar la funcionalidad sin AWS?**
Sí. `mvn test` ejecuta todas las pruebas localmente, sin credenciales ni servicios de AWS.

**¿La aplicación requiere un servidor web?**
No. Es un conjunto de clases Java que se invocan directamente.

**¿Requiere Spring Boot?**
No. La única dependencia del proyecto es JUnit 5, y solo en `scope` de test.

**¿Qué contiene el JAR generado?**
Únicamente las clases compiladas del proyecto y los metadatos de Maven (`jar tf target/orders-api-1.0.0.jar`):

```
META-INF/MANIFEST.MF
com/workshop/orders/application/InMemoryOrderRepository.class
com/workshop/orders/application/OrderRepository.class
com/workshop/orders/application/OrderService.class
com/workshop/orders/domain/CreateOrderRequest.class
com/workshop/orders/domain/InvalidOrderException.class
com/workshop/orders/domain/Order.class
com/workshop/orders/domain/OrderNotFoundException.class
META-INF/maven/com.workshop/orders-api/pom.xml
META-INF/maven/com.workshop/orders-api/pom.properties
```

No incluye JUnit (dependencia de test), ni librerías de AWS, ni servidor web.

**¿Qué partes de la aplicación conocen HTTP?**
Ninguna. En este punto no existe ningún concepto de request, response, header ni status code en el código.
 
---

## 3. Parte II — Despliegue en AWS Lambda

_Pendiente._

## 4. Parte III y IV — API Gateway con mapping templates

_Pendiente._

## 5. Parte V — Lambda proxy integration

_Pendiente._

## 6. Comparación de arquitecturas

_Pendiente._

## 7. Desarrollo asistido por IA

_Pendiente._

## 8. Extensión: DELETE /orders/{id}

_Pendiente._

## 9. Arquitectura final

_Pendiente._

## 10. Cómo compilar y probar

### Requisitos

- JDK 17 o superior (el proyecto compila con `release 17`)
- Maven 3.9+
### Comandos

```bash
mvn test      # ejecuta las pruebas unitarias
mvn package   # genera target/orders-api-1.0.0.jar
```