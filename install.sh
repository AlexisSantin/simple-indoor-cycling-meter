#!/bin/bash

# Script d'installation de l'application Vélo Indoor sur tablette Android 9
# Assurez-vous que ADB est installé et que le débogage USB est activé sur votre tablette

echo "=== Installation de l'Application Vélo Indoor ==="
echo ""

# Chemin vers l'APK
APK_DEBUG="./app/build/outputs/apk/debug/app-debug.apk"
APK_RELEASE="./app/build/outputs/apk/release/app-release-unsigned.apk"

# Vérification de la présence d'ADB
if ! command -v adb &> /dev/null; then
    echo "❌ ADB n'est pas installé. Installez-le avec :"
    echo "   sudo apt install android-tools-adb"
    echo "   Ou téléchargez les Android SDK Platform Tools"
    exit 1
fi

# Fonction pour vérifier la connexion de la tablette
check_device() {
    devices=$(adb devices | grep -v "List of devices" | grep "device$" | wc -l)
    if [ "$devices" -eq 0 ]; then
        echo "❌ Aucun appareil Android connecté."
        echo ""
        echo "Pour connecter votre tablette :"
        echo "1. Activez le mode développeur (Paramètres > À propos > Appuyez 7 fois sur 'Numéro de build')"
        echo "2. Activez le débogage USB (Paramètres > Options pour les développeurs > Débogage USB)"
        echo "3. Connectez votre tablette via USB"
        echo "4. Autorisez le débogage USB sur la tablette"
        echo ""
        return 1
    fi
    return 0
}

# Menu de choix
echo "Choisissez le type d'APK à installer :"
echo "1. Debug APK (app-debug.apk) - Pour développement/test"
echo "2. Release APK (app-release-unsigned.apk) - Version optimisée"
echo ""
read -p "Votre choix (1 ou 2) : " choice

case $choice in
    1)
        APK_FILE="$APK_DEBUG"
        echo "📦 Installation de la version Debug..."
        ;;
    2)
        APK_FILE="$APK_RELEASE"
        echo "📦 Installation de la version Release..."
        ;;
    *)
        echo "❌ Choix invalide. Utilisation de la version Debug par défaut."
        APK_FILE="$APK_DEBUG"
        ;;
esac

# Vérification de l'existence du fichier APK
if [ ! -f "$APK_FILE" ]; then
    echo "❌ Fichier APK non trouvé : $APK_FILE"
    echo "Compilez d'abord l'application avec :"
    echo "   ./gradlew assembleDebug    # pour la version debug"
    echo "   ./gradlew assembleRelease  # pour la version release"
    exit 1
fi

echo ""
echo "🔍 Vérification de la connexion de la tablette..."

# Vérification de la connexion
if ! check_device; then
    exit 1
fi

echo "✅ Tablette détectée !"
adb devices

echo ""
echo "📱 Installation de l'application sur la tablette..."

# Désinstallation de l'ancienne version (si elle existe)
echo "🗑️  Suppression de l'ancienne version (si présente)..."
adb uninstall com.cyclingapp.indoor 2>/dev/null || true

# Installation de la nouvelle version
echo "⬆️  Installation de la nouvelle version..."
if adb install "$APK_FILE"; then
    echo ""
    echo "✅ Installation réussie !"
    echo ""
    echo "🚴‍♂️ L'application 'Vélo Indoor' est maintenant installée sur votre tablette."
    echo ""
    echo "Pour utiliser l'application :"
    echo "1. Allumez votre capteur Stages"
    echo "2. Activez le Bluetooth sur la tablette"
    echo "3. Lancez l'app 'Vélo Indoor'"
    echo "4. Appuyez sur 'Se connecter'"
    echo "5. Profitez de votre séance ! 🚴‍♂️💨"
    echo ""
else
    echo ""
    echo "❌ Erreur lors de l'installation."
    echo ""
    echo "Solutions possibles :"
    echo "- Vérifiez que le débogage USB est activé"
    echo "- Autorisez l'installation d'applications inconnues"
    echo "- Vérifiez l'espace de stockage disponible"
    echo "- Redémarrez la tablette et ressayez"
    exit 1
fi