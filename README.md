# CustomLogger - Un Exemple Simple de Logger en Java

Ce dépôt contient une implémentation simple et personnalisée d'un système de journalisation (logger) en Java. Il a été créé à des fins éducatives et comme exemple de code pour illustrer les concepts fondamentaux des loggers, notamment la gestion des niveaux de messages, l'affichage console et l'écriture dans des fichiers organisés par date.

Si vous cherchez à comprendre les mécanismes de base d'un logger ou avez besoin d'une solution de journalisation très simple pour un petit projet non critique, ce code peut vous être utile.

## Fonctionnalités Clés

* **Niveaux de Journalisation :** Supporte les niveaux DEBUG, INFO, WARN, ERROR et FATAL, permettant de classer les messages par sévérité.
* **Gestionnaire Singleton :** Utilise un modèle singleton pour gérer une instance unique responsable de l'écriture des logs dans les fichiers.
* **Poignées Nommées :** Permet d'obtenir des "poignées" (handles) de logger nommées (généralement par nom de classe) qui acheminent les messages vers le gestionnaire singleton.
* **Double Sortie :** Journalise simultanément sur la console (avec un format concis incluant le nom du logger source) et dans des fichiers (avec un format plus détaillé incluant timestamp, niveau, nom du logger source).
* **Organisation par Date :** Crée un répertoire dédié pour chaque jour d'exécution (ex: `logs/2025-05-03/`). Tous les logs de ce jour sont enregistrés dans ce répertoire.
* **Fichiers par Niveau :** À l'intérieur du répertoire journalier, crée des fichiers de log spécifiques pour chaque niveau majeur (INFO, WARN, ERROR, FATAL) à l'intérieur du répertoire journalier (ex: `info.log`, `error.log`).
* **Mode Ajout (Append) :** Si l'application est lancée plusieurs fois le même jour, elle utilise le même répertoire journalier et ajoute les nouveaux logs à la fin des fichiers existants.
* **Marqueurs d'Exécution :** Ajoute des lignes claires pour marquer le début et la fin de chaque exécution de l'application dans les fichiers de log journaliers.
* **Résumé par Exécution :** Inclut un résumé à la fin de chaque bloc d'exécution dans le fichier de log, indiquant le nombre de messages journalisés par niveau *par cette exécution spécifique*.
* **Documentation Intégrée :** Le code est richement commenté (Javadoc).

## Objectif du Projet

Ce projet est conçu comme un **exemple pédagogique**. Il est utile pour :

* Apprendre comment un logger fonctionne en interne et les concepts de gestionnaire/fabrique.
* Comprendre la gestion des flux d'écriture fichier et l'organisation simple de logs par date.
* Avoir une base pour développer un logger *très spécifique* si les frameworks existants sont jugés trop lourds pour un cas d'usage *particulier et non critique*.

## ⚠️ AVERTISSEMENT TRÈS IMPORTANT - NON DESTINÉ À LA PRODUCTION ⚠️

**Cette implémentation est VOLONTAIREMENT simplifiée.** Elle ne possède **AUCUNE** des caractéristiques essentielles requises pour une utilisation fiable en production, notamment :

* **Gestion Limitée de la Concurrence (Non Entièrement Thread-Safe) :** Bien qu'une `ConcurrentHashMap` soit utilisée pour les poignées, l'accès aux `PrintWriter` n'est pas explicitement synchronisé dans la méthode `log`. Cela peut potentiellement (bien que rarement) entraîner de légers problèmes d'entrelacement si de très nombreux threads logguent *intensivement et simultanément*.
* **Gestion d'Erreurs Basique :** Une gestion rudimentaire des erreurs de lecture/écriture fichier (disque plein, permissions, etc.).
* **Pas de Rotation Avancée :** N'archive pas ni ne nettoie automatiquement les anciens fichiers/répertoires journaliers.
* **Pas de Configuration Externe :** Tout le comportement (niveaux, fichiers) est codé en dur.
* **Performance non Optimisée :** Ne convient pas aux applications générant un très gros volume de logs.

> **Pour toute application en production, nécessitant fiabilité, performance et robustesse, veuillez IMPÉRATIVEMENT utiliser un framework de journalisation Java standard et éprouvé par l'industrie, tel que [SLF4j](https://www.slf4j.org/) couplé à [Logback](https://logback.qos.ch/) ou [Apache Log4j 2](https://logging.apache.org/log4j/2.x/).**

## Comment Utiliser

