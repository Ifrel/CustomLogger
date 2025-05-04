package Global;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap; // Utiliser pour une Map thread-safe si getLogger est appelé concurrentiellement

/**
 * Gestionnaire de Logger Centralisé (Singleton) avec gestion de fichiers journaliers et résumé par exécution.
 * <p>
 * Cette classe implémente le modèle Singleton et sert de fabrique pour obtenir des instances de {@link LoggerHandle}
 * nommées. Toutes les instances de {@link LoggerHandle} partagent la même configuration et écrivent
 * dans les mêmes fichiers de log journaliers gérés par l'instance unique de cette classe.
 * <p>
 * Le logger crée un répertoire pour la date du jour si l'application est exécutée pour la première fois ce jour-là
 * (ex. logs/2025-05-03/). Les messages de chaque niveau (INFO, WARN, ERROR, FATAL) sont écrits
 * dans un fichier dédié (ex. info.log) à l'intérieur de ce répertoire journalier.
 * Si l'application est relancée le même jour, elle utilisera le même répertoire journalier
 * et ajoutera (append) ses logs aux fichiers existants.
 * <p>
 * Il ajoute des marqueurs de début et de fin pour chaque exécution dans les fichiers de log,
 * incluant un résumé des messages journalisés pendant cette exécution spécifique. Les messages DEBUG
 * ne sont affichés qu'en console si le niveau minimum le permet et ne sont pas enregistrés dans un fichier dédié ici.
 * <p>
 * **NOTE IMPORTANTE :** Cette implémentation est simplifiée à des fins éducatives (ou des petits projets personnels)
 * et ne gère PAS toutes les complexités d'un framework de logging professionnel, telles que :
 * <ul>
 * <li>La stricte gestion de l'accès concurrent aux fichiers si plusieurs threads logguent intensément
 * *exactement au même instant* (un {@code ConcurrentHashMap} est utilisé pour les poignées de logger,
 * mais l'accès aux {@code PrintWriter} n'est pas explicitement synchronisé dans la méthode {@code log},
 * ce qui pourrait causer de très rares problèmes d'entrelacement).</li>
 * <li>La rotation avancée (archivage et nettoyage automatique des anciens répertoires journaliers).</li>
 * <li>Une gestion d'erreurs robuste en cas de problème d'écriture (disque plein, permissions, etc.).</li>
 * <li>Une configuration externe (tout est dans le code).</li>
 * <li>Des performances optimisées pour de très gros volumes de logs.</li>
 * </ul>
 * <p>
 * **Pour une application de production, UTILISEZ IMPÉRATIVEMENT un framework standard**
 * comme SLF4j couplé à Logback ou Log4j2.
 *
 * @author Ifrel Rinel MAKOUNDIKA KIDZOUNOU @see<a href="https://github.com/Ifrel"> mon github</a>
 * @version 1.2
 * @since 2025-05-03
 * @see <a href="https://www.slf4j.org/">SLF4j</a>
 * @see <a href="https://logback.qos.ch/">Logback</a>
 * @see <a href="https://logging.apache.org/log4j/2.x/">Apache Log4j 2</a>
 */
public class CustomLogger { // La classe principale devient le gestionnaire singleton



    /**
     * Niveaux de sévérité pour les messages de journalisation.
     * L'ordre de déclaration (ordinal) définit la hiérarchie pour le filtrage.
     */
    public enum Level {
        DEBUG, // Messages très détaillés pour le débogage (ordinal 0)
        INFO,  // Informations générales sur le déroulement normal (ordinal 1)
        WARN,  // Avertissements, situations potentiellement problématiques (ordinal 2)
        ERROR, // Erreurs qui affectent une fonctionnalité mais non bloquantes (ordinal 3)
        FATAL  // Erreurs très graves, pouvant entraîner l'arrêt de l'application (ordinal 4)
    }

    // --- Instance unique du gestionnaire (Singleton) ---
    // Initialisation EAGER (dès le chargement de la classe)
    // L'application principale peut configurer le niveau minimum ICI si nécessaire,
    // avant que getLogger ne soit appelé pour la première fois.
    private static final CustomLogger INSTANCE = new CustomLogger("Application"); // Nom par défaut du logger manager

