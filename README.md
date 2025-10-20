# Simple Indoor Cycling Meter

Application Android simple et efficace qui se connecte à votre capteur Stages Bluetooth pour afficher en temps réel :
- Puissance (Watts)
- Cadence (RPM)
- Vitesse calculée (km/h)
- Distance totale (km)
- Calories brûlées (kcal)
- Vitesse moyenne
- Enregistrement de sessions

## Fonctionnalités

✅ **Connexion Bluetooth LE** : Se connecte automatiquement aux capteurs Stages  
✅ **Données en temps réel** : Affichage instantané des métriques de cyclisme  
✅ **Interface intuitive** : Cards avec métriques claires et colorées  
✅ **Compatible Android 9** : Optimisé pour API 28+  
✅ **Calculs automatiques** : Vitesse et distance calculées à partir puissance/cadence  
✅ **Enregistrement de sessions** : Sauvegarde automatique de vos entraînements  
✅ **Historique des sessions** : Consultez vos performances passées  
✅ **Écran de paramètres** : Configuration Bluetooth et poids utilisateur

## Prérequis

- **Android Studio** ou **SDK Android** installé
- **Java 8+** 
- **Tablette/téléphone Android 9+** avec Bluetooth LE
- **Capteur Stages** avec service Cycling Power (UUID: 0x1818)

## Installation et compilation

### 1. Préparation de l'environnement

```bash
# Vérifier que Java est installé
java -version

# Si Java n'est pas installé :
sudo apt update
sudo apt install openjdk-11-jdk

# Définir JAVA_HOME si nécessaire
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
```

### 2. Installation du SDK Android (si pas Android Studio)

```bash
# Télécharger SDK Android
wget https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip
unzip commandlinetools-linux-9477386_latest.zip
mkdir -p ~/Android/Sdk/cmdline-tools
mv cmdline-tools ~/Android/Sdk/cmdline-tools/latest

# Configurer les variables d'environnement
export ANDROID_HOME=~/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

# Installer les composants nécessaires
sdkmanager "platform-tools" "platforms;android-28" "build-tools;28.0.3"
```

### 3. Compilation de l'APK

```bash
# Se placer dans le dossier du projet
cd "/home/alex/Documents/CyclingApp Android 9"

# Nettoyer le projet
./gradlew clean

# Construire l'APK de debug
./gradlew assembleDebug

# Ou construire l'APK de release (signé)
./gradlew assembleRelease
```

### 4. Installation sur la tablette

#### Option A : Installation automatique (Recommandée)
```bash
# Utiliser le script d'installation automatique
./install.sh
```

#### Option B : Installation manuelle via ADB
```bash
# Activer le débogage USB sur la tablette (Paramètres > Options développeur)
# Connecter la tablette en USB

# Vérifier la connexion
adb devices

# Installer l'APK debug (pour tests)
adb install app/build/outputs/apk/debug/app-debug.apk

# Ou installer l'APK release (version optimisée)
adb install app/build/outputs/apk/release/app-release-unsigned.apk
```

#### Option C : Transfert manuel
```bash
# Copier l'APK sur la tablette via câble USB ou cloud
# L'APK se trouve dans : 
#   - Debug: app/build/outputs/apk/debug/app-debug.apk
#   - Release: app/build/outputs/apk/release/app-release-unsigned.apk
# Sur la tablette : autoriser l'installation d'apps inconnues puis ouvrir l'APK
```

## Utilisation

1. **Lancer l'application** sur votre tablette Android
2. **Activer le Bluetooth** si pas déjà fait
3. **Allumer votre capteur Stages** 
4. **Appuyer sur l'icône engrenage** pour accéder aux paramètres
5. **Configurer votre poids** et **se connecter au capteur**
6. **Retourner à l'écran principal** pour voir les métriques en temps réel
7. **Démarrer une session** pour enregistrer votre entraînement

### Permissions requises

L'application demande automatiquement :
- **Bluetooth** : Pour se connecter au capteur
- **Localisation** : Requis pour le scan Bluetooth LE sur Android

## Structure du projet

```
app/
├── src/main/
│   ├── java/com/cyclingapp/indoor/
│   │   └── MainActivity.java          # Logique principale + Bluetooth
│   ├── res/
│   │   ├── layout/
│   │   │   └── activity_main.xml      # Interface utilisateur
│   │   ├── values/
│   │   │   ├── strings.xml            # Textes de l'app
│   │   │   ├── colors.xml             # Couleurs
│   │   │   └── styles.xml             # Styles UI
│   │   └── mipmap-*/                  # Icônes app
│   └── AndroidManifest.xml            # Configuration + permissions
├── build.gradle                       # Configuration module
└── proguard-rules.pro                # Règles d'obfuscation
```

## Dépannage

### L'APK ne se compile pas
```bash
# Vérifier les versions de Gradle et SDK
./gradlew --version
sdkmanager --list

# Nettoyer et rebuilder
./gradlew clean build
```

### L'app ne trouve pas le capteur
- Vérifier que le capteur Stages est allumé et en mode pairing
- S'assurer que le Bluetooth est activé
- Accorder les permissions de localisation
- Redémarrer l'app si nécessaire

### Erreur de permissions
- Aller dans Paramètres > Apps > Simple Indoor Cycling Meter > Permissions
- Activer toutes les permissions (Bluetooth, Localisation)

## Développement

### Services Bluetooth utilisés
- **Cycling Power Service** (0x1818) : Données puissance et cadence
- **Cycling Power Measurement** (0x2A63) : Characteristic principal
- **Client Characteristic Configuration** (0x2902) : Notifications

### Formule de calcul vitesse
```java
// Calcul basé sur la puissance et la cadence, ajusté selon le poids
speedCoefficient = 0.0053 * Math.pow(userWeight / 75.0, 0.33);
speedFromPower = Math.pow(power / speedCoefficient, 1.0 / 3.0);
cadenceInfluence = Math.min(cadence / 90.0, 1.2);
speed = speedFromPower * cadenceInfluence;
```

### Calcul de la distance
```java
// Distance cumulative basée sur la vitesse
distance += (vitesse * temps_écoulé) / 3600000.0  // en km
```

## Licence

Ce projet est à des fins éducatives et personnelles. Utilisez-le librement pour vos besoins de cyclisme indoor !

---

**Bon entraînement ! 🚴‍♂️💪**