1.  Clonez ou copiez la classe `CustomLogger.java` (et sa classe interne `LoggerHandle`) dans votre projet. Assurez-vous que le package (`com.yourcompany.yourgame.utils` ou autre) est correct.
2.  Dans n'importe quelle classe où vous souhaitez journaliser, obtenez une poignée de logger en utilisant la méthode statique `getLogger()` :
    ```java
    private static final com.yourcompany.yourgame.utils.CustomLogger.LoggerHandle logger =
        com.yourcompany.yourgame.utils.CustomLogger.getLogger("NomDeVotreClasseIci");
    ```
    *(Remplacez `com.yourcompany.yourgame.utils` par votre package réel et `NomDeVotreClasseIci` par le nom de la classe appelante - c'est une bonne pratique).*
3.  Appelez les méthodes de journalisation sur cette poignée : `logger.info("...");`, `logger.warn("...");`, `logger.error("...");`, `logger.fatal("...");`, `logger.debug("...");`.
4.  **Configuration Globale :** Vous pouvez configurer le niveau minimum global du logger au début de votre application (par exemple, dans la méthode `main`) en utilisant la méthode statique `CustomLogger.setGlobalMinimumLevel(...)`. Par défaut, le niveau minimum est INFO.
    ```java
    // Exemple pour changer le niveau global à DEBUG au début de main()
    com.yourcompany.yourgame.utils.CustomLogger.setGlobalMinimumLevel(com.yourcompany.yourgame.utils.CustomLogger.Level.DEBUG);
    ```
5.  **Fermeture du Logger :** La méthode `close()` du gestionnaire singleton est **automatiquement appelée** via un `ShutdownHook` enregistré lors de l'initialisation de l'instance unique. Vous n'avez généralement **pas** besoin d'appeler explicitement `CustomLogger.INSTANCE.close()`. Ce hook assure que les logs de fin d'exécution et le résumé sont écrits lors d'un arrêt normal de la JVM.

## Exemple d'Utilisation (Méthode `main`)

Voici l'exemple de la méthode `main` incluse dans la classe `CustomLogger` qui démontre comment obtenir et utiliser différentes poignées de logger et comment le hook de fermeture fonctionne :

```java
import java.time.Duration; // Assurez-vous que ces imports sont présents si vous copiez la méthode main seule
import java.time.LocalDateTime;
import java.util.Objects;
// ... autres imports nécessaires pour le logger ...

public class CustomLogger { // ... (reste de la classe) ...

    public static void main(String[] args) {
        // Configuration du niveau minimum global de journalisation (optionnel, par défaut INFO)
        // Exemple pour changer le niveau global à DEBUG au début de main() si souhaité:
        // CustomLogger.setGlobalMinimumLevel(CustomLogger.Level.DEBUG);

        // Obtenir des poignées de logger par nom dans différentes "classes" logiques
        // On utilise la méthode statique getLogger()
        LoggerHandle mainLogger = CustomLogger.getLogger("com.yourcompany.yourgame.MainApp");
        LoggerHandle uiLogger = CustomLogger.getLogger("com.yourcompany.yourgame.UI.GameScreen");
        LoggerHandle gameLogicLogger = CustomLogger.getLogger("com.yourcompany.yourgame.GameLogic");


        mainLogger.info("Application démarrée."); // Loggé via la poignée mainLogger

        uiLogger.debug("Mise à jour de l'écran graphique."); // Loggé via la poignée uiLogger (s'affiche si niveau min <= DEBUG)

        try {
            // Simule une opération dans la logique du jeu qui loggue
            gameLogicLogger.info("Tentative de division...");
            int result = divide(10, 2, gameLogicLogger); // Passe la poignée logger à la méthode
            gameLogicLogger.debug("Division réussie, résultat : " + result);

            result = divide(10, 0, gameLogicLogger); // Passe la poignée logger
            gameLogicLogger.info("Résultat de la division par zéro (géré) : " + result); // Loggué INFO
        } catch (Exception e) {
            gameLogicLogger.error("Une erreur inattendue s'est produite dans la logique du jeu : " + e.getMessage()); // Loggué ERROR
        }

        uiLogger.warn("Certaines ressources pourraient être manquantes."); // Loggué WARN

        // Simule une erreur FATAL qui pourrait survenir dans le main
        // Dans une vraie application, une erreur FATAL non gérée entraînerait probablement l'arrêt de la JVM
        // et le shutdown hook serait déclenché.
        if (Math.random() < 0.4) { // 40% de chance de déclencher l'erreur FATAL simulée
             mainLogger.fatal("Une erreur système critique a rendu l'application instable. Arrêt imminent."); // Loggué FATAL
              // Ici, vous pourriez ajouter un System.exit(1); si cette erreur bloque réellement l'application
         }


        mainLogger.info("Application en cours d'exécution..."); // Ce message pourrait apparaître ou non selon l'erreur FATAL simulée

        // L'appel à la méthode close() du gestionnaire singleton est géré automatiquement
        // par le Shutdown Hook enregistré dans le constructeur de CustomLogger.
        // Vous n'avez pas besoin d'appeler CustomLogger.INSTANCE.close() ici.
        // Le hook assure que les logs finaux et le résumé sont écrits.
    }

     // Petite méthode d'exemple montrant comment utiliser une poignée de logger passée en paramètre
     private static int divide(int a, int b, LoggerHandle logger) {
         if (b == 0) {
             logger.warn("Tentative de division par zéro (a=" + a + ", b=" + b + ")"); // Utilise la poignée passée en paramètre
             return 0; // Retourne 0 ou lancez une exception selon la logique du jeu
         }
         // Loggue en debug si le niveau minimum le permet
         logger.debug("Calcul de division : " + a + " / " + b + "."); // Loggué DEBUG
         return a / b;
     }

      // Méthode d'exemple simulant une situation (non utilisée dans main ici, mais pour illustration)
      private static void processData(LoggerHandle logger) {
          logger.info("Traitement des données...");
          // ... logique de traitement ...
          logger.debug("Étape intermédiaire complétée.");
          if (Math.random() > 0.8) {
              logger.error("Erreur de validation des données.");
          }
      }
}
