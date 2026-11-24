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
  `fk_<prefixo>_id`, views como `View_<nome>` e triggers como `Trigger_<nome>`. IDs com `AUTO_INCREMENT`; datas e horas em `DATETIME` (mapear para
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

## Estado atual (atualizado em 2026-11-18)

Em 2026-11-18, por causa do prazo, o grupo pediu para concluir a maior parte do backend de uma
vez (em vez de etapas pequenas). Agora a prioridade é o grupo **entender** o que foi feito:
ao explicar, use os comentários das classes e o `docs/API.md`.

Código em `backend-java/Infratrace/src/main/java/br/com/infratrace/`:
- `controller/` → `service/` → `dao/`, um conjunto por entidade, + `model/`, `dto/`, `exception/`
  (`TratadorDeErros` converte exceções e códigos MySQL 1644/1062/1451/1452/3819 em HTTP),
  e `config/` (`Conexao`, `Transacao`, `WebConfig`, `UsuarioResponsavelInterceptor`).
- **Sem login** (decisão do grupo em 2026-11-18, para a apresentação): login, sessões, hash de
  senha e perfis restringindo acesso foram removidos (estão no histórico do Git, commit
  `91a81ed`). O `UsuarioResponsavelInterceptor` define quem responde por cada escrita: cabeçalho
  `X-Usuario-Id` ou, sem ele, o primeiro ADMIN ativo. O script SQL cria o usuário
  "Administrador" (id 1) com `usr_senha_hash = 'SEM_LOGIN'` (a coluna é NOT NULL).
- Padrão de transação: os DAOs recebem a `Connection`; o Service abre, faz
  `setAutoCommit(false)`, `commit()`/`rollback()`. `config/Transacao` centraliza isso;
  `MovimentacaoService` está escrito por extenso de propósito (exemplo didático).
- Auditoria: toda escrita grava `log_auditoria` na mesma transação (JSON com antes/depois).
- Racks: `RackService.validarPosicao` trava o rack com `SELECT ... FOR UPDATE` e valida
  capacidade e sobreposição antes dos triggers.
- Views (2026-11-23): `View_ocupacao_rack` (lida por `RackDAO.listarOcupacao`, rota
  `GET /racks/ocupacao`) e `View_garantia_ativo` (lida por `AtivoDAO.listarGarantiaVencendo`,
  rota `GET /alertas/garantias`, que agora devolve o DTO `GarantiaVencendo`).
- Spring Boot 4.1.2 só com `spring-boot-starter-web`; Jackson 3 (`tools.jackson.*`).
- `application.properties` não tem `spring.datasource.*`: a `Conexao` lê `System.getenv`.
- JDK 21 em `~/.jdks/openjdk-21.0.2`; Maven do IntelliJ em
  `plugins/maven-plugin/lib/maven3/bin/mvn.cmd` (não está no PATH).
- Anexos: arquivos na pasta `infratrace.anexos.diretorio` (`${INFRATRACE_ANEXOS_DIR:anexos}`,
  fora do Git), salvos com nome UUID; `anx_caminho` guarda esse nome. Como o disco não entra na
  transação, a ordem é arquivo → INSERT (e apaga o arquivo se o INSERT falhar) e DELETE → commit
  → apaga o arquivo. Limite de 10 MB (413).
- Documentação: `docs/API.md`, `docs/InfraTrace.postman_collection.json` (65 requisições;
  editada por script, o gerador não fica no repositório) e `docs/teste_api.ps1` (84 verificações
  de ponta a ponta; todas passaram em 2026-11-23, incluindo as 2 das views; envios de
  anexo usam `curl.exe`). A coleção do Postman inclui
  `GET /racks/ocupacao` (pasta 2. Infraestrutura).
- Apresentação: `database/dados_exemplo.sql` (rodar logo após o script principal; IDs fixos e
  datas relativas a `CURDATE()`) e `docs/roteiro_apresentacao.md` (views, triggers, transação
  e rollback no Workbench, na ordem; depende dos IDs e contagens do `dados_exemplo.sql`).
- `docs/guia_postman.md`: teste da API do zero pela coleção do Postman (banco vazio, sem
  `dados_exemplo.sql`), pasta por pasta, com o status esperado; exclusões ficam para o fim.

## Pendente

1. Login e recuperação de senha ficaram fora desta fase por decisão do grupo. As colunas
   `usr_senha_hash`, `usr_token_recuperacao` e `usr_token_expiracao` continuam no banco.
2. Não há testes JUnit; só o teste de ponta a ponta em PowerShell e a coleção do Postman.
3. Listagens sem paginação.
4. Conferir com o enunciado do professor se há requisitos (RF) não cobertos.
5. Antes de dizer o que está ou não no GitHub, confira com `git fetch` + `git status -sb`
   (em 2026-11-23 tudo foi enviado até o commit das views e da padronização de nomes).