    // --- Attributs du Gestionnaire Singleton ---
    private final String managerName; // Nom de ce gestionnaire (pour les marqueurs d'exécution)
    private Level minimumLevel; // Niveau minimum de message à traiter (configuration globale)
    private final LocalDateTime executionStartTime; // Timestamp du début de cette exécution

    // Mappe pour stocker les écrivains de fichier pour chaque niveau ayant un fichier dédié
    private final Map<Level, PrintWriter> levelWriters;
    // Mappe pour stocker le compte des messages loggés par cette exécution, par niveau
    private final Map<Level, Long> executionCounts;
    // Mappe pour stocker les instances de LoggerHandle créées par nom (simple cache)
    private final Map<String, LoggerHandle> loggerHandles; // Utiliser ConcurrentHashMap pour Thread Safety si getLogger est appelé par plusieurs threads

    // Formatteurs de date et heure
    private DateTimeFormatter directoryDateFormatter; // Pour le nom du répertoire journalier (date seule)
    private final DateTimeFormatter messageFormatter; // Pour les timestamps dans les messages

    // Définition des noms de base des fichiers de log par niveau ayant un fichier dédié (INFO et supérieurs)
    private static Map<Level, String> LOG_FILE_BASE_NAMES;
    private static Path BASE_LOG_DIRECTORY ;                // Répertoire de base pour tous les logs




    /**
     * Constructeur privé pour l'instance unique du gestionnaire de logger.
     * Initialise le système de journalisation, crée les répertoires/fichiers, et écrit le marqueur de début.
     *
     * @param managerName Le nom du gestionnaire de logger (utilisé dans les marqueurs d'exécution).
     * @throws RuntimeException si la création des répertoires ou l'ouverture d'un fichier échoue.
     */
    public CustomLogger(String managerName) {
        this.managerName = Objects.requireNonNull(managerName, "Le nom du gestionnaire de logger ne peut pas être null.");
        this.minimumLevel = Level.INFO; // Niveau minimum par défaut, peut être changé par setMinimumLevel()
        this.executionStartTime = LocalDateTime.now(); // Enregistre l'heure de début de cette exécution

        LOG_FILE_BASE_NAMES = new HashMap<>();
        // Associe un niveau au nom de base du fichier (ex: info.log)
        LOG_FILE_BASE_NAMES.put(Level.INFO, "info");
        LOG_FILE_BASE_NAMES.put(Level.WARN, "warn");
        LOG_FILE_BASE_NAMES.put(Level.ERROR, "error");
        LOG_FILE_BASE_NAMES.put(Level.FATAL, "fatal");
        // Si vous voulez un fichier debug.log, ajoutez LOG_FILE_BASE_NAMES.put(Level.DEBUG, "debug"); ici.
        BASE_LOG_DIRECTORY = Paths.get(".logs"); // Répertoire de base pour tous les logs



        // Initialiser les structures de données
        this.levelWriters = new HashMap<>();
        this.executionCounts = new HashMap<>();
        this.loggerHandles = new ConcurrentHashMap<>(); // Utiliser ConcurrentHashMap pour la gestion des handles

        // Formatteurs de date et heure
        this.directoryDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd"); // Pour le nom du répertoire (date seule)
        this.messageFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"); // Pour les timestamps dans les messages

        // --- Création du répertoire journalier ---
        LocalDate today = LocalDate.now();
        String dailyDirName = today.format(directoryDateFormatter);
        Path dailyLogDirectory = BASE_LOG_DIRECTORY.resolve(dailyDirName); // Chemin complet: logs/AAAA-MM-JJ/

        try {
            // Créer le répertoire de base "logs" ET le sous-répertoire journalier s'ils n'existent pas
            Files.createDirectories(dailyLogDirectory);
        } catch (IOException e) {
            System.err.println("ERREUR GRAVE: Impossible de créer le répertoire de logs journalier: " + dailyLogDirectory.toAbsolutePath());
            e.printStackTrace();
            // L'écriture fichier ne sera pas possible, mais on ne relance pas d'exception ici
            // pour permettre au logger de continuer à logger sur la console.
            // Les levelWriters resteront vides.
        }

        // --- Initialiser les écrivains de fichier pour la journée en cours ---
        // Cette méthode essaiera d'ouvrir les fichiers. Si le répertoire n'a pas pu être créé, cela échouera sans relancer d'exception.
        initFileWriters(dailyLogDirectory);

        // --- Écrire le marqueur de début d'exécution ---
        // On écrit le marqueur avec un niveau INFO. Il n'apparaîtra dans les fichiers que si l'ouverture
        // a réussi et que le niveau minimum est <= INFO.
        writeExecutionMarker(Level.INFO, String.format("---------- Début de l'exécution de %s ----------", managerName));

        // Initialiser les compteurs de messages de cette exécution
        for (Level level : Level.values()) {
            this.executionCounts.put(level, 0L); // Initialise tous les niveaux à 0
        }

        System.out.println(String.format("Gestionnaire de Logger '%s' initialisé (niveau min par défaut: %s).",
                managerName, minimumLevel));

        // --- IMPORTANT : Ajouter un Shutdown Hook pour s'assurer que close() est appelée ---
        // Un shutdown hook est un thread qui s'exécute lorsque la JVM s'arrête proprement.
        Runtime.getRuntime().addShutdownHook(new Thread(this::close)); // Utilise une référence de méthode à l'instance singleton
        System.out.println("Shutdown hook pour le logger enregistré.");
    }




