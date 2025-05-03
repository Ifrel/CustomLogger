# CustomLogger - Un Exemple Simple de Logger en Java

Ce dépôt contient une implémentation simple et personnalisée d'un système de journalisation (logger) en Java. Il a été créé à des fins éducatives et comme exemple de code pour illustrer les concepts fondamentaux des loggers.

Si vous cherchez à comprendre les mécanismes de base d'un logger ou avez besoin d'une solution de journalisation très simple pour un petit projet non critique, ce code peut vous être utile.

## Fonctionnalités Clés

* **Niveaux de Journalisation :** Supporte les niveaux DEBUG, INFO, WARN, ERROR et FATAL, permettant de classer les messages par sévérité.
* **Double Sortie :** Journalise simultanément sur la console (avec un format concis) et dans des fichiers (avec un format plus détaillé).
* **Organisation par Date :** Crée un répertoire dédié pour chaque jour d'exécution (ex: `logs/2025-05-03/`). Tous les logs de ce jour sont enregistrés dans ce répertoire.
* **Fichiers par Niveau :** À l'intérieur du répertoire journalier, crée des fichiers de log spécifiques pour chaque niveau majeur (INFO, WARN, ERROR, FATAL - ex: `info.log`, `error.log`).
* **Mode Ajout (Append) :** Si l'application est lancée plusieurs fois le même jour, elle utilise le même répertoire journalier et ajoute les nouveaux logs à la fin des fichiers existants.
* **Marqueurs d'Exécution :** Ajoute des lignes claires pour marquer le début et la fin de chaque exécution de l'application dans les fichiers de log journaliers.
* **Résumé par Exécution :** Inclut un résumé à la fin de chaque bloc d'exécution dans le fichier de log, indiquant le nombre de messages journalisés par niveau pendant cette exécution spécifique.
* **Documentation Intégrée :** Le code est richement commenté (Javadoc).

## Objectif du Projet

Ce projet sert principalement d'**exemple pédagogique**. Il est utile pour :

* Apprendre comment un logger fonctionne en interne.
* Comprendre la gestion des flux d'écriture fichier et l'organisation simple de logs.
* Avoir une base pour développer un logger *très spécifique* si les frameworks existants sont jugés trop lourds pour un cas d'usage *particulier et non critique*.

## ⚠️ AVERTISSEMENT TRÈS IMPORTANT - NON DESTINÉ À LA PRODUCTION ⚠️

**Cette implémentation est VOLONTAIREMENT simplifiée.** Elle ne possède **AUCUNE** des caractéristiques essentielles requises pour une utilisation fiable en production, notamment :

* **Pas de Gestion de la Concurrence (Non Thread-Safe) :** Utiliser cette classe avec plusieurs threads logguant simultanément peut entraîner des problèmes ou des pertes de logs.
* **Gestion d'Erreurs Basique :** Une gestion rudimentaire des erreurs de lecture/écriture fichier (disque plein, permissions, etc.).
* **Pas de Rotation Avancée :** N'archive pas ni ne nettoie automatiquement les anciens fichiers/répertoires journaliers.
* **Pas de Configuration Externe :** Tout le comportement (niveaux, fichiers) est codé en dur.
* **Performance non Optimisée :** Ne convient pas aux applications générant un très gros volume de logs.

> **Pour toute application en production, nécessitant fiabilité, performance et robustesse, veuillez IMPÉRATIVEMENT utiliser un framework de journalisation Java standard et éprouvé par l'industrie, tel que [SLF4j](https://www.slf4j.org/) couplé à [Logback](https://logback.qos.ch/) ou [Apache Log4j 2](https://logging.apache.org/log4j/2.x/).**

## Comment Utiliser

