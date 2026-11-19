# InfraTrace — Documentação da API

API REST para gestão de ativos de TI e ocupação de racks em data center.

- **Base URL:** `http://localhost:8080`
- **Formato:** JSON (UTF-8). Datas em `AAAA-MM-DD`; data e hora em `AAAA-MM-DDTHH:MM:SS`.
- **Coleção do Postman:** [`InfraTrace.postman_collection.json`](InfraTrace.postman_collection.json)
- **Teste automático de ponta a ponta:** [`teste_api.ps1`](teste_api.ps1) (precisa de um banco recém-criado)

---

## 1. Acesso e usuário responsável

Nesta fase **não há login**: todas as rotas podem ser usadas direto, sem token.

O banco, porém, exige que movimentações, manutenções e o log de auditoria tenham um usuário
responsável (`fk_usr_id NOT NULL`). Por isso, toda alteração (POST, PUT, PATCH e DELETE) é
registrada em nome de alguém:

- **Sem nada a mais:** quem responde é o usuário padrão, o **Administrador** (id 1), criado pelo
  script do banco.
- **Com o cabeçalho `X-Usuario-Id`:** para registrar a ação em nome de outro usuário cadastrado,
  envie o id dele.

  ```
  X-Usuario-Id: 2
  ```

  Se o id não existir, o usuário estiver inativo ou o valor não for um número, a resposta é **400**.

O perfil (`ADMIN`, `TECNICO`, `AUDITOR`) é guardado no cadastro, mas ainda não limita o que cada
um pode fazer.

---

## 2. Erros

Todo erro tem o mesmo formato:

```json
{ "status": 409, "erro": "Conflict", "mensagem": "Slots 2-3 do rack R01 já estão ocupados pelo ativo 'SRV-01' (id 1, slots 1-2)", "dataHora": "2026-11-18T10:15:00" }
```

| Status | Quando |
|---|---|
| 400 | Campo obrigatório faltando, valor inválido, JSON malformado, slot acima da capacidade, `X-Usuario-Id` inválido. |
| 404 | Registro ou rota inexistente. |
| 409 | Conflito com os dados: slot ocupado, aluguel sobreposto, valor duplicado (UNIQUE), exclusão de registro em uso (FK), erro 1644 dos triggers. |
| 413 | Anexo maior que 10 MB. |
| 500 | Erro inesperado (aparece no console da aplicação). |

---

## 3. Endpoints

`{id}` é sempre um número inteiro. Os campos marcados com * são obrigatórios.

### Infraestrutura física: sala → fileira → rack

| Método | Rota | Corpo / parâmetros |
|---|---|---|
| GET | `/salas` · `/salas/{id}` | |
| POST · PUT | `/salas` · `/salas/{id}` | `nome`*, `descricao` |
| DELETE | `/salas/{id}` | 409 se tiver fileiras |
| GET | `/fileiras?salaId=` · `/fileiras/{id}` | |
| POST · PUT | `/fileiras` · `/fileiras/{id}` | `nome`* (até 10 caracteres, único na sala), `salaId`* |
| DELETE | `/fileiras/{id}` | 409 se tiver racks |
| GET | `/racks?fileiraId=` · `/racks/{id}` | |
| GET | `/racks/{id}/ocupacao` | Mapa de ocupação (ver abaixo) |
| POST · PUT | `/racks` · `/racks/{id}` | `nome`*, `totalSlots`* (1 a 60), `fileiraId`* |
| DELETE | `/racks/{id}` | 409 se tiver ativos, aluguéis ou movimentações |

Exemplo de `GET /racks/1/ocupacao`:

```json
{
  "rackId": 1, "rackNome": "R01", "totalSlots": 42,
  "slotsOcupados": 4, "slotsLivres": 38, "percentualOcupacao": 9.5,
  "ocupados": [
    { "ativoId": 1, "ativoNome": "SRV-01", "status": "ATIVO", "slotInicio": 1, "slotFim": 2, "tamanho": 2 },
    { "ativoId": 2, "ativoNome": "SW-01",  "status": "ATIVO", "slotInicio": 11, "slotFim": 12, "tamanho": 2 }
  ],
  "livres": [
    { "slotInicio": 3,  "slotFim": 10, "tamanho": 8 },
    { "slotInicio": 13, "slotFim": 42, "tamanho": 30 }
  ]
}
```

### Cadastros auxiliares

