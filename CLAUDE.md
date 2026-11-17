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
- A versão original em PostgreSQL (`supabase/infratraceDDL.sql`) foi removida em 2026-11-17 por
  decisão do grupo. Se precisar consultá-la, ela está no histórico do Git.
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

## Estado atual (atualizado em 2026-11-17)

Estrutura: `backend-java/Infratrace/` (Maven) e `database/` (DDL MySQL oficial).
Código em `src/main/java/br/com/infratrace/`:
- `Main`: teste do primeiro marco (SELECT em `tipo_ativo`), ainda sem Spring;
- `config/Conexao`: lê `INFRATRACE_DB_*` e lança `SQLException`;
- `model/`: as 14 entidades, só com atributos, getters e setters (sem subpacotes).

Já resolvido:
- Driver `mysql-connector-j` 26.10.0 vem do Maven; o `.jar` manual do `.idea` foi removido.
- `Conexao` sem credenciais no código. A senha antiga `root`/`root` ainda aparece no histórico
  do GitHub, mas não é a senha real.
- Banco `infratrace` criado no MySQL 8.0.46 local; o primeiro marco funciona.
- `.gitignore` criado; `target/`, `out/` e `.idea/` estão fora do Git. Cada integrante configura
  o SDK e a Run Configuration (com as variáveis de ambiente) no próprio IntelliJ.
- Pacote base `br.com.infratrace` em minúsculas; interface `Crud` removida; `InfratraceDAO`
  removido; `Configuracao` e `LogAuditoria` renomeadas.
- Pasta `supabase/` removida.
- Spring Boot 4.1.2 (`spring-boot-starter-parent` + só `spring-boot-starter-web`). Classe
  principal: `br.com.infratrace.InfratraceApplication` (definida como `start-class` no pom,
  porque o `Main` antigo também tem `main`). Sobe na porta 8080.
- `application.properties` **não** tem as credenciais nem `spring.datasource.*`: a `Conexao`
  continua estática e lendo `System.getenv`, porque o acesso é JDBC manual. Se um dia a
  `Conexao` virar um `@Component`, as chaves podem ir para o `application.properties` com
  `${INFRATRACE_DB_*}`.
- JDK 21 instalado pelo IntelliJ em `~/.jdks/openjdk-21.0.2`. Maven não está no PATH; o do
  IntelliJ fica em `plugins/maven-plugin/lib/maven3/bin/mvn.cmd`.

Ainda pendente:
1. O Model `Movimentacao` não bate com a tabela: faltam `fk_rac_destino_id` e a separação entre
   slots de origem (`mov_slot_*_origem`) e de destino (`mov_slot_*_destino`). Corrigir quando
   for implementar a movimentação.
2. Os pacotes `dao/`, `service/`, `controller/` e `dto/` serão criados com a primeira classe de
   cada um (o Git não versiona pastas vazias).

## Próximos passos (nesta ordem, um de cada vez)

1. **Primeira entidade de ponta a ponta** (sugestão: `TipoAtivo`, já tem dados):
   Model → DAO → Service → Controller → `GET /tipos-ativo` testado no Postman. Depois disso o
   `Main` do primeiro marco pode ser removido (e o `start-class` do pom também).

Etapas seguintes do plano do grupo: CRUD → regras de negócio (ocupação dos racks) →
transações manuais (movimentação) → auditoria e usuários (perfis ADMIN, TECNICO, AUDITOR) →
testes no Postman e documentação da API.
