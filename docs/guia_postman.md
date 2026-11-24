# Guia prático — testando a API pelo Postman

Este guia mostra como usar a coleção `docs/InfraTrace.postman_collection.json` para testar
toda a API, passo a passo, com o resultado esperado de cada requisição.

> Para a **apresentação** (views, triggers e transação), use o `docs/roteiro_apresentacao.md`,
> que parte dos dados de `database/dados_exemplo.sql`. Este guia é para **testar a API do zero**.

---

## 1. Preparação

1. **Banco do zero:** no MySQL Workbench, rode só `database/infratrace_mysql.sql`.
   **Não** rode o `dados_exemplo.sql`: a coleção cria os próprios dados, e os ids dela
   (sala 1, rack 1, ativo 1...) contam com um banco vazio.
2. **Suba a aplicação** no IntelliJ (com as variáveis `INFRATRACE_DB_*` configuradas).
   Espere aparecer no console algo como `Tomcat started on port 8080`.
3. **Importe a coleção** no Postman: *Import* → selecione `docs/InfraTrace.postman_collection.json`.
   Se já tinha importado antes, escolha *Replace* para pegar a versão nova.

Para recomeçar do zero a qualquer momento: rode o `infratrace_mysql.sql` de novo. Não precisa
reiniciar a aplicação.

---

## 2. Como o Postman funciona (o básico)

| Onde | Para que serve |
|---|---|
| **Variáveis da coleção** (clique na coleção → aba *Variables*) | `baseUrl` = `http://localhost:8080` e os ids usados nas rotas (`{{rackId}}`, `{{ativoId}}`...). Todos valem **1**, menos `usuarioId` = **2**. |
| **Botão *Send*** | Envia a requisição. |
| **Status** (canto superior direito da resposta) | `200 OK`, `201 Created`, `204 No Content`, `400`, `404`, `409`... |
| **Aba *Body*** da requisição | O JSON enviado nas rotas `POST`/`PUT`/`PATCH`. Pode editar à vontade. |
| **Aba *Headers*** | O cabeçalho `X-Usuario-Id` vem **desmarcado**. Sem ele, a alteração fica no nome do Administrador (id 1). Marque a caixinha para registrar no nome do usuário `{{usuarioId}}` (técnico). |
| **Aba *Params*** | Parâmetros da URL, como `?salaId=1`. Os opcionais vêm desmarcados. |

### O que cada status significa nesta API

| Status | Significado | Exemplo |
|---|---|---|
| **200** | Deu certo (consulta ou alteração) | `GET /racks` |
| **201** | Registro criado | `POST /salas` |
| **204** | Excluído (resposta sem corpo) | `DELETE /anexos/1` |
| **400** | Dado inválido: campo faltando, formato errado, slot acima da capacidade | slot 45 num rack de 44 |
| **404** | Registro não encontrado | `GET /ativos/999` |
| **409** | Conflito com uma regra: slot ocupado, aluguel sobreposto, registro em uso | dois ativos no mesmo slot |
| **413** | Arquivo de anexo maior que 10 MB | |
| **500** | Erro inesperado: olhe o console do IntelliJ | |

Nos erros, a resposta sempre traz um campo **`mensagem`** explicando o motivo.

---

## 3. Roteiro de teste (siga a ordem das pastas)

As requisições estão na mesma ordem da coleção. Algumas **exclusões e inativações ficam para o
fim** (seção 4), porque apagariam dados que os passos seguintes usam. Elas estão marcadas como
**⏭ pular agora**.

### Pasta 1. Usuários

| Requisição | Esperado |
|---|---|
| Listar | 200: só o Administrador (id 1) |
| Buscar | 404 por enquanto (o usuário 2 ainda não existe). Rode de novo depois de criar o técnico. |
| Criar técnico | 201: id **2** |
| Criar auditor | 201: id **3** |
| Buscar | 200: o técnico |
| Alterar | 200: nome vira "Técnico Sênior" |
| Inativar | ⏭ pular agora |

### Pasta 2. Infraestrutura

