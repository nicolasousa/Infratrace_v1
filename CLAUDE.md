# InfraTrace — Instruções para o Claude Code

Sistema de gerenciamento de ativos de TI e controle de ocupação de racks em data center.
Projeto acadêmico de estudantes de Análise e Desenvolvimento de Sistemas. O objetivo é
entregar um backend funcional **e** fazer o grupo entender as tecnologias, principalmente
os conceitos de Banco de Dados vistos em aula.

## Como trabalhar com o grupo (importante)

- O grupo quer **aprender**, não só receber código pronto. Antes de implementar, explique o que
  vai ser feito e por quê. Explique as classes, os métodos e como as camadas se comunicam.
- Trabalhe em **etapas pequenas**. Não gere o projeto inteiro de uma vez. Ao fim de cada etapa,
  pare e espere o grupo testar e responder antes de avançar.
- Sempre que fizer sentido, deixe o grupo escrever partes do código e fazer configurações.
- Se houver mais de uma forma de fazer algo, apresente a recomendação e explique as alternativas.
- Explique os erros encontrados e como resolvê-los.
- Responda em **português**.

## Stack obrigatória

Java 21 · Spring Boot · Spring Web · **JDBC puro** · Maven · **MySQL 8** · Postman · Git/GitHub.
Não troque nenhuma tecnologia sem justificar e receber aprovação.

**Proibido:** Hibernate, JPA, Spring Data JPA, Spring Data JDBC, `JdbcTemplate`, `JpaRepository`,
qualquer ORM e `@Transactional`.

A comunicação com o banco é manual: `Connection`, `DriverManager`, `PreparedStatement`,
`ResultSet`, `setAutoCommit(false)`, `commit()`, `rollback()`, sempre com `try-with-resources`.
O grupo escreve as próprias consultas SQL.

Só backend nesta fase: nada de HTML, CSS, React ou qualquer interface visual.

## Arquitetura

Monolito em camadas:
- **controller**: recebe requisições HTTP e devolve respostas. Sem SQL.
- **service**: regras de negócio, validações e controle de transação (commit/rollback).
- **dao**: SQL via JDBC. Sem regra de negócio.
- **model**: entidades.
- **dto**: dados que entram e saem da API.
- **config**: configurações (ex.: fábrica de conexões).

Evite abstrações desnecessárias. É um projeto acadêmico de pequeno porte.

## Banco de dados (decisão do professor: MySQL 8)

- Script oficial: **`database/infratrace_mysql.sql`**. Ele apaga e recria o banco `infratrace`.
  Nunca rode esse script num banco com dados que precisam ser mantidos.
- `supabase/infratraceDDL.sql` é a versão original em PostgreSQL. Ficou só como referência e
  **não é usada**.
- **Não altere tabelas, colunas ou relacionamentos** sem explicar o motivo e receber aprovação.
  Use sempre os nomes reais do script. Nunca invente nomes de tabelas ou colunas.
- Convenções: snake_case, prefixo de 3 letras por tabela (`atv_`, `rac_`, `usr_`...), FKs como
  `fk_<prefixo>_id`. IDs com `AUTO_INCREMENT`; datas e horas em `DATETIME` (mapear para
  `LocalDateTime`).
- Regras garantidas pelo banco por triggers (`SIGNAL SQLSTATE '45000'`, erro 1644 no JDBC):
  - slots sobrepostos no mesmo rack;
  - slot final acima da capacidade do rack (em `ativo` e no destino de `movimentacao`);
  - redução da capacidade de um rack abaixo de um ativo instalado;
  - aluguéis com período sobreposto no mesmo rack.

  O Service também deve validar essas regras (Etapa 6), para devolver mensagens amigáveis.
- **Movimentação:** a tabela `movimentacao` não altera o ativo sozinha. Na mesma transação JDBC:
  `INSERT INTO movimentacao` → `UPDATE ativo SET fk_rac_id, atv_slot_inicio, atv_slot_fim` →
  `commit()`, ou `rollback()` se qualquer passo falhar.

## Credenciais

**Nunca** coloque usuário ou senha no código nem no GitHub. A conexão lê variáveis de ambiente:

| Variável | Exemplo |
|---|---|
| `INFRATRACE_DB_URL` | `jdbc:mysql://localhost:3306/infratrace` |
| `INFRATRACE_DB_USER` | `root` |
| `INFRATRACE_DB_PASSWORD` | (senha local de cada integrante) |

