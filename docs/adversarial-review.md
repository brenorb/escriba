# Avaliação adversarial

**APROVADO — v0.1.3, sem bloqueantes pendentes no escopo testado.** APK final SHA256 `8b59543e6848a7ffc9f53cf5e54905b3c615260d20591218a73fae1fef22170f`, Android `11cde60`. Passaram 22 testes no API33, quatro modelos reais pelo release HTTPS com runtime público e ASR nativo real no API35. Não há telefone físico nesta avaliação.

A revisão original abaixo corresponde somente à v0.1.0 e permanece como histórico. A reavaliação detalhada da v0.1.3 está registrada ao final e substitui seu veredito para a versão atual.

**APROVADO — sem bloqueantes pendentes no escopo testado.** Revisão independente por subagente adversarial, concluída em 2026-10-10. A aprovação exigiu transcrição real de fala em português pelo caminho de compartilhamento Android; respostas de servidor fixture não foram aceitas como prova de ASR.

## Artefatos avaliados

- Android: código `0b1ffe5`, compatível com Android 13/API 33; APK de distribuição assinado de **49.853 bytes**.
- SHA-256 do APK: `e2e17f32d6c4695a6d447315a171c2e661ccdcef365ad2de82c8b963a4f57230`.
- Adaptador fast-transcript: código `4b3f18a`, incluindo proteção para sobras de áudio menores que um segundo.
- Emulador ARM64 Android **13/API 33**, com serviço Google TTS instalado; backend real `fscript` em CPU no computador.

## Evidência de ASR real

`ACTION_SEND`, MIME `application/octet-stream`, URI `content://`, decodificação MediaExtractor/MediaCodec, conversão PCM, multipart HTTP, inferência real e texto visível no app foram exercitados juntos. O ingresso automatizado usa o provider **exclusivo de debug**; o APK de distribuição foi verificado separadamente e não contém esse provider.

| Caso real no Android 13 | Resultado observado |
| --- | --- |
| Opus com 15 segundos de fala em português, Parakeet v3 int8 | Concluiu com fala reconhecida; texto visível em até 4,81 s |
| Opus com 45 segundos de fala, Parakeet v3 int8 | Texto visível durante processamento em até 5,08 s; três trechos concluídos em até 7,26 s |
| Novo Opus de 7 s durante inferência real de um Opus de 45 s | Concluiu automaticamente em até 4,51 s; somente o novo texto e intervalo `00:00–00:07`, sem resultados antigos |
| Opus de 15 s, modelo **Parakeet Redux escolhido pelo app** | Texto visível em até 5,65 s; conclusão em até 7,83 s após correção da sobra curta |
| MP3, M4A/AAC, WAV, FLAC, Ogg/Vorbis estéreo, WebM/Opus estéreo, MP4/AAC | Todos produziram transcrição real com fala reconhecida |
| MP4 com trilhas de vídeo e áudio | Extração da trilha de áudio e ASR real concluídos; texto visível em até 3,24 s para 7 s de áudio |

Esses tempos são **limites superiores observados**, medidos por amostragem da UI com UIAutomator, não medidas precisas de inferência. Incluem abertura e consulta da interface, e não representam desempenho garantido em um telefone. As conversões de formato usam a mesma fala, sem redistribuir o áudio de origem no repositório. Houve erros de palavras no reconhecimento; esta revisão verifica funcionamento e integração, não estabelece uma nova taxa de acerto/WER.

## Testes de contratos e interface

A suíte final no API 33 passou com **14 testes em 17,22 s**, cobrindo:

- Início automático do Opus, ordem dos trechos e posição de leitura com texto longo.
- MP3/M4A, compartilhamento por ClipData, seletor de documentos e arquivo corrompido.
- Parar ou substituir um áudio durante uma resposta lenta; nenhum texto antigo reaparece.
- HTTP 401 sem texto falso; retry automático de HTTP 503 e cancelamento durante retry.
- Rejeição de conclusão nativa antes de a fonte ser integralmente enviada.
- Clipboard real contendo apenas texto concluído, sem intervalos temporais.
- Menu de Ajustes visível, seleção de servidor, gravação de URL HTTPS e leitura das preferências salvas.
- Contraste mínimo 4,5:1 para texto dos controles nativos no tema escuro.

Os testes de transporte usam respostas fixture somente para verificar contratos, falhas e concorrência. O reconhecimento real acima foi feito separadamente.

Os **oito testes do adaptador** passaram, incluindo escolha do modelo por requisição, erro real do subprocesso sem virar transcrição, remoção do arquivo temporário e padding que preserva exatamente amostras não zero e acrescenta somente silêncio.