| Método | Rota | Corpo |
|---|---|---|
| GET · POST | `/tipos-ativo` | `nome`*, `descricao` |
| GET · PUT · DELETE | `/tipos-ativo/{id}` | |
| GET · POST | `/tipos-cliente` | `nome`* |
| GET · PUT · DELETE | `/tipos-cliente/{id}` | |
| GET · POST | `/notas-fiscais` | `num`*, `valor`*, `razaoSocial`*, `cnpj`* (validado), `chave` (44 dígitos), `volumes`, `endereco`, `inscricao`, `descricao` |
| GET · PUT · DELETE | `/notas-fiscais/{id}` | |

### Ativos

| Método | Rota | Descrição |
|---|---|---|
| GET | `/ativos?status=&rackId=` | Lista com filtros opcionais |
| GET | `/ativos/{id}` | |
| GET | `/ativos/{id}/movimentacoes` | Histórico de posições |
| GET | `/ativos/{id}/manutencoes` | Histórico de manutenções |
| POST | `/ativos` | Cadastra; pode já nascer instalado num rack |
| PUT | `/ativos/{id}` | Altera dados cadastrais e status. **Não muda a posição** (use `/movimentacoes`). |
| POST | `/ativos/{id}/descarte` | `{ "motivo": "...", "data": "2026-11-18" }`. A data é opcional (padrão: hoje). |
| DELETE | `/ativos/{id}` | Só funciona se o ativo não tiver histórico; caso contrário, use o descarte |

Corpo do ativo:

```json
{
  "nome": "SRV-01", "fabricante": "Dell", "modelo": "R750", "numSerie": "SN001",
  "patrimonio": "PAT-0001", "tipoAtivoId": 1, "notaFiscalId": 1,
  "dataAquisicao": "2026-08-10", "valorCompra": 25000.50, "garantia": "2029-08-10",
  "status": "ATIVO",
  "rackId": 1, "slotInicio": 1, "slotFim": 2
}
```

- Obrigatórios: `nome`, `fabricante`, `modelo`, `numSerie` (único), `tipoAtivoId`.
- `status`: `ATIVO` (padrão), `INATIVO`, `EM_MANUTENCAO` ou `AGUARDANDO_DESCARTE`. `DESCARTADO` só pelo endpoint de descarte.
- `rackId`, `slotInicio` e `slotFim` vêm **juntos** ou não vêm. Sem eles, o ativo fica no estoque.

### Anexos de ativos

| Método | Rota | Descrição |
|---|---|---|
| GET | `/ativos/{id}/anexos` | Anexos do ativo |
| POST | `/ativos/{id}/anexos` | Envia um arquivo (**multipart/form-data**) |
| GET | `/anexos/{id}` | Dados do anexo |
| GET | `/anexos/{id}/download` | Baixa o arquivo com o nome original |
| DELETE | `/anexos/{id}` | Remove o registro e o arquivo |

Envio no Postman: *Body → form-data*, com dois campos:

| Campo | Tipo | Valor |
|---|---|---|
| `tipo` | Text | `NOTA_FISCAL`, `MANUAL`, `CONTRATO` ou `OUTRO` |
| `arquivo` | File | pdf, png, jpg, jpeg, txt, csv, doc, docx, xls, xlsx, odt ou ods; até 10 MB |

Resposta:

```json
{ "id": 1, "tipo": "MANUAL", "nomeOriginal": "manual servidor.pdf", "tamanhoBytes": 48213,
  "enviadoEm": "2026-11-18T14:02:11", "ativoId": 2, "download": "/anexos/1/download" }
```

- Os arquivos ficam na pasta da variável `INFRATRACE_ANEXOS_DIR`. Se ela não for definida, a
  pasta é `anexos/`, dentro do diretório onde a aplicação foi iniciada; essa pasta não vai para
  o Git. No disco, cada arquivo é salvo com um nome aleatório (UUID); o nome original fica só no banco.
- Um ativo com anexos não pode ser excluído (FK → 409); use o descarte.
- Para baixar no Postman, use **Send and Download**, na seta ao lado de *Send*.

### Movimentações (transação manual)

| Método | Rota | Descrição |
|---|---|---|
| GET | `/movimentacoes?ativoId=` · `/movimentacoes/{id}` | Histórico (não há PUT nem DELETE) |
| POST | `/movimentacoes` | Move o ativo |

```json
{ "ativoId": 2, "rackDestinoId": 1, "slotInicioDestino": 10, "slotFimDestino": 11, "motivo": "Reorganização" }
```