| Requisição | Esperado |
|---|---|
| Listar salas | 200: lista vazia `[]` |
| Criar sala | 201: id 1 |
| Alterar sala | 200 |
| Excluir sala | ⏭ pular agora |
| Listar fileiras da sala | 200: `[]` |
| Criar fileira | 201: id 1 |
| Alterar fileira | 200: nome "A1" |
| Listar racks | 200: `[]` |
| Criar rack 42U | 201: id 1 |
| Alterar rack | 200: agora com **44** slots |
| Ocupação do rack | 200: 0 ocupados, livres de 1 a 44 |
| Painel de ocupação de todos os racks (view) | 200: o R01 com `percentualOcupacao: 0` (lido da `View_ocupacao_rack`) |
| Excluir rack | ⏭ pular agora |

### Pasta 3. Cadastros auxiliares

| Requisição | Esperado |
|---|---|
| Listar tipos de ativo | 200: os 7 tipos do script (Servidor = 1, Switch = 2...) |
| Criar tipo de ativo | 201: "Nobreak", id 8 |
| Listar tipos de cliente | 200: os 3 tipos do script |
| Criar tipo de cliente | 201: "Educação", id 4 |
| Listar notas fiscais | 200: `[]` |
| Criar nota fiscal | 201: id 1 (o CNPJ com pontos e barra é aceito e salvo só com os números) |

### Pasta 4. Ativos

| Requisição | Esperado |
|---|---|
| Criar já instalado (slots 1-2) | 201: SRV-01, id 1, no rack 1, slots 1-2 |
| Criar no estoque | 201: SW-01, id 2, sem rack |
| Erro: slots sobrepostos (409) | **409**: os slots 2-3 batem com o SRV-01 |
| Erro: acima da capacidade (400) | **400**: slot 45 num rack de 44 |
| Listar | 200: os 2 ativos |
| Listar por status e rack | 200: só o SRV-01 |
| Buscar | 200: o SRV-01 |
| Alterar dados | 200: garantia muda para 2029-08-10 |
| Histórico de movimentações | 200: **1** movimentação (cadastrar já instalado conta como entrada no rack) |
| Histórico de manutenções | 200: `[]` |
| Descartar | ⏭ pular agora |
| Excluir (só sem histórico) | **409**: o SRV-01 tem histórico e não pode ser excluído. Esse é o resultado esperado. |

Confira também: **Ocupação do rack** (pasta 2) agora mostra o SRV-01 nos slots 1-2.

### Pasta 5. Anexos

| Requisição | Esperado |
|---|---|
| Listar anexos do ativo | 200: `[]` |
| Enviar anexo | Na aba *Body* (form-data), no campo **arquivo** clique em *Select Files* e escolha qualquer PDF ou imagem. O campo **tipo** aceita `NOTA_FISCAL`, `MANUAL`, `CONTRATO` ou `OUTRO`. → 201: id 1 |
| Dados do anexo | 200: nome original, tipo e data |
| Baixar arquivo | Use a setinha ao lado de *Send* → **Send and Download** e salve o arquivo. |
| Excluir anexo | 204. Rodando *Dados do anexo* de novo → 404. |

### Pasta 6. Movimentações (transação)

| Requisição | Esperado |
|---|---|
| Listar | 200: 1 movimentação (a instalação do SRV-01) |
| Mover ativo 2 do estoque para slots 10-11 | 201: o SW-01 entra no rack 1 |
| Erro: mover para slot ocupado (409) | **409**: o slot 1 é do SRV-01. **Nada é gravado** (rollback). |
| Mandar ativo 2 para o estoque | 201: o SW-01 sai do rack |
| Listar | 200: **3** movimentações. A tentativa com erro não deixou registro. |

Ponto de estudo: cada movimentação faz `INSERT movimentacao` + `UPDATE ativo` + auditoria na
mesma transação, feita no MySQL pela `Procedure_movimentar_ativo` (chamada pelo `MovimentacaoService`). Confira com `GET /ativos/2`: o `rackId` acompanha cada
movimentação.

### Pasta 7. Manutenções

| Requisição | Esperado |
|---|---|
| Listar | 200: `[]` |
| Abrir manutenção | 201: id 1. O SRV-01 passa a `EM_MANUTENCAO` (confira em *Buscar*, pasta 4) |
| Alterar | 200 |
| Finalizar | 200: com `dataFim` |
| Listar | 200: 1 manutenção, finalizada |

### Pasta 8. Clientes e aluguéis