No APK **assinado de distribuição**, instalado no API 33, a revisão abriu Ajustes usando a interface, selecionou servidor e confirmou que HTTP em loopback é recusado com `Use HTTPS`, enquanto HTTPS é aceito e persiste após reabrir o diálogo. `usesCleartextTraffic=false`, minSdk 33, ausência de FixtureProvider e exclusão de preferências em backup/transferência foram verificados. O buffer de crashes ficou vazio. A interface também foi exercitada em tema escuro, fonte 1,3× e paisagem, preservando o texto e mantendo controles acessíveis.

## Defeitos encontrados e resolvidos

| Defeito reproduzido | Correção e revalidação |
| --- | --- |
| Seletor perdia URI com `setData().setType()` | `setDataAndType`; regressão instrumentada passou |
| Política aceitava HTTP que o release não poderia usar | Distribuição exige HTTPS; rejeição confirmada pela UI do APK assinado |
| Adaptador ignorava modelo enviado pelo app | Seleção por allowlist; Redux real foi escolhido pelo app |
| Provedor nativo podia concluir antes do fim da fonte | Guard explícito; teste vermelho antes da correção e verde depois |
| Novo share falhava com 503 enquanto a inferência cancelada ainda ocupava o servidor | Retry limitado e cancelável; troca durante inferência real passou sem intervenção |
| Menu ancorado na raiz abria fora da tela | Âncora no botão; acesso e configuração reais passaram |
| Spinner nativo tinha contraste insuficiente no escuro | Tema Android escuro aplicado; contraste e inspeção visual passaram |
| Redux falhava com 502 em uma sobra curtíssima após 15 s | Padding de silêncio sem descartar PCM; teste de preservação e Redux real passaram |

## Limites explícitos

- Não há telefone físico nesta avaliação. Não foram usados clientes reais de WhatsApp, Telegram ou Signal; foram testados arquivos/containers e intents Android equivalentes. Variações de providers e codecs de fabricantes continuam dependentes do aparelho.
- O serviço Android 13 instalado no emulador respondeu que o pacote **pt-BR não está instalado**. Esse erro foi mostrado de forma explícita, sem fallback silencioso para envio remoto. **Sucesso de ASR nativo offline em telefone não foi demonstrado.** A API nativa e suas salvaguardas são verificadas, mas o provedor de cada telefone ainda precisa ser validado.
- ASR real foi demonstrado com Parakeet v3 int8 e Redux. Ultra e Phonon-2 têm encaminhamento/configuração permitidos, mas não foram executados nesta revisão.
- O caminho real automatizado de ASR usa debug com loopback; o release foi validado quanto a instalação, interface e política HTTPS, sem executar um endpoint HTTPS externo de produção.
- Trechos fixos de 15 s podem cortar palavras e perder contexto. Cancelar no telefone fecha a conexão e bloqueia resultados antigos, mas não mata imediatamente um subprocesso ASR que já esteja rodando no computador. Retry de servidor ocupado é limitado a aproximadamente 30 s; ocupação mais longa gera erro recuperável.
- Leituras de memória do emulador incluem framework e páginas compartilhadas; não se afirma pico de memória, consumo energético ou uso de CPU de um aparelho físico. O APK não inclui modelos, ffmpeg, WebView, Compose ou runtimes de terceiros.

## Reproduzir a prova real

Com um APK debug instalado, um backend fast-transcript real iniciado e seu próprio áudio com fala conhecida:

```sh
adb -s emulator-5556 reverse tcp:8765 tcp:8765
python3 scripts/e2e-real-asr.py /caminho/voz.opus \
  --serial emulator-5556 --expected 'frase conhecida' \
  --model parakeet-v3-int8 --output /tmp/escriba-real-e2e
# Para áudio longo, acrescente --require-progressive.
```

O script exige API 33, compartilha o arquivo automaticamente e grava a UI, screenshot e resultado JSON. Não contém respostas ASR nem áudio de benchmark embutidos. Para uma URL remota de produção, use HTTPS com certificado confiável e configuração explícita do usuário.

## Reavaliação v0.1.3 — 2026-10-10

**APROVADO — todos os gates concluídos, sem bloqueantes pendentes no escopo testado.** Esta rodada reavaliou a v0.1.2 e as correções para v0.1.3; a aprovação antiga não foi reutilizada. Código Android final `11cde60`; APK assinado v0.1.3, versionCode 4, **58.045 bytes**, SHA256 `8b59543e6848a7ffc9f53cf5e54905b3c615260d20591218a73fae1fef22170f`.

### Falhas reproduzidas nesta rodada