    /**
     * Retourne une poignée (handle) de logger pour le nom spécifié.
     * Chaque appel avec le même nom retournera la même instance de poignée.
     *
     * @param name Le nom du logger (généralement le nom de la classe appelante).
     * @return Une instance de {@link LoggerHandle} pour le nom spécifié.
     */
    public static LoggerHandle getLogger(String name) {
        // Utilise computeIfAbsent pour créer la poignée si elle n'existe pas
        // et la mettre dans la map, tout en gérant la concurrence.
        return INSTANCE.loggerHandles.computeIfAbsent(name, LoggerHandle::new);
    }



    /**
     * Représente une poignée de logger nommée.
     * Les méthodes de log de cette poignée appellent la méthode de journalisation
     * de l'instance unique de {@link CustomLogger}, en lui passant le nom de la poignée.
     */
    public static class LoggerHandle {
        private final String name; // Le nom de ce logger (par ex. le nom de la classe)

        /**
         * Constructeur privé pour une poignée de logger nommée.
         * Doit être créé via {@link CustomLogger#getLogger(String)}.
         *
         * @param name Le nom de ce logger.
         */
        private LoggerHandle(String name) {
            this.name = Objects.requireNonNull(name, "Le nom de la poignée de logger ne peut pas être null.");
        }

        // Méthodes publiques pour chaque niveau, qui appellent le logger singleton

        public void debug(String message) {
            INSTANCE.log(Level.DEBUG, this.name, message);
        }

        public void info(String message) {
            INSTANCE.log(Level.INFO, this.name, message);
        }

        public void warn(String message) {
            INSTANCE.log(Level.WARN, this.name, message);
        }

        public void error(String message) {
            INSTANCE.log(Level.ERROR, this.name, message);
        }

        public void fatal(String message) {
            INSTANCE.log(Level.FATAL, this.name, message);
        }

        // Ajouter d'autres méthodes si le logger singleton en a d'autres publiques pour les poignées
    }



    /**
     * Définit le niveau minimum de messages qui seront traités.
     * Les messages dont le niveau est strictement inférieur seront ignorés.
     *
     * @param minimumLevel Le nouveau niveau minimum.
     */
    private void setMinimumLevel(Level minimumLevel) {
        this.minimumLevel = Objects.requireNonNull(minimumLevel, "Le niveau minimum ne peut pas être null.");
    }



    // --- Méthodes de Configuration du Gestionnaire Singleton ---

