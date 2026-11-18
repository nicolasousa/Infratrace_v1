# =====================================================================
# InfraTrace - teste automático de ponta a ponta da API
#
# Faz ~80 chamadas HTTP reais (login, perfis, racks, slots, movimentação,
# manutenção, aluguéis, alertas, descarte, auditoria) e confere o status
# e o conteúdo de cada resposta. Mostra OK/FALHA por linha e um resumo.
#
# Pré-requisitos:
#   1. Banco RECÉM-CRIADO (rode database/infratrace_mysql.sql antes),
#      porque o teste cria o primeiro admin e conta registros.
#   2. API rodando em http://localhost:8080 (InfratraceApplication).
#
# Como rodar (PowerShell, na raiz do projeto):
#   powershell -ExecutionPolicy Bypass -File docs\teste_api.ps1
# =====================================================================
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080'
$script:ok = 0; $script:falhas = 0

function Chamar($metodo, $rota, $corpo = $null, $token = $null) {
    $h = @{}
    if ($token) { $h['Authorization'] = "Bearer $token" }
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

$hoje = Get-Date
$d = { param($dias) $hoje.AddDays($dias).ToString('yyyy-MM-dd') }

Write-Output '--- Autenticação e perfis'
Esperar 'Sem token -> 401' (Chamar GET '/tipos-ativo') 401
Esperar 'Login sem usuários -> 401' (Chamar POST '/auth/login' @{email='x@x.com'; senha='12345678'}) 401
Esperar 'Primeiro admin -> 201' (Chamar POST '/auth/primeiro-admin' @{nome='Admin'; email='Admin@InfraTrace.local'; senha='Admin@1234'}) 201 { param($j) $j.perfil -eq 'ADMIN' -and $j.email -eq 'admin@infratrace.local' }
Esperar 'Segundo primeiro-admin -> 409' (Chamar POST '/auth/primeiro-admin' @{nome='Outro'; email='o@x.com'; senha='Admin@1234'}) 409
Esperar 'Senha errada -> 401' (Chamar POST '/auth/login' @{email='admin@infratrace.local'; senha='errada123'}) 401
$r = Chamar POST '/auth/login' @{email='admin@infratrace.local'; senha='Admin@1234'}; Esperar 'Login admin -> 200' $r 200 { param($j) $j.token.Length -gt 20 }
$adm = $r.Json.token
Esperar 'GET /auth/me' (Chamar GET '/auth/me' $null $adm) 200 { param($j) $j.nome -eq 'Admin' -and -not $j.PSObject.Properties['senhaHash'] }
Esperar 'GET /tipos-ativo (7 tipos)' (Chamar GET '/tipos-ativo' $null $adm) 200 { param($j) @($j).Count -eq 7 }
Esperar 'Senha curta -> 400' (Chamar POST '/usuarios' @{nome='T'; email='t@x.com'; senha='123'; perfil='TECNICO'} $adm) 400
Esperar 'Criar TECNICO -> 201' (Chamar POST '/usuarios' @{nome='Técnico'; email='tec@infratrace.local'; senha='Tecnico@123'; perfil='TECNICO'} $adm) 201
$r = Chamar POST '/usuarios' @{nome='Auditora'; email='aud@infratrace.local'; senha='Auditor@123'; perfil='AUDITOR'} $adm; Esperar 'Criar AUDITOR -> 201' $r 201
$tecId = (Chamar GET '/usuarios' $null $adm).Json | Where-Object email -eq 'tec@infratrace.local' | Select-Object -ExpandProperty id
Esperar 'E-mail repetido -> 409' (Chamar POST '/usuarios' @{nome='X'; email='TEC@infratrace.local'; senha='Tecnico@123'; perfil='TECNICO'} $adm) 409
$tec = (Chamar POST '/auth/login' @{email='tec@infratrace.local'; senha='Tecnico@123'}).Json.token
$aud = (Chamar POST '/auth/login' @{email='aud@infratrace.local'; senha='Auditor@123'}).Json.token

Write-Output '--- Infraestrutura física'
$r = Chamar POST '/salas' @{nome='Sala 1'; descricao='Térreo'} $adm; Esperar 'Criar sala (ADMIN) -> 201' $r 201; $sala = $r.Json.id
Esperar 'Criar sala (TECNICO) -> 403' (Chamar POST '/salas' @{nome='Sala X'} $tec) 403
Esperar 'GET /usuarios (AUDITOR) -> 403' (Chamar GET '/usuarios' $null $aud) 403
Esperar 'GET /salas (AUDITOR) -> 200' (Chamar GET '/salas' $null $aud) 200
Esperar 'Sala com nome repetido -> 409' (Chamar POST '/salas' @{nome='Sala 1'} $adm) 409
$r = Chamar POST '/fileiras' @{nome='A'; salaId=$sala} $adm; Esperar 'Criar fileira -> 201' $r 201; $fil = $r.Json.id
Esperar 'Fileira em sala inexistente -> 400' (Chamar POST '/fileiras' @{nome='B'; salaId=999} $adm) 400
$r = Chamar POST '/racks' @{nome='R01'; totalSlots=42; fileiraId=$fil} $adm; Esperar 'Criar rack 42U -> 201' $r 201; $r1 = $r.Json.id
$r2 = (Chamar POST '/racks' @{nome='R02'; totalSlots=24; fileiraId=$fil} $adm).Json.id
Esperar 'Rack com 70 slots -> 400' (Chamar POST '/racks' @{nome='R99'; totalSlots=70; fileiraId=$fil} $adm) 400

Write-Output '--- Ativos e ocupação dos racks'
$r = Chamar POST '/notas-fiscais' @{num=1001; valor=25000.50; razaoSocial='Fornecedor TI Ltda'; cnpj='11.222.333/0001-81'} $tec; Esperar 'Nota fiscal (TECNICO) -> 201' $r 201 { param($j) $j.cnpj -eq '11222333000181' }; $nf = $r.Json.id
$r = Chamar POST '/ativos' @{nome='SRV-01'; fabricante='Dell'; modelo='R750'; numSerie='SN001'; tipoAtivoId=1; notaFiscalId=$nf; rackId=$r1; slotInicio=1; slotFim=2; valorCompra=25000.50; dataAquisicao=(& $d -100); garantia=(& $d 20)} $tec
Esperar 'Ativo instalado nos slots 1-2 -> 201' $r 201 { param($j) $j.rackId -eq $r1 -and $j.status -eq 'ATIVO' }; $a1 = $r.Json.id
Esperar 'Ativo nos slots 2-3 (sobreposto) -> 409' (Chamar POST '/ativos' @{nome='SRV-02'; fabricante='HP'; modelo='DL380'; numSerie='SN002'; tipoAtivoId=1; rackId=$r1; slotInicio=2; slotFim=3} $tec) 409
Esperar 'Ativo nos slots 41-43 (passa de 42U) -> 400' (Chamar POST '/ativos' @{nome='SW-01'; fabricante='Cisco'; modelo='9300'; numSerie='SN003'; tipoAtivoId=2; rackId=$r1; slotInicio=41; slotFim=43} $tec) 400
Esperar 'Rack sem slots -> 400' (Chamar POST '/ativos' @{nome='SW-01'; fabricante='Cisco'; modelo='9300'; numSerie='SN003'; tipoAtivoId=2; rackId=$r1} $tec) 400
$r = Chamar POST '/ativos' @{nome='SW-01'; fabricante='Cisco'; modelo='9300'; numSerie='SN003'; tipoAtivoId=2} $tec; Esperar 'Ativo no estoque -> 201' $r 201 { param($j) $null -eq $j.rackId }; $a2 = $r.Json.id
Esperar 'Número de série repetido -> 409' (Chamar POST '/ativos' @{nome='X'; fabricante='X'; modelo='X'; numSerie='SN003'; tipoAtivoId=2} $tec) 409
Esperar 'Status DESCARTADO via cadastro -> 400' (Chamar POST '/ativos' @{nome='X'; fabricante='X'; modelo='X'; numSerie='SN009'; tipoAtivoId=2; status='DESCARTADO'} $tec) 400
Esperar 'AUDITOR criando ativo -> 403' (Chamar POST '/ativos' @{nome='X'} $aud) 403

Write-Output '--- Movimentação (transação manual)'
$r = Chamar POST '/movimentacoes' @{ativoId=$a2; rackDestinoId=$r1; slotInicioDestino=10; slotFimDestino=10; motivo='Instalação'} $tec
Esperar 'Estoque -> R01 slot 10 -> 201' $r 201 { param($j) $null -eq $j.rackOrigemId -and $j.rackDestinoId -eq $r1 }
Esperar 'Ativo atualizado pela movimentação' (Chamar GET "/ativos/$a2" $null $tec) 200 { param($j) $j.rackId -eq $r1 -and $j.slotInicio -eq 10 }
Esperar 'Mover para slot ocupado (1) -> 409' (Chamar POST '/movimentacoes' @{ativoId=$a2; rackDestinoId=$r1; slotInicioDestino=1; slotFimDestino=1} $tec) 409
Esperar 'Ativo continua no slot 10 (rollback)' (Chamar GET "/ativos/$a2" $null $tec) 200 { param($j) $j.slotInicio -eq 10 }
Esperar 'Mesma posição -> 409' (Chamar POST '/movimentacoes' @{ativoId=$a2; rackDestinoId=$r1; slotInicioDestino=10; slotFimDestino=10} $tec) 409
$r = Chamar POST '/movimentacoes' @{ativoId=$a2; rackDestinoId=$r1; slotInicioDestino=11; slotFimDestino=12; motivo='Deslizar no mesmo rack'} $tec
Esperar 'Deslizar no mesmo rack (10 -> 11-12) -> 201' $r 201 { param($j) $j.slotInicioOrigem -eq 10 -and $j.slotFimDestino -eq 12 }
Esperar 'Histórico do ativo (2 movimentações)' (Chamar GET "/ativos/$a2/movimentacoes" $null $aud) 200 { param($j) @($j).Count -eq 2 }
Esperar 'Ativo com instalação no cadastro tem histórico' (Chamar GET "/ativos/$a1/movimentacoes" $null $aud) 200 { param($j) @($j).Count -eq 1 }
$r = Chamar GET "/racks/$r1/ocupacao" $null $aud
Esperar 'Ocupação R01: 4 de 42 ocupados' $r 200 { param($j) $j.slotsOcupados -eq 4 -and $j.slotsLivres -eq 38 -and @($j.livres).Count -eq 2 -and $j.livres[0].slotInicio -eq 3 }
Write-Output ("        livres: " + (($r.Json.livres | ForEach-Object { "$($_.slotInicio)-$($_.slotFim)" }) -join ', ') + " | ocupado: $($r.Json.percentualOcupacao)%")
Esperar 'Reduzir R01 para 5U (ativo até o 12) -> 409' (Chamar PUT "/racks/$r1" @{nome='R01'; totalSlots=5; fileiraId=$fil} $adm) 409
Esperar 'Reduzir R01 para 20U -> 200' (Chamar PUT "/racks/$r1" @{nome='R01'; totalSlots=20; fileiraId=$fil} $adm) 200
Esperar 'Excluir rack com ativos -> 409' (Chamar DELETE "/racks/$r1" $null $adm) 409

Write-Output '--- Manutenção'
$r = Chamar POST '/manutencoes' @{ativoId=$a1; tipo='corretiva'; descricao='Troca de fonte'; responsavel='Fornecedor'; dataInicio=(& $d 0)} $tec; Esperar 'Abrir manutenção -> 201' $r 201; $man = $r.Json.id
Esperar 'Ativo ficou EM_MANUTENCAO' (Chamar GET "/ativos/$a1" $null $tec) 200 { param($j) $j.status -eq 'EM_MANUTENCAO' }
Esperar 'Segunda manutenção aberta -> 409' (Chamar POST '/manutencoes' @{ativoId=$a1; tipo='PREVENTIVA'; descricao='X'; responsavel='Y'; dataInicio=(& $d 0)} $tec) 409
Esperar 'Finalizar antes do início -> 400' (Chamar PATCH "/manutencoes/$man/finalizar" @{dataFim=(& $d -5)} $tec) 400
Esperar 'Finalizar manutenção -> 200' (Chamar PATCH "/manutencoes/$man/finalizar" $null $tec) 200 { param($j) $j.dataFim -ne $null }
Esperar 'Ativo voltou para ATIVO' (Chamar GET "/ativos/$a1" $null $tec) 200 { param($j) $j.status -eq 'ATIVO' }

Write-Output '--- Clientes e aluguéis'
Esperar 'CNPJ inválido -> 400' (Chamar POST '/clientes' @{nome='ACME'; cnpj='11.222.333/0001-00'; representante='Ana'; tipoClienteId=1} $adm) 400
$r = Chamar POST '/clientes' @{nome='ACME S.A.'; cnpj='11.222.333/0001-81'; representante='Ana'; tipoClienteId=1} $adm; Esperar 'Criar cliente -> 201' $r 201 { param($j) $j.ativo -eq $true }; $cli = $r.Json.id
Esperar 'CNPJ repetido -> 409' (Chamar POST '/clientes' @{nome='Outro'; cnpj='11222333000181'; representante='B'; tipoClienteId=2} $adm) 409
Esperar 'TECNICO criando cliente -> 403' (Chamar POST '/clientes' @{nome='X'} $tec) 403
$r = Chamar POST '/alugueis' @{dataInicio=(& $d 30); dataFim=(& $d 395); valor=3500; clienteId=$cli; rackId=$r1} $adm; Esperar 'Aluguel R01 -> 201' $r 201
Esperar 'Aluguel sobreposto no R01 -> 409' (Chamar POST '/alugueis' @{dataInicio=(& $d 200); dataFim=(& $d 500); valor=3500; clienteId=$cli; rackId=$r1} $adm) 409
Esperar 'Aluguel logo depois no R01 -> 201' (Chamar POST '/alugueis' @{dataInicio=(& $d 396); dataFim=(& $d 500); valor=3600; clienteId=$cli; rackId=$r1} $adm) 201
Esperar 'Aluguel R02 vencendo em 10 dias -> 201' (Chamar POST '/alugueis' @{dataInicio=(& $d -20); dataFim=(& $d 10); valor=2000; clienteId=$cli; rackId=$r2} $adm) 201
Esperar 'Fim antes do início -> 400' (Chamar POST '/alugueis' @{dataInicio=(& $d 10); dataFim=(& $d 5); valor=1; clienteId=$cli; rackId=$r2} $adm) 400

Write-Output '--- Configurações e alertas'
Esperar 'Alerta de garantias (SRV-01 vence em 20 dias)' (Chamar GET '/alertas/garantias' $null $aud) 200 { param($j) $j.quantidade -eq 1 -and $j.diasAntecedencia -eq 30 }
Esperar 'Alerta de aluguéis (R02 vence em 10 dias)' (Chamar GET '/alertas/alugueis' $null $aud) 200 { param($j) $j.quantidade -eq 1 }
Esperar 'Configuração com valor fora das opções -> 400' (Chamar PUT '/configuracoes/alerta_garantia_dias' @{valor='45'} $adm) 400
Esperar 'TECNICO alterando configuração -> 403' (Chamar PUT '/configuracoes/alerta_garantia_dias' @{valor='60'} $tec) 403
Esperar 'ADMIN altera para 60 dias -> 200' (Chamar PUT '/configuracoes/alerta_garantia_dias' @{valor='60'} $adm) 200 { param($j) $j.valor -eq '60' }

Write-Output '--- Descarte'
$r = Chamar POST "/ativos/$a1/descarte" @{motivo='Fim de vida útil'} $tec
Esperar 'Descartar ativo instalado -> 200' $r 200 { param($j) $j.status -eq 'DESCARTADO' -and $null -eq $j.rackId -and $j.motivoDescarte }
Esperar 'Descarte gerou movimentação para o estoque' (Chamar GET "/ativos/$a1/movimentacoes" $null $tec) 200 { param($j) @($j).Count -eq 2 -and $null -eq $j[0].rackDestinoId }
Esperar 'Movimentar ativo descartado -> 409' (Chamar POST '/movimentacoes' @{ativoId=$a1; rackDestinoId=$r1; slotInicioDestino=1; slotFimDestino=1} $tec) 409
Esperar 'Descartar de novo -> 409' (Chamar POST "/ativos/$a1/descarte" @{motivo='x'} $tec) 409
Esperar 'Excluir ativo com histórico -> 409' (Chamar DELETE "/ativos/$a1" $null $adm) 409
Esperar 'Excluir tipo de ativo em uso -> 409' (Chamar DELETE '/tipos-ativo/1' $null $adm) 409

Write-Output '--- Auditoria, erros gerais e sessões'
$r = Chamar GET '/auditoria?limite=500' $null $aud
Esperar 'AUDITOR lê auditoria' $r 200 { param($j) @($j).Count -gt 20 }
Write-Output ("        registros: " + @($r.Json).Count + " | ações: " + ((@($r.Json) | Group-Object acao | ForEach-Object { "$($_.Name)=$($_.Count)" }) -join ', '))
Esperar 'Log do usuário não contém hash de senha' $r 200 { param($j) -not (@($j) | Where-Object { "$($_.valorNovo)$($_.valorAnterior)" -match 'senhaHash|pbkdf2' }) }
Esperar 'TECNICO lê auditoria -> 403' (Chamar GET '/auditoria' $null $tec) 403
Esperar 'Rota inexistente -> 404' (Chamar GET '/nao-existe' $null $adm) 404
Esperar 'JSON inválido -> 400' (Chamar POST '/salas' '{"nome": ' $adm) 400
Esperar 'Id não numérico -> 400' (Chamar GET '/salas/abc' $null $adm) 400
Esperar 'Ativo inexistente -> 404' (Chamar GET '/ativos/9999' $null $adm) 404
Esperar 'ADMIN não pode se inativar -> 400' (Chamar PATCH "/usuarios/1/inativar" $null $adm) 400
Esperar 'Inativar TECNICO -> 200' (Chamar PATCH "/usuarios/$tecId/inativar" $null $adm) 200 { param($j) $j.ativo -eq $false }
Esperar 'Token do TECNICO inativado -> 401' (Chamar GET '/ativos' $null $tec) 401
Esperar 'Rebaixar o único ADMIN -> 409' (Chamar PUT '/usuarios/1' @{nome='Admin'; email='admin@infratrace.local'; perfil='TECNICO'} $adm) 409
Esperar 'Logout -> 204' (Chamar POST '/auth/logout' $null $adm) 204
Esperar 'Token após logout -> 401' (Chamar GET '/auth/me' $null $adm) 401

Write-Output ''
Write-Output "RESULTADO: $script:ok OK, $script:falhas FALHA(S)"
