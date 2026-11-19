# InfraTrace

Backend para gestão de ativos de TI e controle de ocupação de racks em data center.
Projeto acadêmico de Análise e Desenvolvimento de Sistemas.

**Stack:** Java 21 · Spring Boot 4.1 (Spring Web) · JDBC puro (sem ORM) · MySQL 8 · Maven.

Documentação completa da API: [`docs/API.md`](docs/API.md).

## Como rodar

1. **Banco:** no MySQL Workbench, abra e execute `database/infratrace_mysql.sql`.
   > O script **apaga e recria** o banco `infratrace`.
2. **IntelliJ:** abra o projeto, defina o SDK como **JDK 21** (*File → Project Structure*) e
   clique em **Reload** na aba *Maven*.
3. **Variáveis de ambiente** (*Run → Edit Configurations → InfratraceApplication → Environment variables*):
   ```
   INFRATRACE_DB_URL=jdbc:mysql://localhost:3306/infratrace;INFRATRACE_DB_USER=root;INFRATRACE_DB_PASSWORD=<sua senha>
   ```
   A senha **nunca** vai para o código nem para o GitHub.
   Opcional: `INFRATRACE_ANEXOS_DIR=<pasta>` define onde os anexos são gravados. O padrão é
   `anexos/`, dentro da pasta onde a aplicação roda.
4. Rode `InfratraceApplication`. O console deve mostrar `Tomcat started on port 8080`.
5. No **Postman**, importe `docs/InfraTrace.postman_collection.json` e use as requisições
   direto. Não há login nesta fase: as alterações ficam registradas em nome do
   **Administrador** (id 1) criado pelo script. Para registrar em nome de outro usuário,
   envie o cabeçalho `X-Usuario-Id`.

Teste automático (com o banco recém-criado e a API rodando):
`powershell -ExecutionPolicy Bypass -File docs\teste_api.ps1`

## Estrutura

```
backend-java/Infratrace/src/main/java/br/com/infratrace/
├── InfratraceApplication   liga o Spring Boot
├── controller/   recebe HTTP e devolve JSON (sem SQL, sem regra de negócio)
├── service/      regras de negócio, validações e transações (commit/rollback)
├── dao/          SQL com PreparedStatement/ResultSet (sem regra de negócio)
├── model/        entidades (espelham as tabelas)
├── dto/          dados que entram e saem da API
├── exception/    exceções do sistema e conversão em respostas HTTP
└── config/       conexão com o banco, transação manual, pasta de anexos e o interceptor
                  que identifica o usuário responsável por cada alteração
database/infratrace_mysql.sql   script oficial do banco (tabelas, triggers, dados iniciais)
docs/                            documentação da API, coleção do Postman e teste automático
```

## Fluxo de uma requisição

```
Postman → UsuarioResponsavelInterceptor (quem fez?) → Controller → Service → DAO → MySQL
                                                       ↑ erros viram JSON no TratadorDeErros
```
