# Avaliação adversarial

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
