# Roteiro da apresentação — InfraTrace

Objetivo: mostrar ao professor, funcionando, os quatro conceitos de Banco de Dados do projeto:
**views**, **triggers**, **procedures** e **transações**. Tempo estimado: 15 a 20 minutos.

Os passos usam os dados de `database/dados_exemplo.sql`. **Siga a ordem**: alguns passos
dependem do anterior (por exemplo, a contagem de movimentações na parte 4).

---

## 0. Preparação (antes da aula)

1. No MySQL Workbench, rode `database/infratrace_mysql.sql` (recria o banco do zero).
2. Rode `database/dados_exemplo.sql`. No fim ele mostra as duas views preenchidas.
3. No IntelliJ, suba a aplicação (variáveis `INFRATRACE_DB_*` configuradas).
4. No Postman, importe `docs/InfraTrace.postman_collection.json` (`baseUrl` = `http://localhost:8080`).
5. Deixe abertos: Workbench, Postman e, no IntelliJ, os arquivos
   `database/infratrace_mysql.sql`, `config/Transacao.java` e `service/MovimentacaoService.java`.

Situação inicial dos dados:

| Rack | Sala / fileira | Slots | Ativos |
|---|---|---|---|
| R01 (id 1) | Sala 1 / A | 42 | SRV-01 (1-2), SRV-02 (3-4, em manutenção), SW-01 (42) |
| R02 (id 2) | Sala 1 / A | 42 | STG-01 (1-4), FW-01 (10, garantia vencida) |
| R03 (id 3) | Sala 1 / B | 24 | PDU-01 (1), RT-01 (10-11) — alugado à Alfa até daqui a 10 dias |
| R04 (id 4) | Sala 2 / A | 42 | **vazio** |
| R05 (id 5) | Sala 1 / B | 12 | PP-01 (1-10) — alugado à Beta |
| estoque | — | — | SRV-03 (id 7) e SRV-OLD (id 8, descartado) |

---

## 1. Apresentação rápida do sistema (2 min)

- O que é: controle de ativos de TI e da ocupação dos racks de um data center.
- Arquitetura: **controller → service → dao**, com JDBC puro (sem ORM). Explicar que todo SQL
  foi escrito pelo grupo, com `PreparedStatement`.
- Banco: MySQL 8, script `database/infratrace_mysql.sql`.

---

## 2. Views (4 min)

**Explicação:** uma view é um `SELECT` guardado no banco com um nome. Não guarda dados; toda vez
que é consultada, o MySQL executa a consulta sobre as tabelas. Mostrar as duas `CREATE VIEW`
no script (procurar por `CREATE VIEW`).

### 2.1 No Workbench

```sql
SELECT * FROM View_ocupacao_rack;
```
Pontos para comentar:
- R01 11,9% · R02 11,9% · R03 12,5% · **R04 0%** · R05 83,3%.
- O **R04 aparece mesmo vazio** por causa do `LEFT JOIN` com `ativo`. Com `JOIN` comum ele sumiria.
- O `COALESCE(SUM(...), 0)` transforma o `NULL` do rack vazio em 0.

```sql
SELECT * FROM View_garantia_ativo ORDER BY dias_restantes;
```
Pontos para comentar:
- `dias_restantes` é calculado com `DATEDIFF(atv_garantia, CURDATE())`.
- FW-01 tem **-30**: garantia já vencida.
- SRV-03 está no estoque: rack, fileira e sala vêm `NULL` (de novo o `LEFT JOIN`).
- SRV-OLD **não aparece**: está descartado (`WHERE atv_status <> 'DESCARTADO'`).
- PDU-01 e PP-01 não aparecem: não têm garantia cadastrada.

### 2.2 A API usando as views (Postman)

| Requisição | Resultado esperado |
|---|---|
| `GET /racks/ocupacao` | Os 5 racks, com os mesmos números do Workbench |
| `GET /racks/ocupacao?salaId=2` | Só o R04 |
| `GET /alertas/garantias` | `diasAntecedencia: 30`, `quantidade: 3`: STG-01 (10 dias), SRV-01 (20), SRV-03 (25) |
| `PUT /configuracoes/alerta_garantia_dias` com `{ "valor": "60" }` | 200 |
| `GET /alertas/garantias` | `quantidade: 4`: entrou o SRV-02 (45 dias) |
| `PUT /configuracoes/alerta_garantia_dias` com `{ "valor": "30" }` | 200 (volta ao normal) |
| `GET /alertas/alugueis` | O aluguel do R03 (Alfa), que vence em 10 dias |

Mostrar no código: `RackDAO.listarOcupacao` e `AtivoDAO.listarGarantiaVencendo` fazem um `SELECT`
simples na view — para o Java, uma view é igual a uma tabela.

---

## 3. Triggers (4 min)