    /**
     * Définit le niveau minimum de messages qui seront traités par TOUTES les poignées de logger.
     * Les messages dont le niveau est strictement inférieur à ce seuil seront ignorés.
     *
     * {@code DEBUG} Messages très détaillés pour le débogage (ordinal 0)
     * {@code INFO} Informations générales sur le déroulement normal (ordinal 1)
     * {@code WARN} Avertissements, situations potentiellement problématiques (ordinal 2)
     * {@code ERROR} Erreurs qui affectent une fonctionnalité mais non bloquantes (ordinal 3)
     * {@code FATAL} Erreurs très graves, pouvant entraîner l'arrêt de l'application (ordinal 4)
     *
     * par défaut : INFO
     *
     * @param minimumLevel Le nouveau niveau minimum.
     *
     */
    public void setGlobalMinimumLevel(String minimumLevel) {
        switch (minimumLevel){
            case "DEBUG":
                INSTANCE.setMinimumLevel(Level.DEBUG); // Appelle la méthode privée de l'instance singleton
                break;
            case "INFO":
                INSTANCE.setMinimumLevel(Level.INFO);
                break;
            case "WARN":
                INSTANCE.setMinimumLevel(Level.WARN);
                break;
            case "ERROR":
                INSTANCE.setMinimumLevel(Level.ERROR);
                break;
            case "FATAL":
                INSTANCE.setMinimumLevel(Level.FATAL);
                break;
            default:
                INSTANCE.log(Level.WARN, "Application", "Erreur de définition du niveau minimum de messages qui seront traités par TOUTES les poignées de logge");
                break;
        }
    }



    // --- Initialisation / Gestion des écrivains de fichier (méthodes privées du Singleton) ---

    /**
     * Ouvre ou réutilise les fichiers de log dans le répertoire journalier spécifié en mode ajout (append).
     * Un écrivain est créé pour chaque niveau défini dans LOG_FILE_BASE_NAMES.
     *
     * @param dailyLogDirectory Le répertoire journalier dans lequel ouvrir les fichiers.
     * @throws RuntimeException si l'ouverture d'un fichier échoue.
     */
    private void initFileWriters(Path dailyLogDirectory) {
        // Ferme les écrivains existants si applicable
        closeFileWriters();

        for (Map.Entry<Level, String> entry : LOG_FILE_BASE_NAMES.entrySet()) {
            Level level = entry.getKey(); // Niveau (ex: INFO)
            String baseFileName = entry.getValue(); // Nom de base (ex: "info")
            // Construit le nom de fichier à l'intérieur du répertoire journalier (ex: info.log)
            String fileName = baseFileName + ".log";
            Path filePath = dailyLogDirectory.resolve(fileName); // Chemin complet: logs/AAAA-MM-JJ/info.log

            try {
                // Ouvre le fichier en mode ajout (append = true).
                // Si le fichier n'existe pas, il est créé dans le répertoire journalier.
                FileWriter fw = new FileWriter(filePath.toFile(), true);
                PrintWriter pw = new PrintWriter(fw, true); // true pour auto-flush
                this.levelWriters.put(level, pw);
            } catch (IOException e) {
                System.err.println(String.format("ERREUR : Impossible d'ouvrir/écrire dans le fichier de log journalier pour le niveau %s : %s", level, filePath.toAbsolutePath()));
                e.printStackTrace();
                // L'écriture fichier ne sera pas possible pour ce niveau, le levelWriter restera absent/null dans la map.
            }
        }
    }



    /**
     * Ferme tous les écrivains de fichier ouverts.
     * C'est une méthode privée du gestionnaire singleton.
     */
    private void closeFileWriters() {
        for (PrintWriter writer : levelWriters.values()) {
            if (writer != null) {
                writer.close(); // Ferme le flux
            }
        }
        levelWriters.clear(); // Vide la map
    }