- O teste de Ajustes ainda esperava um seletor e quatro campos, embora a tela tivesse dois seletores e três campos. O teste foi atualizado para a interface real.
- Trocar o modelo no menu não transcrevia novamente o áudio aberto e não substituía inferência em andamento: os testes vermelhos observaram apenas uma requisição em vez de duas.
- O botão de instalar voz desaparecia após redesenhar a tela. A falha foi confirmada tanto por callback de teste quanto pelo provedor Android real no API35.
- Durante instalação, Parar ficava desabilitado. O contrato exige ação de cancelamento habilitada, além de ignorar callbacks atrasados.
- Paisagem com fonte 150% deixava somente **38 px** para o texto numa janela de **1080 px** de altura. O teste exige orientação efetivamente aplicada e mede o ScrollView que contém a transcrição. A versão corrigida distribui controles e leitura em duas colunas.
- Após instalar um pacote de voz real, a retomada recriava o reconhecedor e falhava com `ERROR_SERVER_DISCONNECTED` (11); era preciso tocar Tentar novamente. Reutilizar a sessão na retomada passou no provedor real.
- Dois shares nativos consecutivos também reproduziram erro 11; a reconexão automática é limitada e invalidada por Parar/novo share.
- Um teste inicial de cancelamento usava `performClick()` sem exigir que o botão estivesse habilitado. Isso dava falsa confiança, pois esse método pode invocar o listener mesmo com botão desabilitado. O teste reforçado reproduziu que `modelUnavailable → disconnected` mantinha Parar desabilitado. A correção habilita Parar, encerra a apresentação de instalação e invalida o retry quando cancelado; o teste reforçado passou após reproduzir o vermelho.

### ASR real e procedência do runtime

Os quatro modelos foram executados pelo fluxo Android13 de compartilhamento Opus de fala portuguesa, extração/decodificação, multipart e inferência real. A preferência persistida foi comparada ao modelo solicitado para impedir que a migração mascarasse a seleção.

| Modelo | Primeiro texto visível (limite superior) | Conclusão (limite superior) |
| --- | --- | --- |
| Parakeet Redux | 5,66 s | 8,51 s |
| Parakeet v3 int8 | 4,31 s | 4,33 s |
| Phonon-2 | 27,22 s | 34,34 s |
| Parakeet Ultra | 5,35 s | 7,55 s |

Essas quatro execuções usaram o binário local `fast-transcript/target/release/fscript`, SHA256 `a6534eea13b95625703778d1f3e550fbcc836f8de9a94c137be29ae4d74dc2e2`, cuja árvore de origem possui alterações locais. Por isso, uma prova adicional de distribuição usa o instalador público fixado no commit `cadda438a87ba8eeb52f1794cbb66017a9015478`, binário `/tmp/escriba-public-installer/bin/fscript`, SHA256 `36e5cd47f63549dfa354d4ac184f92617652753359b8a530df68e6302aa786b7`. O `fscript` antigo em `/opt/homebrew/bin` recusou `--model`; ele não satisfaz o pré-requisito da integração atual.

O APK assinado de SHA256 `bea1145e55c791fefa63bf93bdddf104420b53222f897c92b0e3db881bd70c80` concluiu Redux pelo runtime público em **HTTPS**, com primeiro texto em até **7,29 s** e conclusão em até **10,21 s**. Foi usado áudio real em `content://media/external/audio/media/1000000027`, grant Android real do usuário shell, e CA temporária instalada somente no truststore de sistema do emulador API33. A validação TLS do aplicativo permaneceu intacta; não foi usado FixtureProvider nem HTTP cleartext nesse caminho. A configuração foi preparada por adb root para isolar a integração; os testes instrumentados exercitam separadamente Ajustes pela interface. A prova foi repetida no APK final para todos os quatro modelos, conforme tabela abaixo. Nenhum sucesso dependeu de desabilitar TLS ou inserir resposta artificial.

### Provedor Android real

No API35, o revisor de UX confirmou instalação de pacote **es-ES de 49,31 MB** pelo diálogo Android, progresso **41%**, retomada automática e estado **Concluído · 1 trecho**, sem Tentar novamente nem erro11. O áudio era português e o idioma espanhol foi escolhido deliberadamente para testar um pacote ainda não instalado; isso não mede qualidade em espanhol. O mesmo provedor reconheceu fala portuguesa quando pt-BR estava instalado. Estas são provas do provedor real, distintas dos testes que injetam callbacks para verificar a interface.

Não houve telefone físico ou clientes WhatsApp/Telegram/Signal reais. A execução dos quatro modelos usa caches disponíveis e não comprova tempo de primeiro download frio. Tempos do emulador e computador não são comparáveis a desempenho garantido no telefone. Os erros de palavras observados não foram tratados como transcrição perfeita nem convertidos em estimativa de WER.