1.  Clonez ou copiez la classe `CustomLogger.java` dans votre projet.
2.  Dans la classe où vous souhaitez journaliser ( ou dans un fichier vous permetant d'y avoir accès dans l'ensemble de votre projet), créez une instance du logger (souvent `private static final`) :
    ```java
    private static final com.yourcompany.yourgame.utils.CustomLogger logger =
        new com.yourcompany.yourgame.utils.CustomLogger("NomDeVotreClasse", com.yourcompany.yourgame.utils.CustomLogger.Level.INFO);
    ```
    *(Adaptez le package et le nom de la classe si vous l'avez renommée)*
3.  Appelez les méthodes de journalisation : `logger.info("...");`, `logger.warn("...");`, `logger.error("...");`, `logger.fatal("...");`, `logger.debug("...");`.
4.  **TRÈS IMPORTANT :** Assurez-vous d'appeler la méthode `logger.close()` lorsque votre application s'arrête pour garantir l'écriture des logs finaux et du résumé. La meilleure façon est d'utiliser un `Runtime.addShutdownHook(...)` ou de l'appeler lors de la fermeture de la fenêtre principale de votre application java.

## Documentation

Le code source contient une documentation détaillée au format Javadoc, expliquant chaque classe, méthode et concept.



# // --- Exemple d'utilisation ---
```
public static void main(String[] args) {
    // Crée un logger pour la classe "ApplicationMain" avec un niveau minimum INFO
    // Il utilisera/créera le dossier logs/AAAA-MM-JJ/ et les fichiers info.log, warn.log, error.log, fatal.log à l'intérieur
    CustomLogger appLogger = new CustomLogger("ApplicationMain", Level.INFO);

    // --- IMPORTANT : Ajouter un Shutdown Hook pour s'assurer que close() est appelée ---
    // Un shutdown hook est un thread qui s'exécute lorsque la JVM s'arrête proprement.
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        System.out.println("\nShutdown hook détecté. Fermeture du logger...");
        appLogger.close(); // Appelle la méthode close() pour écrire le résumé et fermer les fichiers
        System.out.println("Logger fermé via shutdown hook.");
    }));
    // Dans une application Swing, vous pourriez aussi appeler appLogger.close()
    // dans un WindowListener de votre JFrame principal, géré par setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE).


    // Loggue des messages de différents niveaux
    appLogger.debug("Ceci est un message de debug (ne s'affiche pas si minLevel > DEBUG sur console et pas de fichier)."); // Niveau DEBUG
    appLogger.info("Démarrage de l'application."); // Niveau INFO
    appLogger.debug("Initialisation des sous-systèmes..."); // Niveau DEBUG

    try {
        // Simule une opération qui pourrait causer un avertissement ou une erreur
        // On passe le logger aux méthodes pour qu'elles puissent journaliser
        int result = divide(10, 0, appLogger);
        appLogger.info("Résultat de la division : " + result);
    } catch (Exception e) {
        appLogger.error("Une erreur s'est produite lors de l'opération : " + e.getMessage()); // Niveau ERROR
    }

    appLogger.info("Traitement principal terminé."); // Niveau INFO
    appLogger.warn("Configuration non standard détectée."); // Niveau WARN

    // Simule une situation d'erreur FATAL
    // Dans une vraie application, une erreur FATAL non gérée entraînerait probablement l'arrêt de la JVM
    // et le shutdown hook serait déclenché.
    if (Math.random() < 0.3) { // Petite chance de déclencher l'erreur FATAL simulée
        appLogger.fatal("Simulation: L'application a rencontré une erreur irrécupérable et va s'arrêter."); // Niveau FATAL
        // Ici, vous pourriez ajouter un System.exit(1); si cette erreur bloque réellement l'application
    }


    appLogger.info("Application en cours..."); // Ce message pourrait apparaître ou non selon l'erreur FATAL simulée

    // L'appel à appLogger.close() est assuré par le Shutdown Hook ou une gestion manuelle à la sortie.
    // Ne pas mettre de System.exit() ici si vous voulez que le hook s'exécute.
}

/**
 * Petite méthode d'exemple pour montrer l'utilisation du logger dans d'autres méthodes.
 * @param a Numérateur.
 * @param b Dénominateur.
 * @param logger L'instance du logger à utiliser.
 * @return Le résultat de la division ou 0 si division par zéro avec avertissement.
 */
private static int divide(int a, int b, CustomLogger logger) {
    if (b == 0) {
        logger.warn("Tentative de division par zéro (a=" + a + ", b=" + b + ")"); // Utilise le logger passé en paramètre
        return 0; // Retourne 0 ou lancez une exception selon la logique du jeu
    }
    // Loggue en debug si le niveau minimum le permet
    logger.debug("Division calculée : " + a + " / " + b + " = " + (a/b));
    return a / b;
}

/**
 * Méthode d'exemple pour simuler une situation pouvant nécessiter un log FATAL.
 * @param logger L'instance du logger à utiliser.
 */
private static void simulateFatalError(CustomLogger logger) {
    // Dans une vraie application, une situation FATAL pourrait être:
    // - Échec de l'initialisation critique (ex: base de données, réseau)
    // - OutOfMemoryError non gérable
    // - Erreur interne logique qui rend l'état du jeu incohérent et irrécupérable
    logger.info("Tentative de simuler une situation FATAL (pour l'exemple)...");
    // Ici, on journalise juste un message FATAL. Une vraie erreur FATAL
    // (comme une Exception ou Error non gérée) entraînerait l'arrêt et le hook.
    // Exemple: throw new OutOfMemoryError("Fake OOM for logging test");
}
```