    /**
     * Écrit un marqueur spécial dans les fichiers de log.
     * Utilisé pour les marqueurs de début/fin d'exécution et le résumé.
     * C'est une méthode privée du gestionnaire singleton.
     *
     * @param level Le niveau auquel associer le marqueur (détermine dans quels fichiers il est écrit).
     * @param markerMessage Le message du marqueur.
     */
    private void writeExecutionMarker(Level level, String markerMessage) {
        String timestamp = LocalDateTime.now().format(messageFormatter);
        // Le format du marqueur est le même que celui des messages normaux dans les fichiers
        // Note: Le loggerName utilisé dans le marqueur est celui du *gestionnaire*, pas d'une poignée spécifique
        String formattedMarker = String.format("[%s] [%s] [%s] %s",
                timestamp,
                level.name(),
                managerName, // Utilise le nom du gestionnaire ici
                markerMessage);

        // Écrit le marqueur dans le fichier correspondant au niveau spécifié et tous les niveaux supérieurs
        // qui ont un fichier dédié (selon LOG_FILE_BASE_NAMES)
        // Synchroniser sur levelWriters ou les PrintWriter individuels pour la thread-safety
        // Dans cette implémentation simple, on suppose que les appels sont rares ou que la non-thread-safety est acceptable.
        for (Map.Entry<Level, PrintWriter> entry : levelWriters.entrySet()) {
            if (entry.getKey().ordinal() >= level.ordinal()) {
                PrintWriter writer = entry.getValue();
                if (writer != null) {
                    // Synchroniser si thread-safe est requis pour l'écriture fichier
                    // synchronized(writer) { writer.println(...) }
                    writer.println(formattedMarker);
                    writer.println(); // Ajoute une ligne vide après le marqueur pour la lisibilité
                }
            }
        }
        // S'assurer que le marqueur apparaît sur la console aussi, si le niveau minimum le permet
        if (level.ordinal() >= this.minimumLevel.ordinal()) {
            if (level.ordinal() >= Level.WARN.ordinal()) { // Niveaux WARN, ERROR, FATAL
                System.err.println(formattedMarker); // Utilise System.err pour les niveaux élevés
            } else {
                System.out.println(formattedMarker); // Utilise System.out pour INFO, DEBUG
            }
        }
    }


    // --- Méthode interne de journalisation (appelée par les poignées) ---

    /**
     * Traite un message de journalisation provenant d'une poignée de logger nommée.
     * Incrémente le compteur pour le niveau de message pour cette exécution.
     * Formatte le message différemment pour la console et les fichiers.
     * Envoie à la console et écrit dans le fichier correspondant au niveau (si configuré).
     * <p>
     * C'est une méthode privée du gestionnaire singleton.
     *
     * @param level     Le niveau du message.
     * @param loggerName Le nom de la poignée de logger d'où provient le message.
     * @param message   Le message à journaliser (peut être null).
     */
    private void log(Level level, String loggerName, String message) {
        // Incrémente le compteur pour ce niveau pour l'exécution actuelle
        // Utilise computeIfAbsent pour initialiser à 0L si le niveau n'était pas encore présent
        executionCounts.computeIfAbsent(level, k -> 0L);
        executionCounts.merge(level, 1L, Long::sum);


        // Vérifie si le niveau du message est supérieur ou égal au niveau minimum configuré globalement
        if (level.ordinal() >= this.minimumLevel.ordinal()) {

            String currentMessage = message == null ? "null" : message;
            String timestamp = LocalDateTime.now().format(messageFormatter);

            // --- Format pour la console (Infos nécessaires + nom du logger source) ---
            String consoleMessage = String.format("[%s] [%s] %s",
                    level.name(),
                    loggerName, // Ajoute le nom du logger source dans la console
                    currentMessage);

            // --- Format pour le fichier (Infos complètes + nom du logger source) ---
            String fileMessage = String.format("[%s] [%s] [%s] %s",
                    timestamp,
                    level.name(),
                    loggerName, // Ajoute le nom du logger source dans le fichier
                    currentMessage);

            // --- Affichage sur la console ---
            // Utilise System.err pour WARN, ERROR, FATAL
            if (level.ordinal() >= Level.WARN.ordinal()) { // Niveaux WARN, ERROR, FATAL
                System.err.println(consoleMessage);
            } else { // Niveaux DEBUG, INFO
                System.out.println(consoleMessage);
            }

            // --- Écriture dans le fichier correspondant (si ce niveau a un fichier dédié) ---
            // Vérifie si le niveau du message a un écrivain de fichier associé (INFO, WARN, ERROR, FATAL dans ce cas)
            if (LOG_FILE_BASE_NAMES.containsKey(level)) {
                PrintWriter writer = levelWriters.get(level);
                if (writer != null) {
                    // Synchroniser ici si thread-safe est requis pour l'écriture fichier
                    // synchronized(writer) { writer.println(fileMessage); }
                    writer.println(fileMessage); // Écriture simple (potentiellement non thread-safe si appels très fréquents et concurrents)
                    // L'auto-flush est activé (PrintWriter(fw, true))
                } else {
                    // Ceci ne devrait pas arriver si initFileWriters n'a pas échoué pour ce niveau,
                    // mais gère le cas par sécurité.
                    // Note: on n'utilise pas ce logger pour journaliser cette erreur interne,
                    // on utilise System.err directement.
                    System.err.println(String.format("ERREUR INTERNE LOGGER: Écrivain de fichier non trouvé ou non initialisé pour le niveau: %s", level));
                }
            }
            // Les messages DEBUG ne sont pas écrits dans des fichiers spécifiques ici.
        }
    }





