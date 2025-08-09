# Vibe Universe — Getting Started

This repo contains a **Java + libGDX (LWJGL3)** starter set up for both **Maven** and **Gradle**. It opens a window titled “Vibe Universe” and renders a simple 3D sun with one orbiting planet at 60 FPS on most hardware. From here we can grow it into your solar-system + galactic visualizer.

---

## Prerequisites

- **GPU drivers** up to date (NVIDIA/AMD/Intel).
- **Java 17+** (LTS recommended).
- **Maven 3.9+** (if you plan to use Maven).
- **Gradle 8.5+** (if you plan to use Gradle).

libGDX 1.13.x bundles each platform’s natives via `gdx-platform:natives-desktop`, so you *don’t* need to install OpenAL or GLFW separately.

---

## Windows 11 Setup

### 1) Install Java
Using **winget**:

```powershell
winget install EclipseAdoptium.Temurin.21.JDK
# or for Java 17 LTS:
# winget install EclipseAdoptium.Temurin.17.JDK
```

Verify:
```powershell
java -version
```

### 2) (Optional) Install Maven and/or Gradle
- Maven:
  ```powershell
  winget install Apache.Maven
  mvn -v
  ```
- Gradle:
  ```powershell
  winget install Gradle.Gradle
  gradle -v
  ```

> Tip: if `winget` is blocked in your environment, download installers from the vendors’ sites or use Chocolatey/Scoop.

### 3) Run the project
**With Maven** (desktop module):
```powershell
mvn -pl desktop -am package
mvn -pl desktop exec:java -Dexec.mainClass=net.joostvdg.vibe_universe.DesktopLauncher
```
Build a single runnable JAR:
```powershell
mvn -pl desktop -am -DskipTests package
# output: desktop/target/desktop-0.1.0-SNAPSHOT-shaded.jar
java -jar desktop/target/desktop-0.1.0-SNAPSHOT-shaded.jar
```

**With Gradle**:
```powershell
gradle :desktop:run
# or build distribution:
gradle :desktop:installDist
# run the generated app:
.\desktopuild\installibe-universeinibe-universe.bat
```

---

## Ubuntu Linux Setup (22.04/24.04)

### 1) Install Java
```bash
sudo apt update
sudo apt install -y openjdk-17-jdk
# or use 21 if available on your release:
# sudo apt install -y openjdk-21-jdk
java -version
```

> Alternative: use **SDKMAN!** to manage Java/Gradle versions:
> ```bash
> curl -s "https://get.sdkman.io" | bash
> source "$HOME/.sdkman/bin/sdkman-init.sh"
> sdk install java 21-tem
> sdk install gradle 8.9
> ```

### 2) (Optional) Install Maven and/or Gradle
```bash
sudo apt install -y maven
mvn -v
# If you didn't use SDKMAN:
sudo apt install -y gradle
gradle -v
```

> Note: Ubuntu’s `gradle` package can lag behind. If you hit version issues, prefer SDKMAN or the official binary.

### 3) Verify graphics stack
- Make sure proprietary drivers (NVIDIA) or Mesa (AMD/Intel) are installed.
- On X11 or Wayland, LWJGL3 + GLFW should Just Work™ with the bundled natives.

Optional diagnostics:
```bash
glxinfo | grep "OpenGL version"
```

### 4) Run the project
**With Maven**:
```bash
mvn -pl desktop -am package
mvn -pl desktop exec:java -Dexec.mainClass=net.joostvdg.vibe_universe.DesktopLauncher
```
Runnable JAR:
```bash
mvn -pl desktop -am -DskipTests package
java -jar desktop/target/desktop-0.1.0-SNAPSHOT-shaded.jar
```

**With Gradle**:
```bash
gradle :desktop:run
# or build the distribution:
gradle :desktop:installDist
./desktop/build/install/vibe-universe/bin/vibe-universe
```

---

## Project Structure

```
vibe-universe/
  core/           # Cross-platform game logic (libGDX API only)
  desktop/        # Desktop-specific launcher using LWJGL3
  pom.xml         # Maven parent (aggregator) POM
  build.gradle    # Gradle multi-project root
  settings.gradle # Gradle settings
```

- Package name: `net.joostvdg.vibe_universe`
- Window title: **Vibe Universe**

---

## Common Issues & Fixes

- **Black window / crashes on startup**
  - Update GPU drivers.
  - Ensure you are running on a GPU (on laptops, use the discrete GPU).

- **`UnsatisfiedLinkError` for GLFW/OpenAL/etc.**
  - Make sure `gdx-platform:${gdx_version}:natives-desktop` is on the **runtime** classpath.
  - If you modified the POM/Gradle files, re-check the `classifier`/`runtimeOnly` lines.

- **Wayland quirks**
  - If input/windowing acts weird on Wayland, try launching the session under X11/XWayland, or update GLFW/Mesa.

---

## Next Steps

- Add real orbital mechanics (Kepler elements) and time controls.
- Implement scale switching (heliocentric vs. galactic) with logarithmic depth.
- Introduce a soft “Tiny Glade” look via custom GLSL (rim lighting, gentle bloom, FXAA).
