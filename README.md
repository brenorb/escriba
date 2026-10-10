# Escriba

Compartilhe um áudio → leia a transcrição. Android **13 ou posterior**, app nativo sem bibliotecas de execução, sem modelos embutidos e sem conta.

A tela prioriza texto selecionável, parágrafos e intervalos do áudio. Configurações ficam em **⋮ → Ajustes**. Opus/Ogg do WhatsApp e Telegram, AAC/M4A, MP3, WAV, FLAC, AMR, WebM e áudio de vídeos usam os decodificadores do Android. O codec/container precisa ser suportado pelo aparelho; não depende do nome do mensageiro.

## Instalar e usar

Baixe o APK em [Releases](https://github.com/brenorb/escriba/releases), instale e compartilhe **um áudio** com Escriba. Depois da configuração inicial, cada compartilhamento inicia automaticamente. Também pode abrir um arquivo em **Escolher áudio**. Copiar e Enviar exportam somente trechos concluídos. Parar preserva o texto pronto; um novo compartilhamento substitui o anterior. O menu ⋮ já traz os quatro modelos do benchmark para troca imediata; a escolha não pede nome nem URL de modelo.

**Android local (padrão):** se faltar o idioma, o botão **Instalar voz e continuar** solicita o pacote ao Android e retoma o mesmo áudio após a instalação. O sistema pode pedir sua confirmação de download. Parar abandona a transcrição e sua espera; um download já iniciado pelo Android pode continuar no sistema. Usa `createOnDeviceSpeechRecognizer`, áudio PCM por pipe e sessões segmentadas da API 33. Requer serviço compatível e pacote do idioma instalado. O Android exige permissão de microfone até para arquivos; Escriba não contém código de gravação. Alguns serviços ignoram extras ou não implementam entrada de arquivo: a verificação de suporte reduz esse risco, mas não certifica o provedor. Não há fallback silencioso para rede. Falhas mostram como configurar o servidor. O caminho nativo depende do fabricante e precisa de validação no seu telefone.

**Servidor (opcional):** configure uma vez URL completa HTTPS e chave/token em Ajustes. O modelo vem em uma lista pré-programada; o idioma começa em `pt-BR`. O padrão é `parakeet-redux`, o menor cache nos benchmarks locais (170,7 MiB; contra 325,4 MiB do Phonon-2), e o app migra a preferência antiga `parakeet-v3-int8` para ele. Depois, escolha um modelo no menu ⋮; com servidor configurado, isso ativa o backend e reinicia automaticamente o áudio aberto. O fast-transcript baixa pesos e SDK necessários pela internet na primeira transcrição do modelo escolhido; os links dos modelos são pré-programados. O iniciador abaixo prepara o `fscript` compatível e o `uv` no computador; versões antigas do CLI são recusadas antes de aceitar áudios. O download acontece no computador/servidor configurado, não no celular. O backend aceita endpoint compatível com multipart `POST` (`file`, `model`, `language`) que devolva JSON `{"text":"…"}`, inclusive `/v1/audio/transcriptions`. O áudio é convertido em PCM16 mono 16 kHz; trechos de até 15 segundos são enviados em ordem, sem esperar reproduzir o áudio. Cada resultado aparece imediatamente. Se o servidor responder ocupado (HTTP 503), o app tenta novamente por até 30 segundos; Parar ou outro compartilhamento cancela essa espera. Isso é inferência progressiva por trechos, com possível perda de contexto nas fronteiras, não streaming neural nativo. Intervalos mostrados são posições no áudio, não alinhamentos de palavras.

## Modelos do fast-transcript

O adaptador Python usa somente a biblioteca padrão e executa o [fast-transcript](https://github.com/brenorb/fast-transcript) no computador. O iniciador instala a revisão pública `cadda438` em um cache próprio, sem substituir outro `fscript`. Nenhum peso vai para o celular. `parakeet-v3-int8`, `parakeet-redux`, `parakeet-ultra` e `phonon-2` são permitidos; a seleção enviada pelo app prevalece sobre o modelo padrão do servidor. `--device` é configuração do servidor. Sobras de decodificação menores que um segundo são completadas com silêncio apenas no servidor, preservando as amostras e os intervalos originais. O fscript identifica o idioma automaticamente; o campo idioma do app é usado pelo Android e por outros endpoints que o aceitam.

```sh
# Um comando no computador prepara ferramentas e inicia Redux:
./scripts/start-server.sh --device cpu
# No macOS com Homebrew, uv e Rust são instalados automaticamente se faltarem.
# Linux precisa de Rust/Cargo, uv e Python 3 antes do primeiro uso.
# SDK e pesos de Redux baixam automaticamente na primeira transcrição.
# endpoint interno: http://127.0.0.1:8765/transcribe
```

Para acessar pelo telefone, coloque um proxy HTTPS com certificado confiável na frente do servidor. Ao escutar além do loopback, o adaptador exige `ESCRIBA_TOKEN` no ambiente; informe a mesma chave no app. **Não publique o servidor sem HTTPS e autenticação.** Debug permite HTTP somente em loopback para testes; o APK de distribuição exige HTTPS inclusive na LAN. Tokens ficam nas preferências privadas do app e são excluídos de backup/transferência. A primeira preparação/inferência pode levar até dez minutos; o app informa essa preparação e permite cancelar. O timeout do servidor encerra também os instaladores filhos. Áudio temporário do servidor é removido ao terminar a requisição. Sem histórico persistente ou logs de conteúdo. O app cancela ao ser destruído; processamento não é garantido após sair ou com o sistema encerrando o processo.

```sh
# Servidor na rede local, atrás de proxy TLS:
ESCRIBA_TOKEN='sua-chave' ./scripts/start-server.sh --bind 0.0.0.0 --model parakeet-ultra --device mps
```

## Desenvolvimento e testes

JDK 17, SDK Android 35 e imagem API33. O minSdk é **33**. O APK não inclui Compose, Kotlin runtime, WebView, ffmpeg ou pesos de ASR.

```sh
./scripts/test-core.sh
python3 -m unittest discover -s server/tests
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
./gradlew connectedDebugAndroidTest  # emulador Android 13
```

O histórico registra testes vermelhos antes da implementação, seguidos das correções. Testes instrumentados usam um servidor fixture somente para contratos de decodificação, ordenação, falhas e concorrência. A aprovação adversarial exige separadamente reconhecimento **real**, compartilhando Opus com fala em português no emulador Android 13.

- [Pesquisa e decisões](docs/research.md)
- [Avaliação adversarial e limites da validação](docs/adversarial-review.md)
- [Assinatura e distribuição](docs/releasing.md)

## Licença

MIT. Modelos e ferramentas externas preservam suas próprias licenças; não são redistribuídos pelo APK.