**Explicação:** trigger é um código que o banco executa sozinho antes (`BEFORE`) de um `INSERT` ou
`UPDATE`. Os nossos conferem uma regra e, se ela for violada, cancelam a operação com
`SIGNAL SQLSTATE '45000'`. Mostrar no script os 7 `CREATE TRIGGER Trigger_...`.

### 3.1 Direto no Workbench (sem passar pelo Java)

Mostra que a regra é do **banco**: vale até para quem grava sem usar a API.
Rode um comando por vez (Ctrl+Enter). Os três devem dar **Error Code: 1644**.

```sql
-- Trigger_ativo_valida_slots_ins: slots 2-3 do R01 já estão ocupados (SRV-01 e SRV-02)
INSERT INTO ativo (atv_nome, atv_fabricante, atv_modelo, atv_num_serie, fk_tpa_id,
                   fk_rac_id, atv_slot_inicio, atv_slot_fim)
VALUES ('TESTE', 'Dell', 'R650', 'SN-TESTE', 1, 1, 2, 3);

-- Trigger_rack_reducao_capacidade: o R05 tem ativo até o slot 10
UPDATE rack SET rac_total_slots = 8 WHERE rac_id = 5;

-- Trigger_aluguel_periodo_ins: o R03 já está alugado neste período
INSERT INTO aluguel (alu_data_inicio, alu_data_fim, alu_valor, fk_cli_id, fk_rac_id)
VALUES (CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 YEAR), 3200, 2, 3);
```

### 3.2 As mesmas regras pela API

O Service confere as mesmas regras **antes** do banco, para devolver uma mensagem clara.
O trigger fica como última proteção. Se mesmo assim o trigger barrar, o `TratadorDeErros`
transforma o erro 1644 em HTTP 409.

| Requisição | Corpo | Resultado |
|---|---|---|
| `POST /ativos` | `{ "nome": "SRV-04", "fabricante": "Dell", "modelo": "R650", "numSerie": "SN-SRV-04", "tipoAtivoId": 1, "rackId": 1, "slotInicio": 2, "slotFim": 3 }` | **409**, dizendo qual ativo ocupa os slots |
| `PUT /racks/5` | `{ "nome": "R05", "totalSlots": 8, "fileiraId": 2 }` | **409**, ativo ocupando até o slot 10 |
| `POST /alugueis` | `{ "dataInicio": "<hoje>", "dataFim": "<daqui a 1 ano>", "valor": 3200, "clienteId": 2, "rackId": 3 }` | **409**, R03 já alugado |
| `POST /alugueis` | o mesmo, mas com `"rackId": 4` | **201**: o R04 está livre |

Datas no formato `AAAA-MM-DD`.

---

## 4. Transações (6 min) — a parte principal

**Definição (como o professor pediu):** uma transação é uma operação que só é executada se uma
**série de condições for verdadeira**. Ela junta vários passos, e **ou todos são gravados, ou
nenhum é** (atomicidade).

A transação da movimentação é feita **pelo MySQL**, na `Procedure_movimentar_ativo`
(mostrar no script, procurando por `CREATE PROCEDURE`):
```sql
DECLARE EXIT HANDLER FOR SQLEXCEPTION   -- se QUALQUER comando falhar:
BEGIN
    ROLLBACK;                           -- desfaz tudo
    RESIGNAL;                           -- e devolve o erro para quem chamou
END;

START TRANSACTION;                      -- nada é gravado de vez a partir daqui
-- confere as condições; se alguma for falsa -> SIGNAL (erro)
-- INSERT movimentacao, UPDATE ativo, INSERT log_auditoria
COMMIT;                                 -- todas verdadeiras: grava tudo
```

A API só chama a procedure: `MovimentacaoDAO.movimentar` faz
`CALL Procedure_movimentar_ativo(?, ?, ?, ?, ?, ?, ?)` com `CallableStatement` (JDBC). Antes,
o `MovimentacaoService` confere as condições lendo o banco, para responder com mensagem clara.

Condições da movimentação:

1. o ativo existe (e fica travado com `SELECT ... FOR UPDATE`);
2. não está descartado;
3. não está sendo mandado para o estoque se já estiver lá;
4. o destino é diferente da posição atual;
5. o rack de destino existe (e fica travado) e o slot final cabe na capacidade;
6. nenhum outro ativo ocupa esses slots;
7. o `INSERT INTO movimentacao` passa nos CHECKs e no trigger;
8. o `UPDATE ativo` passa no trigger;
9. a auditoria é gravada.

Só com as 9 verdadeiras acontece o `COMMIT`. As condições 1, 2, 5, 6, 7 e 8 são conferidas
pelo próprio banco (procedure e triggers); o Java confere de novo 1 a 6 só para dar mensagens
melhores.

