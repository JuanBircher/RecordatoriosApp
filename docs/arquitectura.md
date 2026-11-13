# Arquitectura: de Compose a Firebase

Cómo circula una operación por las capas de la app. Toma como ejemplo **modificar un recordatorio**; crear
y eliminar siguen el mismo camino.

## Capas y responsabilidades

```mermaid
flowchart LR
    subgraph UI["Presentación"]
        C["PantallaCrear<br/>(@Composable)"]
        VM["CrearViewModel<br/>UiState + StateFlow"]
    end
    subgraph DATA["Capa de datos"]
        R["RecordatoriosRepository<br/>(interfaz)"]
        RI["RecordatoriosRepositoryImpl"]
        DAO["RecordatorioDao<br/>(Room, filtra por uid)"]
        S["SincronizadorRecordatorios"]
        FR["FirestoreRemoteDataSource"]
        AR["AuthRepository<br/>FirebaseAuthRepository"]
        CO["CoordinadorSesion"]
    end
    subgraph CLOUD["Firebase"]
        FA["Authentication"]
        FS["Cloud Firestore<br/>users/{uid}/reminders/{clave}<br/>+ reglas de seguridad"]
    end

    C -- "onGuardar()" --> VM
    VM -- "actualizarRecordatorio()" --> R
    R --- RI
    RI -- "1 · guarda Pendiente" --> DAO
    DAO -- "Flow (vuelve a emitir)" --> VM
    VM -- "uiState" --> C
    RI -- "2 · pide sincronizar" --> S
    S -- "pendientes del uid" --> DAO
    S -- "guardar(uid, clave, dto)" --> FR
    FR -- "set() / get(SERVER)" --> FS
    AR -- "sesión, uid" --> FA
    AR -- "uid activo" --> RI
    CO -- "cuándo sincronizar" --> S
```

## Recorrido de una modificación

```mermaid
sequenceDiagram
    actor P as Persona
    participant C as PantallaCrear (Compose)
    participant VM as CrearViewModel
    participant R as RecordatoriosRepositoryImpl
    participant DB as Room (DAO)
    participant S as Sincronizador
    participant F as FirestoreRemoteDataSource
    participant FS as Cloud Firestore

    P->>C: toca «Guardar»
    C->>VM: onGuardar() (lambda)
    VM->>VM: valida el formulario
    VM->>R: actualizarRecordatorio(recordatorio)
    R->>R: uid = cuenta con sesión activa
    R->>DB: actualizar(fila con uid, sincronizacion = Pendiente)
    DB-->>VM: Flow emite la fila nueva
    VM-->>C: uiState (se ve «Pendiente de enviar»)
    R-)S: sincronizar() en segundo plano
    S->>DB: obtenerPendientes(uid)
    alt sin conexión
        S-->>DB: queda Pendiente (se reintenta al volver la red)
    else con conexión
        S->>F: guardar(uid, claveCliente, dto)
        F->>FS: set(users/uid/reminders/claveCliente)
        FS->>FS: reglas: ¿request.auth.uid == uid?, ¿documento válido?
        alt permitido
            FS-->>F: OK
            F-->>S: Exito
            S->>DB: Sincronizado
            DB-->>VM: Flow emite (se ve «Sincronizado»)
        else rechazado (PERMISSION_DENIED)
            FS-->>F: error
            F-->>S: NoAutorizado
            S-->>DB: queda Pendiente (no se pierde)
            Note over S: CoordinadorSesion vuelve a comprobar la sesión
        end
    end
```

## Qué sabe cada capa

| Capa | Conoce | No conoce |
|---|---|---|
| Composables | `UiState`, lambdas de eventos | ViewModel internos, repositorio, Firebase |
| ViewModel | Interfaces `RecordatoriosRepository` y `AuthRepository`, modelos de dominio | Room, DTO, Firebase, códigos de error |
| Repositorio | DAO, sincronizador, uid activo | Firebase (solo a través de la fuente remota) |
| Fuente remota | Firestore, DTO, mapeo a `Map` | Room, interfaz |
| Clasificadores | Códigos de error del proveedor | Todo lo demás: son funciones puras |

## Modelos en cada capa

| Modelo | Dónde | Para qué |
|---|---|---|
| `Recordatorio` | dominio / interfaz | Lo que ve la persona |
| `RecordatorioEntity` | Room | Fila local: suma `uid`, `claveCliente`, `enRemoto`, estado de sincronización e intentos |
| `RecordatorioDto` | contrato remoto | Solo lo que se comparte entre dispositivos |
| `Map<String, Any>` | Firestore | Documento: el DTO sin la clave (es el id del documento), más `actualizadoEl` del servidor |

Las conversiones están en `data/Mapeos.kt` (dominio ↔ entidad ↔ DTO) y `data/remote/MapeosFirestore.kt`
(DTO ↔ documento).