    /**
     * Écrit le marqueur de fin d'exécution avec le résumé des messages de cette exécution,
     * puis ferme tous les fichiers de log ouverts.
     * <p>
     * **Cette méthode DOIT être appelée avant la fin de l'application**
     * pour garantir que les logs de la fin d'exécution et le résumé sont bien enregistrés
     * et que les ressources (flux de fichiers) sont libérées.
     * <p>
     * L'appel de cette méthode est automatiquement géré par un {@link Runtime#addShutdownHook(Thread)}
     * enregistré lors de l'initialisation de l'instance unique. Cependant, si vous arrêtez la JVM
     * de manière non propre ({@code kill -9}), ce hook pourrait ne pas s'exécuter.
     * C'est une méthode privée du gestionnaire singleton.
     */
    private void close() {
        // Écrire le marqueur de fin d'exécution avec le résumé des messages de cette exécution
        // On écrit le marqueur avec un niveau INFO pour qu'il apparaisse dans info.log (et fichiers supérieurs)
        writeExecutionMarker(Level.INFO, formatExecutionSummary());

        // Fermer tous les écrivains de fichier ouverts
        closeFileWriters();

        System.out.println(String.format("Gestionnaire de Logger '%s' arrêté. Fichiers de log fermés.", managerName));
    }




    /**
     * Génère le message de résumé de l'exécution actuelle.
     * Calcule la durée de l'exécution et liste le nombre de messages loggés par niveau
     * pendant cette exécution.
     *
     * @return La chaîne de caractères du résumé formaté pour les fichiers de log.
     */
    private String formatExecutionSummary() {
        LocalDateTime executionEndTime = LocalDateTime.now();
        Duration duration = Duration.between(executionStartTime, executionEndTime);

        StringBuilder summary = new StringBuilder();
        summary.append(String.format("---------- Fin de l'exécution de %s [%s] (Durée: ~%d secondes) ----------\n",
                managerName, executionEndTime.format(messageFormatter), duration.getSeconds()));

        summary.append("Résumé des messages loggés pendant cette exécution par niveau:\n");
        // Afficher les comptes par niveau, du plus sévère au moins sévère
        Level[] levelsInOrder = Level.values();
        boolean hasLoggedAnything = false;
        for (int i = levelsInOrder.length - 1; i >= 0; i--) { // Parcourir de FATAL à DEBUG
            Level level = levelsInOrder[i];
            Long count = executionCounts.get(level);
            if (count != null && count > 0) {
                summary.append(String.format("  - %s: %d\n", level.name(), count));
                hasLoggedAnything = true;
            }
        }

        if (!hasLoggedAnything) {
            summary.append("  (Aucun message loggé par cette exécution au-dessus du niveau DEBUG).\n");
        }

        // Optionnel : Ajouter un résumé par logger source si besoin (complexité accrue)
        // summary.append("\nRésumé par logger source :\n");
        // ... logique pour parcourir les messages loggés par loggerHandle ...


        summary.append("---------------------------------------------------------------------------------------------------------------------------------");

        return summary.toString();
    }

}