As outras escritas do sistema (cadastros, aluguel, manutenção) usam transação JDBC
(`config/Transacao.java`: `setAutoCommit(false)`, `commit()`, `rollback()`).

### 4.1 Condições falsas → nada é gravado (Postman, `POST /movimentacoes`)

Antes: `GET /movimentacoes` → **2** movimentações.

| Corpo | Condição falsa | Resultado |
|---|---|---|
| `{ "ativoId": 99, "rackDestinoId": 4, "slotInicioDestino": 1, "slotFimDestino": 1 }` | 1 (não existe) | 404 |
| `{ "ativoId": 8, "rackDestinoId": 4, "slotInicioDestino": 1, "slotFimDestino": 1 }` | 2 (SRV-OLD descartado) | 409 |
| `{ "ativoId": 3, "rackDestinoId": 1, "slotInicioDestino": 42, "slotFimDestino": 42 }` | 4 (já está lá) | 409 |
| `{ "ativoId": 3, "rackDestinoId": 5, "slotInicioDestino": 11, "slotFimDestino": 13 }` | 5 (R05 só tem 12 slots) | 400 |
| `{ "ativoId": 3, "rackDestinoId": 1, "slotInicioDestino": 1, "slotFimDestino": 1 }` | 6 (slot do SRV-01) | 409 |

Depois: `GET /movimentacoes` → continua **2**. Nenhuma tentativa deixou dado pela metade.

### 4.2 Todas verdadeiras → commit

`POST /movimentacoes` com
`{ "ativoId": 7, "rackDestinoId": 4, "slotInicioDestino": 1, "slotFimDestino": 2, "motivo": "Volta após upgrade" }`
→ **201**.

Conferir que os dois passos foram gravados juntos:
- `GET /movimentacoes` → **3** (o INSERT);
- `GET /ativos/7` → `rackId: 4`, slots 1-2 (o UPDATE);
- `GET /racks/ocupacao` → o R04 passou de 0% para **4,8%** (a view já reflete);
- `GET /ativos/7/movimentacoes` → histórico do SRV-03: saiu do R03 e voltou no R04;
- `GET /auditoria?entidade=ativo&entidadeId=7` → o UPDATE do ativo, com antes e depois.

### 4.3 Rollback na prática (Workbench) — o momento principal

Aqui o grupo faz "à mão", passo a passo, o que a procedure faz. O INSERT dá certo, o UPDATE falha, e o ROLLBACK desfaz
o INSERT. Rode **um comando por vez** (Ctrl+Enter):

```sql
START TRANSACTION;

-- Passo 1: registra a movimentação do SW-01 (slot 42 -> slot 1 do R01).
-- Passa: o trigger da movimentação só confere a capacidade (1 <= 42).
INSERT INTO movimentacao (mov_motivo, mov_slot_inicio_origem, mov_slot_fim_origem,
                          mov_slot_inicio_destino, mov_slot_fim_destino,
                          fk_rac_origem_id, fk_rac_destino_id, fk_atv_id, fk_usr_id)
VALUES ('Teste de rollback', 42, 42, 1, 1, 1, 1, 3, 1);

SELECT COUNT(*) FROM movimentacao;   -- 4: dentro da transação o INSERT já aparece

-- Passo 2: move o ativo. FALHA (1644): o slot 1 é do SRV-01.
UPDATE ativo SET fk_rac_id = 1, atv_slot_inicio = 1, atv_slot_fim = 1 WHERE atv_id = 3;

-- Uma condição foi falsa: desfaz tudo.
ROLLBACK;

SELECT COUNT(*) FROM movimentacao;   -- 3 de novo: o INSERT do passo 1 sumiu
```

Ponto para falar: **sem a transação**, o banco ficaria com uma movimentação registrada de um
ativo que nunca saiu do lugar. É exatamente o que o `EXIT HANDLER ... ROLLBACK` da
`Procedure_movimentar_ativo` evita (parte 5.2).

### 4.4 (Opcional) Concorrência com `SELECT ... FOR UPDATE`

Abra a conexão do Workbench **duas vezes** (duas abas de conexão; abas de query na mesma
conexão compartilham a sessão e não servem).

| Passo | Conexão 1 | Conexão 2 |
|---|---|---|
| 1 | `START TRANSACTION;` `SELECT * FROM rack WHERE rac_id = 1 FOR UPDATE;` | |
| 2 | | `START TRANSACTION;` `SELECT * FROM rack WHERE rac_id = 1 FOR UPDATE;` → **fica esperando** |
| 3 | `COMMIT;` | a consulta termina na hora |
| 4 | | `COMMIT;` |

É o que o `SELECT ... FROM rack ... FOR UPDATE` da `Procedure_movimentar_ativo` faz: duas pessoas não ocupam o mesmo slot ao mesmo
tempo, porque a segunda espera a primeira terminar e já vê o slot ocupado. Se esperar mais de
50 segundos, o MySQL desiste com erro 1205 (lock wait timeout).

