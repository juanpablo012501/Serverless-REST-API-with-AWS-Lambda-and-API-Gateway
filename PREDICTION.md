# Predicción: DELETE /orders/{id}

> Escrita antes de pedirle la implementación al asistente de IA.

## Arquitectura en la que se implementará
...
se implementara conlambda y api gateway
## 1. Clases Java que deben cambiar
- ...
la de http
## 2. ¿Se necesita una # Predicción: DELETE /orders/{id}

> Escrita **antes** de implementar el cambio.
> Se comparará después con la propuesta del asistente de IA y con la implementación final.

## Comportamiento esperado

| Request | Resultado |
|---|---|
| `DELETE /orders/ORD-001` (existe) | `204 No Content`, sin body |
| `DELETE /orders/ORD-999` (no existe) | `404 Not Found` con `{"error":"ORDER_NOT_FOUND","message":"Order ORD-999 does not exist"}` |
| `DELETE /orders/ORD-001` dos veces | Primera vez `204`, segunda vez `404` |

## Arquitectura en la que se implementará

Solo en la **Arquitectura B (Lambda proxy integration)**. Es la que elegí para un sistema más grande y la que queda como API final. En la Arquitectura A, el cambio requeriría un método nuevo, una Lambda nueva, mapping template, method responses e integration responses con regex, sin tests, lo que reforzaría las desventajas ya documentadas.

## 1. Clases Java que deben cambiar

| Capa | Clase | Cambio |
|---|---|---|
| Repositorio | `OrderRepository` | Nuevo método para eliminar por id (por ejemplo `boolean deleteById(String id)`) |
| Repositorio | `InMemoryOrderRepository` | Implementar la eliminación sobre el mapa en memoria |
| Aplicación | `OrderService` | Nuevo método `deleteOrder(String id)`: valida el id y lanza `OrderNotFoundException` si la orden no existe |
| Adaptador | `OrdersHttpHandler` | Nueva ruta `DELETE` + `/orders/{id}` en `route()` que llama al servicio y responde 204 |

**No cambian:** `Order`, `CreateOrderRequest`, las excepciones de dominio (se reutiliza `OrderNotFoundException`) ni `ErrorResponse`.

## 2. ¿Se necesita una nueva función Lambda?

**No.** En la Arquitectura B, una sola Lambda (`orders-http`) atiende todas las operaciones. Solo hay que subir el JAR nuevo a esa misma función.

## 3. ¿Cambia la configuración de API Gateway?

**No.** El recurso `/orders/{id}` tiene el método `ANY` con proxy integration, así que un `DELETE` ya llega a `orders-http`. Hoy el adaptador lo responde con 405; después del cambio, lo atenderá. Solo hay que volver a desplegar el código de la Lambda, no la API.

## 4. ¿Dónde vive la lógica de eliminación?

En la **lógica de aplicación**: `OrderService.deleteOrder()` decide si la orden existe y le pide al repositorio que la elimine; `InMemoryOrderRepository` la quita del almacenamiento. Eliminar una orden es una operación del negocio y debe funcionar igual sin importar si la invoca HTTP, una cola de mensajes o un test. `Order` no cambia: una orden no se elimina a sí misma.

## 5. ¿Dónde vive la decisión de responder 204 o 404?

Son dos decisiones distintas:
- **Saber** que la orden no existe: `OrderService`, que lanza `OrderNotFoundException`.
- **Traducirlo a HTTP**: `OrdersHttpHandler`, que es la única clase que conoce los status codes. Responde 204 si la eliminación terminó bien, y el `catch (OrderNotFoundException)` que ya existe para el GET produce el 404 sin código nuevo.
## 6. Tests a agregar

**`OrderServiceTest`**
- Eliminar una orden existente y verificar que luego `getOrder` lanza `OrderNotFoundException`.
- Eliminar una orden inexistente lanza `OrderNotFoundException`.
- Eliminar con id vacío lanza `InvalidOrderException`.
  **`OrdersHttpHandlerTest`**
- `DELETE` de una orden existente responde **204** sin body.
- `DELETE` de una orden inexistente responde **404** con el formato de error del contrato.
- `DELETE` dos veces seguidas: 204 y luego 404.
- `GET` después de `DELETE` responde 404.
## Riesgos o dudas

- **El test `unsupportedMethodReturns405` usa `PUT`**, que seguirá sin soportarse, así que debería seguir pasando. Si hubiera usado `DELETE`, habría que cambiarlo.
- **Memoria no compartida:** una orden eliminada en un entorno de Lambda puede seguir existiendo en otro entorno concurrente, o "reaparecer" si AWS crea un entorno nuevo con los datos precargados. Es la misma limitación de persistencia ya documentada.
- **Idempotencia:** HTTP define `DELETE` como idempotente (repetirlo deja el mismo estado), pero la segunda llamada responderá 404 en vez de 204. Ambas opciones son aceptables; elijo 404 porque así lo pide el enunciado.
- **Body vacío en el 204:** hay que asegurarse de que el adaptador no envíe body ni `Content-Type: application/json` en una respuesta sin contenido.
  nueva función Lambda?
...
si
## 3. ¿Cambia la configuración de API Gateway?
...
no
## 4. ¿Dónde vive la lógica de eliminación?
...
en el modelo
## 5. ¿Dónde vive la decisión de responder 204 o 404?
...
en el lambda
## 6. Tests a agregar
- ...
que borre o que no haya algo que borrar
## Riesgos o dudas
- ...