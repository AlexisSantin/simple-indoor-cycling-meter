# Application de Vélo Indoor Android

Cette application Android se connecte à un capteur Stages Bluetooth pour récupérer les données de cadence et de puissance, puis calcule et affiche la vitesse et la distance en temps réel.

## Services Bluetooth utilisés
- Cycling Power Service (UUID: 0x1818) pour récupérer puissance et cadence
- Generic Access Service (UUID: 0x1800)
- Generic Attribute Service (UUID: 0x1801)

## Fonctionnalités principales
1. Connexion Bluetooth LE au capteur Stages
2. Lecture des données de puissance et cadence
3. Calcul de la vitesse basé sur la puissance et cadence
4. Calcul de la distance cumulative
5. Affichage en temps réel des métriques
6. Compatible Android 9 (API 28)

## Technologies utilisées
- Android SDK (API 28 minimum)
- Bluetooth Low Energy (BLE)
- Java/Kotlin pour le développement
- Gradle pour la compilation et génération APK

## Structure du projet
- Application native Android
- Interface simple et claire pour l'affichage des métriques
- Gestion des permissions Bluetooth
- Logique de calcul de vitesse/distance optimisée