---

## 5. Procedures (3 min)

**Explicação:** uma procedure é um bloco de SQL guardado no banco com um nome e executado com
`CALL`. Recebe parâmetros e, diferente da view, pode ter várias instruções, variáveis, `IF` e
controle de transação. Mostrar no script os 2 `CREATE PROCEDURE Procedure_...`.

### 5.1 Procedure simples (Workbench)

```sql
CALL Procedure_ativos_do_rack(1);   -- SRV-01 (1-2), SRV-02 (3-4), SW-01 (42)
```

### 5.2 A transação da movimentação: `Procedure_movimentar_ativo`

É a procedure que a API chama no `POST /movimentacoes` (parte 4). Aqui ela é chamada direto
no Workbench, sem o Java.

Parâmetros: `(ativo, rack destino, slot início, slot fim, motivo, usuário, @id)`. O último é de
saída (`OUT`): depois do `CALL`, `SELECT @id;` mostra o id da movimentação criada.
Rode um comando por vez:

```sql
SELECT COUNT(*) FROM movimentacao;   -- 3 (depois do passo 4.2)

-- Condição falsa: o ativo 99 não existe -> erro 1644 'Ativo não encontrado'
CALL Procedure_movimentar_ativo(99, 4, 5, 5, 'Teste', 1, @id);

-- O INSERT dá certo, o UPDATE é barrado pelo trigger (slot 1 é do SRV-01) -> erro 1644
CALL Procedure_movimentar_ativo(3, 1, 1, 1, 'Teste de rollback', 1, @id);

SELECT COUNT(*) FROM movimentacao;   -- continua 3: o handler desfez o INSERT

-- Todas as condições verdadeiras -> COMMIT
CALL Procedure_movimentar_ativo(7, 4, 10, 11, 'Reorganização do rack', 1, @id);
SELECT @id;                          -- id da nova movimentação

SELECT COUNT(*) FROM movimentacao;   -- 4
CALL Procedure_ativos_do_rack(4);    -- SRV-03 agora nos slots 10-11
SELECT log_acao, log_entidade, log_valor_anterior, log_valor_novo
  FROM log_auditoria ORDER BY log_id DESC LIMIT 2;   -- a auditoria gravada pela procedure
```

Ponto para falar: é o 4.3 de novo, mas agora o `ROLLBACK` é **automático**, feito pelo próprio
banco. A auditoria entra na mesma transação: no `CALL` que falhou, nenhum log ficou gravado.

---

## 6. Auditoria (1 min)

`GET /auditoria` → tudo o que foi alterado pela API durante a apresentação (configuração, aluguel,
movimentação), com quem fez, quando e os valores antes/depois em JSON.
Ponto para falar: o registro de auditoria é gravado **na mesma transação** da alteração; se a
alteração sofrer rollback, a auditoria também some.

Os dados de `dados_exemplo.sql` não aparecem aqui porque foram inseridos direto no banco, sem a API.

---

## Perguntas que o professor pode fazer

**Por que validar no Service se o trigger já valida?**
O trigger garante a regra para qualquer um que grave no banco. O Service devolve uma mensagem
melhor ("o slot 1 está ocupado pelo SRV-01") e evita ir ao banco à toa. São duas camadas.

**Se a procedure faz a transação, por que o Java também confere as condições?**
Para responder com o código HTTP e a mensagem certos (404 "ativo não encontrado", 409 "slot
ocupado pelo SRV-01"). Quem garante a regra é o banco: mesmo que alguém pule o Java, a
procedure e os triggers barram.

**Por que o Java não usa `setAutoCommit(false)` antes do `CALL`?**
Porque quem abre e fecha a transação é a procedure (`START TRANSACTION` / `COMMIT`). Um
`START TRANSACTION` dentro de uma transação já aberta faz o MySQL confirmar (commit implícito)
o que estava pendente, então as duas não podem ser misturadas.

**Por que não usar `@Transactional` ou JPA?**
Requisito do trabalho: JDBC puro, para controlar a transação à mão (`setAutoCommit(false)`,
`commit()`, `rollback()`) e escrever o SQL.

**A view guarda os dados?**
Não. Ela executa o `SELECT` a cada consulta, por isso o R04 mudou de 0% para 4,8% sem ninguém
atualizar a view.

**Por que a view aparece em minúsculas no Workbench?**
No Windows o MySQL guarda nomes de tabelas e views em minúsculas (`lower_case_table_names = 1`).
O nome funciona de qualquer jeito.

**O que acontece se a conexão cair no meio da movimentação?**
Nada fica gravado: sem `commit()`, o MySQL desfaz a transação sozinho quando a conexão fecha.
