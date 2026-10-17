# NEXUS MOBILADOR PRO — Android 12+ (Kotlin / Jetpack Compose / Shizuku)

## Como gerar o arquivo `.apk` instalável (2 Métodos)

### Método 1 — Sem precisar de PC forte / Sem instalar Android Studio (Via GitHub Actions Gratuito)
Este pacote já inclui o workflow `.github/workflows/build-apk.yml` configurado:
1. Crie um repositório no GitHub e envie os arquivos deste `.zip`.
2. Acesse a aba **Actions** do seu repositório no GitHub e clique em **Build Nexus Mobilador Pro APK**.
3. Em cerca de 2 a 3 minutos, o servidor do GitHub compila o projeto com JDK 17 + Android SDK 35 e disponibiliza o link direto do **`NexusMobiladorPro-APKs.zip`** contendo o `.apk` pronto para instalar no celular.

### Método 2 — Compilação Local no Android Studio (Windows / Linux / macOS)
1. Extraia o arquivo `NexusMobiladorPro-Projeto-Android-Completo.zip`.
2. Abra a pasta no **Android Studio Ladybug (2024.2.1+)** com JDK 17 e Android SDK 35.
3. No menu superior, clique em **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. O arquivo `.apk` será gerado em `app/build/outputs/apk/debug/app-debug.apk`.
