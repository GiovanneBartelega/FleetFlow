# FleetFlow Backend - REST API (Kotlin + Spring Boot)

Backend RESTful desenvolvido em **Kotlin** e **Spring Boot** para o ecossistema de Gestão de Frotas **FleetFlow**.

> ⚠️ **AVISO IMPORTANTE DE ARMAZENAMENTO**
> Este backend **NÃO UTILIZA BANCO DE DADOS**. Todos os dados de usuários e refresh tokens são mantidos puramente em memória (`ConcurrentHashMap`) thread-safe. **Todos os dados são perdidos quando a aplicação for reiniciada**.

---

## 🛠️ Tecnologias Utilizadas

- **Linguagem:** Kotlin 2.1
- **Framework:** Spring Boot 3.4.3
- **Segurança:** Spring Security + JSON Web Token (JJWT 0.12.6)
- **Autenticação Externa:** Google OAuth / Google ID Token Verifier
- **Documentação:** Swagger UI / OpenAPI 3.0 (`springdoc-openapi`)
- **Monitoramento:** Spring Boot Actuator (`/actuator/health`)
- **Gerenciador de Build:** Gradle (Groovy / Kotlin DSL)

---

## 📂 Arquitetura da Aplicação

```text
backend/
├── src/
│   ├── main/
│   │   ├── kotlin/com/fleetflow/backend/
│   │   │   ├── FleetFlowApplication.kt       # Classe Principal Spring Boot
│   │   │   ├── config/                       # Configurações (OpenAPI / Swagger)
│   │   │   ├── controller/                   # Endpoints REST (AuthController, UserController)
│   │   │   ├── dto/                          # Objetos de Transferência de Dados
│   │   │   ├── exception/                    # Exceções e Global Exception Handler
│   │   │   ├── model/                        # Modelos e Enums (User, RefreshToken, Role, Status)
│   │   │   ├── security/                     # Spring Security, JWT, Google Verifier, Filtro
│   │   │   └── service/                      # Regras de Negócio e Armazenamento em Memória
│   │   │
│   │   └── resources/
│   │       └── application.yml               # Configurações da aplicação
│   │
│   └── test/                                 # Testes Automatizados (JUnit 5 + MockMvc)
│
├── .env.example                              # Modelo de Variáveis de Ambiente
└── README.md
```

---

## 🔑 Funcionalidades e Regras de Negócio

### 1. Autenticação com Google ID Token
- Endpoint: `POST /api/auth/google`
- O cliente envia o `idToken` emitido pelo Google.
- O backend valida a assinatura, público (*audience*), emissor (*issuer*) e validade do token.
- Caso o usuário não exista em memória, ele é automaticamente criado.

### 2. Regra do Primeiro Administrador
- **O primeiro usuário criado no sistema recebe automaticamente a Role `ADMINISTRATOR`**.
- Os usuários subsequentes recebem a Role padrão `DRIVER`.
- A criação de usuários é sincronizada em bloco `synchronized` para garantir segurança em acessos concorrentes (*thread-safe*).

### 3. Emissão e Rotação de Tokens
- Após validar a identidade, o backend gera um **Access Token JWT** com validade curta (15 minutos).
- Um **Refresh Token** criptograficamente aleatório e seguro é gerado com validade de 7 dias e armazenado em memória utilizando o hash SHA-256.
- Endpoint de renovação: `POST /api/auth/refresh`.
- **Rotação de Refresh Token:** Ao utilizar um Refresh Token, o token antigo é revogado e um novo par de tokens é emitido. Se um token revogado for reutilizado, a requisição é bloqueada por segurança.

### 4. Logout
- Endpoint: `POST /api/auth/logout`.
- Revoga o Refresh Token no backend.

### 5. Middleware de Autenticação JWT
- Todas as rotas protegidas (como `GET /api/users/me`) exigem o cabeçalho `Authorization: Bearer <JWT>`.
- O filtro `JwtAuthenticationFilter` valida a assinatura e expiração do JWT e configura o `SecurityContextHolder`.

---

## ⚙️ Variáveis de Ambiente

Crie um arquivo `.env` ou configure as seguintes variáveis no sistema/IDE:

| Variável | Descrição | Valor Padrão (Dev) |
| :--- | :--- | :--- |
| `GOOGLE_CLIENT_ID` | OAuth 2.0 Client ID do Google Cloud | `your-google-client-id-here.apps.googleusercontent.com` |
| `JWT_SECRET` | Chave secreta HMAC-SHA256 para o JWT (mínimo 256 bits) | `v9y$B&E)H@MbQeThWmZq4t7w!z%C*F-JaNdRfUjXn2r5u8x/A?DGKaPdSgVkYp3s` |

---

## 🚀 Como Executar o Backend

### No Windows (PowerShell / Prompt):
```powershell
# Executar a aplicação
.\gradlew.bat bootRun

# Executar a suíte de testes
.\gradlew.bat test
```

### No Linux / macOS:
```bash
# Dar permissão de execução
chmod +x gradlew

# Executar a aplicação
./gradlew bootRun

# Executar a suíte de testes
./gradlew test
```

O servidor iniciará na porta **`8080`**: `http://localhost:8080`.

---

## 📑 Documentação dos Endpoints (Swagger & Health)

- **Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI JSON:** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- **Health Check:** [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

---

## 🧪 Testes Automatizados Executados

Os testes cobrem:
- **UserServiceTest:** Regra do primeiro administrador, criação concorrente *thread-safe*, recuperação e atualização.
- **JwtServiceTest:** Geração de JWT, validação de assinatura e extração de claims.
- **RefreshTokenServiceTest:** Geração, verificação, rotação e bloqueio de reutilização.
- **AuthControllerTest:** Testes de integração `MockMvc` para os endpoints de login Google, refresh e logout.
- **UserControllerTest:** Testes de integração `MockMvc` para `GET /api/users/me` com e sem token JWT.