- A **origem** não é enviada: o sistema usa a posição atual do ativo.
- Para mandar ao **estoque**, envie só `ativoId` e `motivo` (destino nulo).
- Tudo acontece numa única transação: INSERT em `movimentacao` + UPDATE em `ativo` + log. Se algo falhar, nada é gravado.

### Manutenções

| Método | Rota | Corpo |
|---|---|---|
| GET | `/manutencoes?ativoId=` · `/manutencoes/{id}` | |
| POST | `/manutencoes` | `ativoId`*, `tipo`* (`PREVENTIVA`/`CORRETIVA`), `descricao`*, `responsavel`*, `dataInicio`*, `dataFim` |
| PUT | `/manutencoes/{id}` | `tipo`*, `descricao`*, `responsavel`*, `dataInicio`* (a data de fim só muda por `/finalizar`) |
| PATCH | `/manutencoes/{id}/finalizar` | `{ "dataFim": "2026-11-20" }`. O corpo é opcional (padrão: hoje). |

Abrir uma manutenção sem `dataFim` coloca o ativo em `EM_MANUTENCAO`. Ao finalizar, ele volta para `ATIVO`.

### Clientes e aluguéis

| Método | Rota | Corpo |
|---|---|---|
| GET | `/clientes?ativo=true` · `/clientes/{id}` | |
| POST · PUT | `/clientes` · `/clientes/{id}` | `nome`*, `cnpj`* (com ou sem máscara; validado), `representante`*, `tipoClienteId`* |
| PATCH | `/clientes/{id}/inativar` | Clientes não são excluídos |
| GET | `/alugueis?clienteId=&rackId=` · `/alugueis/{id}` | |
| POST · PUT | `/alugueis` · `/alugueis/{id}` | `dataInicio`*, `dataFim`*, `valor`* (mensal), `clienteId`* (ativo), `rackId`* |
| DELETE | `/alugueis/{id}` | |

O mesmo rack não pode ter dois aluguéis com períodos sobrepostos (409).

### Usuários

| Método | Rota | Corpo |
|---|---|---|
| GET | `/usuarios` · `/usuarios/{id}` | |
| POST | `/usuarios` | `nome`*, `email`* (único), `perfil`* (`ADMIN`, `TECNICO` ou `AUDITOR`) |
| PUT | `/usuarios/{id}` | `nome`*, `email`*, `perfil`* |
| PATCH | `/usuarios/{id}/inativar` | Usuários não são excluídos. O responsável pela requisição não pode inativar a si mesmo, e o sistema não pode ficar sem ADMIN ativo. |

### Configurações, alertas e auditoria

| Método | Rota | Descrição |
|---|---|---|
| GET | `/configuracoes` · `/configuracoes/{chave}` | |
| PUT | `/configuracoes/{chave}` | `{ "valor": "60" }`. `alerta_garantia_dias`: 30, 60 ou 90. `alerta_aluguel_dias`: 15, 30 ou 60. |
| GET | `/alertas/garantias` | Ativos com garantia vencendo dentro do prazo configurado |
| GET | `/alertas/alugueis` | Aluguéis terminando dentro do prazo configurado |
| GET | `/auditoria?entidade=&entidadeId=&usuarioId=&limite=` | Log de alterações. `limite`: padrão 200, máximo 1000. |

Cada alteração gera uma linha em `log_auditoria`, com os valores anterior e novo em JSON, **na
mesma transação** da alteração.

---

## 4. Roteiro sugerido de teste no Postman

1. `GET /usuarios` (o Administrador já existe) → `POST /usuarios` (um técnico)
2. `POST /salas` → `POST /fileiras` → `POST /racks` (42 slots)
3. `POST /ativos` com `rackId` e slots 1-2 → `POST /ativos` sem rack (estoque)
4. `POST /movimentacoes` levando o segundo ativo para os slots 10-11
5. `GET /racks/1/ocupacao`
6. Tente conflitos: ativo nos slots 2-3 (409), slot 43 (400), aluguel sobreposto (409)
7. `POST /manutencoes` → `GET /ativos/1` (EM_MANUTENCAO) → `PATCH /manutencoes/1/finalizar`
8. `POST /ativos/1/anexos` (form-data) → `GET /anexos/1/download`
9. `POST /ativos/1/descarte` → `GET /ativos/1/movimentacoes`
10. `GET /auditoria`