### Gates finais de distribuição

A suíte de **22 testes instrumentados** passou no API33 em **31,984 s** após a última correção. A migração usa ActivityMonitor para exigir que a nova Activity realmente execute após recriação, e finaliza essa instância para evitar interferência. O teste de reconexão exige Parar habilitado e confirma que o callback atrasado não retoma depois de cancelado. Callbacks simulados validam somente contratos e UI; as provas ASR abaixo executam o modelo real.

Os **11 testes do servidor** passaram em **0,616 s**, incluindo timeout e encerramento dos descendentes do instalador, limpeza do áudio temporário e rejeição clara de CLI incompatível. O teste de processos precisou permissão para consultar `ps` no host; seu erro inicial de sandbox não foi ignorado.

Todos os modelos abaixo passaram no **APK final assinado**, com áudio Opus português real compartilhado por **URI MediaStore e grant Android**, **HTTPS com verificação de certificado intacta**, e o **fast-transcript público instalado no commit cadda438**. Persistência do modelo escolhido foi verificada em cada execução.

| Modelo no release final e runtime público | Primeiro texto visível (limite superior) | Conclusão (limite superior) |
| --- | --- | --- |
| Parakeet Redux | 7,15 s | 9,59 s |
| Parakeet v3 int8 | 4,25 s | 4,27 s |
| Phonon-2 | 22,08 s | 28,61 s |
| Parakeet Ultra | 5,98 s | 8,91 s |

Os arquivos de evidência local ficam em `/tmp/escriba-013-release-public-{modelo}/`, com `result.json`, `ui.xml`, `screen.png` e log. O APK final foi instalado com sucesso, a assinatura v3 foi verificada, minSdk33/targetSdk35 e `usesCleartextTraffic=false` foram confirmados; nenhum provider de fixture aparece no manifest de distribuição e o buffer de crashes permaneceu vazio. A CA existe somente no emulador de QA. Isso demonstra integração HTTPS release real em ambiente controlado, sem afirmar validação de um servidor externo ou telefone físico.

### Gate nativo final e veredito

O revisor de UX instalou o **mesmo APK final de hash 8b59543…** no API35 e enviou dois intents consecutivos `ACTION_VIEW`, `audio/ogg`, URI `content://media/external/audio/media/1000000018`, grant `0x1`. O provedor nativo reconheceu fala portuguesa e concluiu **1 trecho** automaticamente, sem erro11 ou toque em Tentar novamente. A evidência está em `/tmp/escriba-ux-final-hash-native.xml` e `.png`; o subagente adversarial inspecionou o XML do resultado e o screenshot final em paisagem escura com fonte200%. A área de leitura permanece ampla e os controles estão em uma coluna rolável independente.

A instalação real de pacote, a retomada automática e os shares consecutivos foram provas do serviço Android, não respostas simuladas. Os 22 contratos API33, 11 testes servidor, verificação de assinatura/manifest/crashes, os 20 testes do núcleo registrados no build final e as quatro inferências HTTPS públicas sustentam a aprovação da **v0.1.3**. A aprovação não depende da CI remota ainda em andamento; a verificação local correspondente foi executada. Permanecem somente os limites declarados: ausência de telefone físico/clientes sociais reais, dependência do provedor nativo do aparelho, caches do host já disponíveis e possíveis erros de reconhecimento/contexto entre trechos.

### Correção do harness após a CI

A CI `38023370328` falhou somente na medida de paisagem (`reading=297`, `viewport=640`). O teste alterava `font_scale`, o Android recriava a Activity, mas a medição continuava usando a referência destruída. Isso foi reproduzido no emulador API33 com viewport **480×640**, densidade160: `originalDestroyed=true`, `activeChanged=true`, janela antiga **480×640**, janela ativa em paisagem **606×480**, área real de leitura **432 px**. O teste corrigido usa ActivityMonitor, exige fonte150% realmente aplicada, exige largura maior que altura após layout e mede a Activity atual. O critério de leitura ocupar pelo menos metade da altura foi preservado. O caso isolado passou em **1,416 s**. Nenhum código do app ou APK foi alterado para corrigir essa falha de teste. O resultado da CI com o harness corrigido ainda precisa ser confirmado antes da publicação.

A suíte inteira de **22 testes passou em 29,156 s** nesse viewport compacto, com ActivityMonitor e sem qualquer alteração no APK de produção. O emulador foi restaurado ao tamanho, densidade e fonte padrão depois da reprodução.