No IntelliJ, essas variáveis ficam em *Run → Edit Configurations → Environment variables*.

## Git

- Antes de mexer em qualquer arquivo: veja `git status`, a branch atual e o que já existe.
- Preserve o que já funciona. Não sobrescreva arquivos sem necessidade.
- Commits pequenos, com mensagens claras em português.
- **Não faça push, merge nem nada destrutivo sem autorização.**
- A branch `supabase` é antiga. O trabalho segue na `main`.

## Estado atual (análise feita em 2026-11-16)

Estrutura: `backend-java/Infratrace/` (Maven), `supabase/` (DDL PostgreSQL antigo) e
`database/` (DDL MySQL oficial).

Problemas encontrados que ainda **não** foram corrigidos:
1. `pom.xml` só define Java 21: não tem nenhuma dependência (nem Spring Boot, nem driver MySQL).
2. O driver MySQL foi adicionado à mão no IntelliJ, como `.jar` na pasta Downloads
   (`.idea/libraries/mysql_connector_j_26_10_0.xml`). Deve passar a vir do Maven:
   `com.mysql:mysql-connector-j`.
3. `DAO/Conexao.java`:
   - tem `root`/`root` fixos no código (já foram para o GitHub);
   - a URL não inclui o nome do banco;
   - em caso de erro, retorna `null` em vez de lançar a exceção;
   - não usa `try-with-resources`.
4. Não há `.gitignore` na raiz e a pasta `target/` está versionada.
5. Os pacotes estão em maiúsculas (`DAO`, `Model`) e não há pacote base. O Spring Boot precisa
   de um pacote raiz. **Aguardando aprovação do grupo:** criar `br.com.infratrace` com os
   pacotes em minúsculas.
6. Os Models implementam a interface `Crud` (`buscar`, `adicionar`...), o que mistura
   persistência com entidade. **Aguardando aprovação do grupo:** remover a interface e deixar
   essas operações nos DAOs.
7. `InfratraceDAO` tem código comentado copiado de outro projeto (`Alunos`).
8. O Model `Movimentacao` não bate com a tabela: faltam `fk_rac_destino_id` e a separação entre
   slots de origem (`mov_slot_*_origem`) e de destino (`mov_slot_*_destino`).
9. As classes `logAuditoria` e `configuracao` começam com letra minúscula.
10. Ambiente (Windows): o MySQL Server 8.0 e o Workbench 8.0 estão instalados. Em
    `C:\Program Files\Java` só foi encontrado o Java 8. Confirme que existe um **JDK 21** em
    *File → Project Structure → SDK* e, se não existir, oriente o download (Eclipse Temurin 21).

## Próximos passos (nesta ordem, um de cada vez)

1. **Criar o banco:** confirmar que o grupo rodou `database/infratrace_mysql.sql` no Workbench e
   que `SELECT tpa_id, tpa_nome FROM infratrace.tipo_ativo ORDER BY tpa_id;` devolve 7 linhas.
2. **Conferir o ambiente:** `java -version`, JDK 21 no IntelliJ e `git status`.
3. **Driver no pom.xml:** adicionar `mysql-connector-j` (versão atual estável do Maven Central)
   e remover a biblioteca manual do `.idea`. Explicar o que é um driver JDBC.
4. **Corrigir a `Conexao`:** ler as variáveis de ambiente e lançar `SQLException` em vez de
   devolver `null`.
5. **Primeiro marco:** no `Main`, ainda sem Spring, executar
   `SELECT tpa_id, tpa_nome FROM tipo_ativo ORDER BY tpa_id` com `PreparedStatement` e
   `ResultSet`, usando `try-with-resources`, e imprimir as linhas. Explicar o caminho
   Java → driver → MySQL → `ResultSet` → objeto Java.
6. Só depois: `.gitignore`, as reorganizações aprovadas (itens 5 e 6), o Spring Boot
   (`application.properties` com `${INFRATRACE_DB_*}`) e a primeira entidade com
   Model → DAO → Service → Controller → endpoint REST testado no Postman.

Etapas seguintes do plano do grupo: CRUD → regras de negócio (ocupação dos racks) →
transações manuais (movimentação) → auditoria e usuários (perfis ADMIN, TECNICO, AUDITOR) →
testes no Postman e documentação da API.