| Requisição | Esperado |
|---|---|
| Listar clientes ativos | 200: `[]` |
| Criar cliente | 201: ACME, id 1 |
| Alterar cliente | 200 |
| Inativar cliente | ⏭ pular agora (cliente inativo não pode alugar) |
| Listar aluguéis | 200: `[]` |
| Criar aluguel | 201: id 1, rack 1 de 01/12/2026 a 30/11/2027 |
| Erro: aluguel sobreposto (409) | **409**: o período bate com o aluguel 1 (trigger `Trigger_aluguel_periodo_*` + Service) |
| Alterar aluguel | 200: até 31/12/2027 e valor 3800 |
| Excluir aluguel | ⏭ pular agora |

### Pasta 9. Configurações, alertas e auditoria

| Requisição | Esperado |
|---|---|
| Listar configurações | 200: `alerta_garantia_dias` e `alerta_aluguel_dias`, ambos 30 |
| Alterar prazo de garantia | 200: passa a 60. Valores aceitos: 30, 60 ou 90 (outro valor → 400). |
| Garantias vencendo | 200: `diasAntecedencia: 60`, `quantidade: 0`, porque a garantia do SRV-01 foi para 2029 em *Alterar dados*. Lido da `View_garantia_ativo`. |
| Aluguéis vencendo | 200: `quantidade: 0` (o aluguel termina em 2027) |
| Auditoria (últimos 50) | 200: tudo o que foi feito até aqui, com `valorAnterior` e `valorNovo` em JSON |
| Auditoria de um ativo | 200: só os registros do SRV-01 |

Quer ver um alerta aparecendo? No *Body* de **Alterar dados** (pasta 4), troque a `garantia`
para uma data daqui a uns 20 dias, envie, e rode **Garantias vencendo** de novo → `quantidade: 1`,
com `diasRestantes`.

---

## 4. Exclusões e inativações (por último, nesta ordem)

| # | Requisição | Esperado |
|---|---|---|
| 1 | Pasta 8 → Excluir aluguel | 204 |
| 2 | Pasta 8 → Inativar cliente | 200: `ativo: false`. Listar clientes ativos → `[]` |
| 3 | Pasta 4 → Descartar | 200: o SRV-01 vira `DESCARTADO` e sai do rack. Histórico de movimentações → 2 (o descarte gera a ida para o estoque). |
| 4 | Pasta 2 → Excluir rack | **409**: o rack tem histórico de movimentações. O banco protege o histórico (chave estrangeira, erro 1451). |
| 5 | Pasta 2 → Excluir sala | **409**: a sala tem fileira |
| 6 | Pasta 1 → Inativar | 200: o técnico (id 2) fica inativo. Deixe o `X-Usuario-Id` **desmarcado**: ninguém pode inativar a si mesmo. |
| 7 | Qualquer `POST` com `X-Usuario-Id` marcado (= 2) | **400**: o usuário 2 está inativo |

Os 409 dos passos 4 e 5 são o comportamento correto: o sistema não apaga o que tem histórico.

---

## 5. Problemas comuns

| Sintoma | Causa provável | Solução |
|---|---|---|
| `Error: connect ECONNREFUSED 127.0.0.1:8080` | A aplicação não está rodando | Suba pelo IntelliJ e espere o `Tomcat started` |
| 500 em qualquer rota | Banco desligado ou variáveis `INFRATRACE_DB_*` erradas | Veja o erro no console do IntelliJ |
| 404 em *Buscar*/*Alterar* | O id da variável não existe (banco não estava vazio, ou o passo de criar foi pulado) | Confira o id na resposta do *Criar* e ajuste em *Variables*, ou recomece do zero |
| 409 inesperado em *Criar* | O registro já existe (nome, e-mail, número de série ou CNPJ repetido) | Recomece do zero ou mude o valor no *Body* |
| 400 "Corpo da requisição inválido..." | Vírgula ou aspas faltando no *Body*, ou data fora do formato `AAAA-MM-DD` | Corrija o JSON (o Postman sublinha o erro em vermelho) |
| A rota nova não aparece | Coleção antiga importada | Importe de novo escolhendo *Replace* |

Para ver exatamente o que foi enviado e recebido: menu *View* → **Show Postman Console**.

---

## 6. Teste automático (opcional)

O `docs/teste_api.ps1` faz um caminho parecido com este guia de forma automática (84 verificações).
Com o banco recém-criado e a aplicação rodando, no PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File docs\teste_api.ps1
```

No fim ele mostra quantas verificações passaram e quantas falharam.
