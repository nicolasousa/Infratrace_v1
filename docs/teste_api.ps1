# =====================================================================
# InfraTrace - teste automático de ponta a ponta da API
#
# Faz ~85 chamadas HTTP reais (usuários, racks, slots, movimentação,
# manutenção, anexos, aluguéis, alertas, descarte, auditoria) e confere o
# status e o conteúdo de cada resposta. Mostra OK/FALHA por linha e um resumo.
#
# Pré-requisitos:
#   1. Banco RECÉM-CRIADO (rode database/infratrace_mysql.sql antes),
#      porque o teste conta registros e usa os ids 1, 2, 3...
#   2. API rodando em http://localhost:8080 (InfratraceApplication).
#
# Como rodar (PowerShell, na raiz do projeto):
#   powershell -ExecutionPolicy Bypass -File docs\teste_api.ps1
# =====================================================================

$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'
$script:ok = 0; $script:falhas = 0

# $usuario (opcional) vira o cabeçalho X-Usuario-Id: quem fica registrado como responsável
function Chamar($metodo, $rota, $corpo = $null, $usuario = $null) {
    $h = @{}
    if ($usuario) { $h['X-Usuario-Id'] = "$usuario" }
    $p = @{ Uri = "$base$rota"; Method = $metodo; Headers = $h; UseBasicParsing = $true }
    if ($null -ne $corpo) {
        $json = if ($corpo -is [string]) { $corpo } else { $corpo | ConvertTo-Json -Depth 5 }
        $p['Body'] = [Text.Encoding]::UTF8.GetBytes($json); $p['ContentType'] = 'application/json; charset=utf-8'
    }
    try {
        $r = Invoke-WebRequest @p
        $status = [int]$r.StatusCode
        $txt = [Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
    } catch [System.Net.WebException] {
        $resp = $_.Exception.Response
        $status = [int]$resp.StatusCode
        $ms = New-Object IO.MemoryStream; $resp.GetResponseStream().CopyTo($ms)
        $txt = [Text.Encoding]::UTF8.GetString($ms.ToArray())
    }
    $obj = $null; if ($txt) { try { $obj = $txt | ConvertFrom-Json } catch {} }
    [pscustomobject]@{ Status = $status; Json = $obj; Texto = $txt }
}

function Esperar($nome, $resp, $statusEsperado, [scriptblock]$cond = $null) {
    $passou = $resp.Status -eq $statusEsperado
    if ($passou -and $cond) { $passou = [bool](& $cond $resp.Json) }
    if ($passou) { $script:ok++; $marca = 'OK  ' } else { $script:falhas++; $marca = 'FALHA' }
    $msg = if ($resp.Json -and $resp.Json.mensagem) { " -> " + $resp.Json.mensagem } else { '' }
    Write-Output ("[{0}] {1} (HTTP {2}){3}" -f $marca, $nome, $resp.Status, $msg)
    if (-not $passou -and -not $msg) { Write-Output "        corpo: $($resp.Texto)" }
}

# Envio multipart/form-data (o Invoke-WebRequest do PowerShell 5.1 não tem -Form; usamos o curl.exe do Windows)
function EnviarArquivo($rota, $tipo, $arquivo, $usuario = $null) {
    $saida = [IO.Path]::GetTempFileName()
    $campos = @()
    if ($usuario) { $campos += @('-H', "X-Usuario-Id: $usuario") }
    if ($tipo) { $campos += @('-F', "tipo=$tipo") }
    if ($arquivo) { $campos += @('-F', "arquivo=@$arquivo") }
    $status = & curl.exe -s -o $saida -w '%{http_code}' @campos "$base$rota"
    $txt = [IO.File]::ReadAllText($saida, [Text.Encoding]::UTF8); Remove-Item $saida
    $obj = $null; if ($txt) { try { $obj = $txt | ConvertFrom-Json } catch {} }
    [pscustomobject]@{ Status = [int]$status; Json = $obj; Texto = $txt }
}

function Baixar($rota, $destino) {
    [int](& curl.exe -s -o $destino -w '%{http_code}' "$base$rota")
}

$hoje = Get-Date
$d = { param($dias) $hoje.AddDays($dias).ToString('yyyy-MM-dd') }

Write-Output '--- Usuários e responsável pelas alterações'
Esperar 'GET /usuarios: só o Administrador do script' (Chamar GET '/usuarios') 200 { param($j) @($j).Count -eq 1 -and $j[0].perfil -eq 'ADMIN' -and -not $j[0].PSObject.Properties['senhaHash'] }
Esperar 'GET /tipos-ativo (7 tipos)' (Chamar GET '/tipos-ativo') 200 { param($j) @($j).Count -eq 7 }
$r = Chamar POST '/usuarios' @{nome='Técnico'; email='Tec@InfraTrace.local'; perfil='TECNICO'}; Esperar 'Criar TECNICO -> 201' $r 201 { param($j) $j.email -eq 'tec@infratrace.local' }
$tecId = $r.Json.id
Esperar 'Criar AUDITOR -> 201' (Chamar POST '/usuarios' @{nome='Auditora'; email='aud@infratrace.local'; perfil='AUDITOR'}) 201
Esperar 'E-mail repetido -> 409' (Chamar POST '/usuarios' @{nome='X'; email='TEC@infratrace.local'; perfil='TECNICO'}) 409
Esperar 'Perfil inválido -> 400' (Chamar POST '/usuarios' @{nome='X'; email='x@infratrace.local'; perfil='CHEFE'}) 400
Esperar 'X-Usuario-Id não numérico -> 400' (Chamar POST '/salas' @{nome='Sala X'} 'abc') 400
Esperar 'X-Usuario-Id inexistente -> 400' (Chamar POST '/salas' @{nome='Sala X'} 999) 400

Write-Output '--- Infraestrutura física'
$r = Chamar POST '/salas' @{nome='Sala 1'; descricao='Térreo'}; Esperar 'Criar sala -> 201' $r 201; $sala = $r.Json.id
Esperar 'Sala com nome repetido -> 409' (Chamar POST '/salas' @{nome='Sala 1'}) 409
$r = Chamar POST '/fileiras' @{nome='A'; salaId=$sala}; Esperar 'Criar fileira -> 201' $r 201; $fil = $r.Json.id
Esperar 'Fileira em sala inexistente -> 400' (Chamar POST '/fileiras' @{nome='B'; salaId=999}) 400
$r = Chamar POST '/racks' @{nome='R01'; totalSlots=42; fileiraId=$fil}; Esperar 'Criar rack 42U -> 201' $r 201; $r1 = $r.Json.id
$r2 = (Chamar POST '/racks' @{nome='R02'; totalSlots=24; fileiraId=$fil}).Json.id
Esperar 'Rack com 70 slots -> 400' (Chamar POST '/racks' @{nome='R99'; totalSlots=70; fileiraId=$fil}) 400

Write-Output '--- Ativos e ocupação dos racks (feitos pelo TECNICO via X-Usuario-Id)'
$r = Chamar POST '/notas-fiscais' @{num=1001; valor=25000.50; razaoSocial='Fornecedor TI Ltda'; cnpj='11.222.333/0001-81'} $tecId; Esperar 'Nota fiscal -> 201' $r 201 { param($j) $j.cnpj -eq '11222333000181' }; $nf = $r.Json.id
$r = Chamar POST '/ativos' @{nome='SRV-01'; fabricante='Dell'; modelo='R750'; numSerie='SN001'; tipoAtivoId=1; notaFiscalId=$nf; rackId=$r1; slotInicio=1; slotFim=2; valorCompra=25000.50; dataAquisicao=(& $d -100); garantia=(& $d 20)} $tecId
Esperar 'Ativo instalado nos slots 1-2 -> 201' $r 201 { param($j) $j.rackId -eq $r1 -and $j.status -eq 'ATIVO' }; $a1 = $r.Json.id
Esperar 'Ativo nos slots 2-3 (sobreposto) -> 409' (Chamar POST '/ativos' @{nome='SRV-02'; fabricante='HP'; modelo='DL380'; numSerie='SN002'; tipoAtivoId=1; rackId=$r1; slotInicio=2; slotFim=3} $tecId) 409
Esperar 'Ativo nos slots 41-43 (passa de 42U) -> 400' (Chamar POST '/ativos' @{nome='SW-01'; fabricante='Cisco'; modelo='9300'; numSerie='SN003'; tipoAtivoId=2; rackId=$r1; slotInicio=41; slotFim=43} $tecId) 400
Esperar 'Rack sem slots -> 400' (Chamar POST '/ativos' @{nome='SW-01'; fabricante='Cisco'; modelo='9300'; numSerie='SN003'; tipoAtivoId=2; rackId=$r1} $tecId) 400
$r = Chamar POST '/ativos' @{nome='SW-01'; fabricante='Cisco'; modelo='9300'; numSerie='SN003'; tipoAtivoId=2} $tecId; Esperar 'Ativo no estoque -> 201' $r 201 { param($j) $null -eq $j.rackId }; $a2 = $r.Json.id
Esperar 'Número de série repetido -> 409' (Chamar POST '/ativos' @{nome='X'; fabricante='X'; modelo='X'; numSerie='SN003'; tipoAtivoId=2}) 409
Esperar 'Status DESCARTADO via cadastro -> 400' (Chamar POST '/ativos' @{nome='X'; fabricante='X'; modelo='X'; numSerie='SN009'; tipoAtivoId=2; status='DESCARTADO'}) 400

Write-Output '--- Movimentação (transação manual)'
$r = Chamar POST '/movimentacoes' @{ativoId=$a2; rackDestinoId=$r1; slotInicioDestino=10; slotFimDestino=10; motivo='Instalação'} $tecId
Esperar 'Estoque -> R01 slot 10 -> 201' $r 201 { param($j) $null -eq $j.rackOrigemId -and $j.rackDestinoId -eq $r1 -and $j.usuarioId -eq $tecId }
Esperar 'Ativo atualizado pela movimentação' (Chamar GET "/ativos/$a2") 200 { param($j) $j.rackId -eq $r1 -and $j.slotInicio -eq 10 }
Esperar 'Mover para slot ocupado (1) -> 409' (Chamar POST '/movimentacoes' @{ativoId=$a2; rackDestinoId=$r1; slotInicioDestino=1; slotFimDestino=1}) 409
Esperar 'Ativo continua no slot 10' (Chamar GET "/ativos/$a2") 200 { param($j) $j.slotInicio -eq 10 }
Esperar 'Mesma posição -> 409' (Chamar POST '/movimentacoes' @{ativoId=$a2; rackDestinoId=$r1; slotInicioDestino=10; slotFimDestino=10}) 409
$r = Chamar POST '/movimentacoes' @{ativoId=$a2; rackDestinoId=$r1; slotInicioDestino=11; slotFimDestino=12; motivo='Deslizar no mesmo rack'}
Esperar 'Deslizar no mesmo rack (10 -> 11-12) -> 201' $r 201 { param($j) $j.slotInicioOrigem -eq 10 -and $j.slotFimDestino -eq 12 -and $j.usuarioId -eq 1 }
Esperar 'Histórico do ativo (2 movimentações)' (Chamar GET "/ativos/$a2/movimentacoes") 200 { param($j) @($j).Count -eq 2 }
Esperar 'Ativo com instalação no cadastro tem histórico' (Chamar GET "/ativos/$a1/movimentacoes") 200 { param($j) @($j).Count -eq 1 }
$r = Chamar GET "/racks/$r1/ocupacao"
Esperar 'Ocupação R01: 4 de 42 ocupados' $r 200 { param($j) $j.slotsOcupados -eq 4 -and $j.slotsLivres -eq 38 -and @($j.livres).Count -eq 2 -and $j.livres[0].slotInicio -eq 3 }
Write-Output ("        livres: " + (($r.Json.livres | ForEach-Object { "$($_.slotInicio)-$($_.slotFim)" }) -join ', ') + " | ocupado: $($r.Json.percentualOcupacao)%")
Esperar 'Reduzir R01 para 5U (ativo até o 12) -> 409' (Chamar PUT "/racks/$r1" @{nome='R01'; totalSlots=5; fileiraId=$fil}) 409
Esperar 'Reduzir R01 para 20U -> 200' (Chamar PUT "/racks/$r1" @{nome='R01'; totalSlots=20; fileiraId=$fil}) 200
Esperar 'Excluir rack com ativos -> 409' (Chamar DELETE "/racks/$r1") 409

Write-Output '--- Manutenção'
$r = Chamar POST '/manutencoes' @{ativoId=$a1; tipo='corretiva'; descricao='Troca de fonte'; responsavel='Fornecedor'; dataInicio=(& $d 0)} $tecId; Esperar 'Abrir manutenção -> 201' $r 201 { param($j) $j.usuarioId -eq $tecId }; $man = $r.Json.id
Esperar 'Ativo ficou EM_MANUTENCAO' (Chamar GET "/ativos/$a1") 200 { param($j) $j.status -eq 'EM_MANUTENCAO' }
Esperar 'Segunda manutenção aberta -> 409' (Chamar POST '/manutencoes' @{ativoId=$a1; tipo='PREVENTIVA'; descricao='X'; responsavel='Y'; dataInicio=(& $d 0)}) 409
Esperar 'Finalizar antes do início -> 400' (Chamar PATCH "/manutencoes/$man/finalizar" @{dataFim=(& $d -5)}) 400
Esperar 'Finalizar manutenção -> 200' (Chamar PATCH "/manutencoes/$man/finalizar") 200 { param($j) $j.dataFim -ne $null }
Esperar 'Ativo voltou para ATIVO' (Chamar GET "/ativos/$a1") 200 { param($j) $j.status -eq 'ATIVO' }

Write-Output '--- Clientes e aluguéis'
Esperar 'CNPJ inválido -> 400' (Chamar POST '/clientes' @{nome='ACME'; cnpj='11.222.333/0001-00'; representante='Ana'; tipoClienteId=1}) 400
$r = Chamar POST '/clientes' @{nome='ACME S.A.'; cnpj='11.222.333/0001-81'; representante='Ana'; tipoClienteId=1}; Esperar 'Criar cliente -> 201' $r 201 { param($j) $j.ativo -eq $true }; $cli = $r.Json.id
Esperar 'CNPJ repetido -> 409' (Chamar POST '/clientes' @{nome='Outro'; cnpj='11222333000181'; representante='B'; tipoClienteId=2}) 409
Esperar 'Aluguel R01 -> 201' (Chamar POST '/alugueis' @{dataInicio=(& $d 30); dataFim=(& $d 395); valor=3500; clienteId=$cli; rackId=$r1}) 201
Esperar 'Aluguel sobreposto no R01 -> 409' (Chamar POST '/alugueis' @{dataInicio=(& $d 200); dataFim=(& $d 500); valor=3500; clienteId=$cli; rackId=$r1}) 409
Esperar 'Aluguel logo depois no R01 -> 201' (Chamar POST '/alugueis' @{dataInicio=(& $d 396); dataFim=(& $d 500); valor=3600; clienteId=$cli; rackId=$r1}) 201
Esperar 'Aluguel R02 vencendo em 10 dias -> 201' (Chamar POST '/alugueis' @{dataInicio=(& $d -20); dataFim=(& $d 10); valor=2000; clienteId=$cli; rackId=$r2}) 201
Esperar 'Fim antes do início -> 400' (Chamar POST '/alugueis' @{dataInicio=(& $d 10); dataFim=(& $d 5); valor=1; clienteId=$cli; rackId=$r2}) 400

Write-Output '--- Configurações e alertas'
Esperar 'Alerta de garantias (SRV-01 vence em 20 dias)' (Chamar GET '/alertas/garantias') 200 { param($j) $j.quantidade -eq 1 -and $j.diasAntecedencia -eq 30 }
Esperar 'View_garantia_ativo: diasRestantes = 20 e tipo Servidor' (Chamar GET '/alertas/garantias') 200 { param($j) $j.itens[0].diasRestantes -eq 20 -and $j.itens[0].tipo -eq 'Servidor' }
Esperar 'View_ocupacao_rack: painel com todos os racks' (Chamar GET '/racks/ocupacao') 200 { param($j) @($j | Where-Object { $_.rackId -eq $r1 -and $_.salaNome }).Count -eq 1 }
Esperar 'Alerta de aluguéis (R02 vence em 10 dias)' (Chamar GET '/alertas/alugueis') 200 { param($j) $j.quantidade -eq 1 }
Esperar 'Configuração com valor fora das opções -> 400' (Chamar PUT '/configuracoes/alerta_garantia_dias' @{valor='45'}) 400
Esperar 'Alterar para 60 dias -> 200' (Chamar PUT '/configuracoes/alerta_garantia_dias' @{valor='60'}) 200 { param($j) $j.valor -eq '60' }

Write-Output '--- Anexos'
$tmp = Join-Path ([IO.Path]::GetTempPath()) 'infratrace_teste_anexos'; New-Item -ItemType Directory -Force $tmp | Out-Null
$pdf = Join-Path $tmp 'manual servidor.pdf'; [IO.File]::WriteAllText($pdf, "%PDF-1.4 conteudo de teste $(Get-Random)")
$exe = Join-Path $tmp 'virus.exe'; [IO.File]::WriteAllText($exe, 'MZ')
$vazio = Join-Path $tmp 'vazio.pdf'; [IO.File]::WriteAllText($vazio, '')
$grande = Join-Path $tmp 'grande.pdf'; $fs = [IO.File]::Create($grande); $fs.SetLength(11MB); $fs.Close()
$pastaAnexos = $env:INFRATRACE_ANEXOS_DIR
$contarArquivos = { if ($pastaAnexos -and (Test-Path $pastaAnexos)) { @(Get-ChildItem $pastaAnexos -File).Count } else { -1 } }
$antes = & $contarArquivos

$r = EnviarArquivo "/ativos/$a2/anexos" 'manual' $pdf $tecId
Esperar 'Enviar PDF -> 201' $r 201 { param($j) $j.nomeOriginal -eq 'manual servidor.pdf' -and $j.tipo -eq 'MANUAL' -and $j.tamanhoBytes -gt 0 -and -not $j.PSObject.Properties['caminho'] }
$anx = $r.Json.id
Esperar 'Extensão .exe -> 400' (EnviarArquivo "/ativos/$a2/anexos" 'OUTRO' $exe) 400
Esperar 'Tipo inválido -> 400' (EnviarArquivo "/ativos/$a2/anexos" 'FOTO' $pdf) 400
Esperar 'Sem o campo arquivo -> 400' (EnviarArquivo "/ativos/$a2/anexos" 'MANUAL' $null) 400
Esperar 'Arquivo vazio -> 400' (EnviarArquivo "/ativos/$a2/anexos" 'MANUAL' $vazio) 400
Esperar 'Arquivo de 11 MB -> 413' (EnviarArquivo "/ativos/$a2/anexos" 'MANUAL' $grande) 413
Esperar 'Ativo inexistente -> 404' (EnviarArquivo '/ativos/9999/anexos' 'MANUAL' $pdf) 404
if ($antes -ge 0) {
    $depois = & $contarArquivos
    Esperar 'Só 1 arquivo novo no disco (erros não deixam órfãos)' ([pscustomobject]@{ Status = 200 }) 200 { param($j) $depois -eq $antes + 1 }
}
Esperar 'Listar anexos do ativo' (Chamar GET "/ativos/$a2/anexos") 200 { param($j) @($j).Count -eq 1 }
$baixado = Join-Path $tmp 'baixado.pdf'
$st = Baixar "/anexos/$anx/download" $baixado
Esperar 'Download devolve o mesmo conteúdo' ([pscustomobject]@{ Status = $st }) 200 { param($j) (Get-FileHash $baixado).Hash -eq (Get-FileHash $pdf).Hash }
Esperar 'Excluir anexo -> 204' (Chamar DELETE "/anexos/$anx") 204
if ($antes -ge 0) {
    $final = & $contarArquivos
    Esperar 'Arquivo apagado do disco' ([pscustomobject]@{ Status = 200 }) 200 { param($j) $final -eq $antes }
}
Esperar 'Anexo excluído -> 404' (Chamar GET "/anexos/$anx") 404
Esperar 'Novo anexo para o ativo 2 -> 201' (EnviarArquivo "/ativos/$a2/anexos" 'NOTA_FISCAL' $pdf) 201
Remove-Item -Recurse -Force $tmp

Write-Output '--- Descarte'
$r = Chamar POST "/ativos/$a1/descarte" @{motivo='Fim de vida útil'} $tecId
Esperar 'Descartar ativo instalado -> 200' $r 200 { param($j) $j.status -eq 'DESCARTADO' -and $null -eq $j.rackId -and $j.motivoDescarte }
Esperar 'Descarte gerou movimentação para o estoque' (Chamar GET "/ativos/$a1/movimentacoes") 200 { param($j) @($j).Count -eq 2 -and $null -eq $j[0].rackDestinoId }
Esperar 'Movimentar ativo descartado -> 409' (Chamar POST '/movimentacoes' @{ativoId=$a1; rackDestinoId=$r1; slotInicioDestino=1; slotFimDestino=1}) 409
Esperar 'Descartar de novo -> 409' (Chamar POST "/ativos/$a1/descarte" @{motivo='x'}) 409
Esperar 'Excluir ativo com histórico -> 409' (Chamar DELETE "/ativos/$a1") 409
Esperar 'Excluir tipo de ativo em uso -> 409' (Chamar DELETE '/tipos-ativo/1') 409

Write-Output '--- Auditoria, erros gerais e usuários'
$r = Chamar GET '/auditoria?limite=500'
Esperar 'Auditoria registrou as alterações' $r 200 { param($j) @($j).Count -gt 20 }
Write-Output ("        registros: " + @($r.Json).Count + " | ações: " + ((@($r.Json) | Group-Object acao | ForEach-Object { "$($_.Name)=$($_.Count)" }) -join ', '))
Esperar 'Auditoria separa quem fez o quê (TECNICO)' (Chamar GET "/auditoria?usuarioId=$tecId") 200 { param($j) @($j).Count -ge 5 }
Esperar 'Rota inexistente -> 404' (Chamar GET '/nao-existe') 404
Esperar 'JSON inválido -> 400' (Chamar POST '/salas' '{"nome": ') 400
Esperar 'Id não numérico -> 400' (Chamar GET '/salas/abc') 400
Esperar 'Ativo inexistente -> 404' (Chamar GET '/ativos/9999') 404
Esperar 'Administrador não pode inativar a si mesmo -> 400' (Chamar PATCH '/usuarios/1/inativar') 400
Esperar 'Inativar TECNICO -> 200' (Chamar PATCH "/usuarios/$tecId/inativar") 200 { param($j) $j.ativo -eq $false }
Esperar 'Usuário inativo como responsável -> 400' (Chamar POST '/salas' @{nome='Sala 2'} $tecId) 400
Esperar 'Rebaixar o único ADMIN -> 409' (Chamar PUT '/usuarios/1' @{nome='Administrador'; email='admin@infratrace.local'; perfil='TECNICO'}) 409

Write-Output ''
Write-Output "RESULTADO: $script:ok OK, $script:falhas FALHA(S)"
