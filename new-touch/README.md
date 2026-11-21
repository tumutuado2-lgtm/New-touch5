# New Touch

Aplicativo Android com a função **Arraste Contínuo**: um dedo virtual fica pressionado na tela e se move
na horizontal, vertical ou diagonal, **sem nunca ser levantado** ao chegar no fim de cada arrasto.

## Como funciona (e a limitação do Android)

- Usa o **serviço de acessibilidade** (`AccessibilityService.dispatchGesture`) com
  `StrokeDescription(willContinue = true)` e `continueStroke()`. Cada trecho continua o mesmo toque.
- O Android **não permite** estender o toque real do seu dedo. A melhor alternativa técnica é este
  "dedo virtual". Entre um trecho e outro pode haver alguns milissegundos de intervalo, e alguns apps/jogos
  podem ignorar gestos injetados.
- Pausar encerra o toque; retomar inicia um novo toque no ponto configurado.
- O botão flutuante é desenhado pelo próprio serviço (`TYPE_ACCESSIBILITY_OVERLAY`), então não precisa da permissão
  "Exibir sobre outros apps". O app não tem permissão de internet e não lê o conteúdo da tela.
- Requisitos: Android 10 (API 29) ou superior.

## Gerar o APK só com o celular

1. No navegador do celular, entre em github.com e crie um repositório novo (ex.: `new-touch`).
2. No repositório: **Add file > Upload files** e envie o arquivo `new-touch.zip`. Toque em **Commit changes**.
3. **Add file > Create new file**. No nome digite `.github/workflows/build.yml`
   (as barras criam as pastas). Cole o conteúdo do arquivo `build.yml` e toque em **Commit changes**.
4. Abra a aba **Actions**. O build "Build New Touch APK" começa sozinho (leva ~5 a 10 minutos).
   Ele extrai o ZIP, compila e publica o APK.
5. Quando ficar verde, abra a aba **Releases** (ou a página principal do repositório > Releases) e baixe
   `New-Touch.apk`.

Se o passo 4 falhar com erro de permissão: **Settings > Actions > General > Workflow permissions >
Read and write permissions**, salve, e rode de novo em **Actions > Build New Touch APK > Run workflow**.

## Instalar e usar

1. Abra o `New-Touch.apk` baixado e permita "instalar apps desconhecidos" quando o Android pedir.
2. Abra o New Touch e toque em **Conceder permissão** (ou **Permissões**).
3. Em Acessibilidade, ative **New Touch**.
   - Se a chave estiver cinza (Android 13+): informações do app > menu ⋮ > **Permitir configurações restritas**.
4. Volte ao app e toque em **Ativar**. Aparece o botão flutuante.
5. Toque no botão flutuante: ▶ inicia/pausa, ■ encerra, ⚙ abre o app. Arraste a bolinha para mover.
6. Ajuste velocidade, alcance, direção e o ponto de toque na tela inicial. Use **Testar** para ver o rastro e
   o contador "Levantadas" (deve ficar em 0 durante o movimento).

## Editar pelo GitHub

Edite qualquer arquivo pelo ícone de lápis e faça commit. Cada commit gera um novo APK em Releases.

```
.github/workflows/build.yml      compilação automática + release
app/build.gradle.kts             configuração do app
app/src/main/AndroidManifest.xml
app/src/main/java/com/newtouch/app/
  TouchService.kt                serviço de acessibilidade, gesto contínuo, botão flutuante
  MainActivity.kt                tela inicial
  PermissionsActivity.kt         tela de permissões
  TestActivity.kt, TrailView.kt  tela de teste
  Prefs.kt, Util.kt              configurações e animações
app/src/main/res/                layouts, cores, ícones e animações
```
