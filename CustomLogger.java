import javax.swing.*;
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

/**
 * Logger Personnalisé Basique avec un répertoire journalier par exécution et résumé par exécution.
 * <p>
 * Ce logger crée un répertoire pour la date du jour si l'application est exécutée pour la première fois ce jour-là
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
 * <li>La stricte gestion de l'accès concurrent aux fichiers si plusieurs instances du logger
 * écrivent dans les mêmes fichiers exactement au même instant (risque faible mais existant).</li>
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
 * @version 1.1
 * @since 2025-05-03
 * @see <a href="https://www.slf4j.org/">SLF4j</a>
 * @see <a href="https://logback.qos.ch/">Logback</a>
 * @see <a href="https://logging.apache.org/log4j/2.x/">Apache Log4j 2</a>
 */
public class CustomLogger {

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

    // --- Attributs du Logger ---
    private final String loggerName;                // Nom de ce logger (souvent le nom de la classe qui l'utilise)
    private Level minimumLevel;                     // Niveau minimum de message à traiter
    private final LocalDateTime executionStartTime; // Timestamp du début de cette exécution

    // Mappe pour stocker les écrivains de fichier pour chaque niveau ayant un fichier dédié
    // La clé est le niveau, la valeur est le PrintWriter pour le fichier correspondant dans le dossier journalier
    private final Map<Level, PrintWriter> levelWriters;
    private final Map<Level, Long> executionCounts;     // Mappe pour stocker le compte des messages loggés par cette exécution, par niveau

    // Formatteurs de date et heure
    private DateTimeFormatter directoryDateFormatter;   // Pour le nom du répertoire journalier (date seule)
    private DateTimeFormatter messageFormatter;         // Pour les timestamps dans les messages

    // Définition des noms de base des fichiers de log par niveau ayant un fichier dédié (INFO et supérieurs)
    private static final Map<Level, String> LOG_FILE_BASE_NAMES = new HashMap<>();
    static {
        // Associe un niveau au nom de base du fichier (ex: info.log)
        LOG_FILE_BASE_NAMES.put(Level.INFO, "info");
        LOG_FILE_BASE_NAMES.put(Level.WARN, "warn");
        LOG_FILE_BASE_NAMES.put(Level.ERROR, "error");
        LOG_FILE_BASE_NAMES.put(Level.FATAL, "fatal");
        // Note: DEBUG n'est pas dans cette map, il n'est loggé qu'en console si le niveau minimum le permet.
        // Si vous voulez un fichier debug.log, ajoutez LOG_FILE_BASE_NAMES.put(Level.DEBUG, "debug"); ici.
    }

    private static final Path BASE_LOG_DIRECTORY = Paths.get("logs"); // Répertoire de base pour tous les logs



    /**
     * Crée un nouveau Custom_Logger.
     * Détermine la date du jour, crée le répertoire journalier si nécessaire,
     * ouvre les fichiers de log journaliers en mode ajout, et écrit le marqueur de début d'exécution.
     *
     * @param loggerName   Le nom de ce logger (souvent le nom de la classe qui l'utilise).
     * @param minimumLevel Le niveau minimum de messages qui seront traités (loggés).
     * Les messages dont le niveau est strictement inférieur seront ignorés.
     * @throws RuntimeException si la création des répertoires ou l'ouverture d'un fichier échoue.
     */
    public CustomLogger(String loggerName, Level minimumLevel) {
        this.loggerName = Objects.requireNonNull(loggerName, "Le nom du logger ne peut pas être null.");
        this.minimumLevel = Objects.requireNonNull(minimumLevel, "Le niveau minimum ne peut pas être null.");
        this.executionStartTime = LocalDateTime.now(); // Enregistre l'heure de début de cette exécution

        // Formatteurs de date et heure
        this.directoryDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd"); // Pour le nom du répertoire (date seule)
        this.messageFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"); // Pour les timestamps dans les messages (ajout des millisecondes)

        // Initialiser les structures de données
        this.levelWriters = new HashMap<>();
        this.executionCounts = new HashMap<>(); // Initialise les compteurs pour cette exécution

        // --- Création du répertoire journalier ---
        LocalDate today = LocalDate.now();
        String dailyDirName = today.format(directoryDateFormatter);
        Path dailyLogDirectory = BASE_LOG_DIRECTORY.resolve(dailyDirName); // Chemin complet: logs/AAAA-MM-JJ/

        try {
            // Créer le répertoire de base "logs" ET le sous-répertoire journalier s'ils n'existent pas
            Files.createDirectories(dailyLogDirectory);
            // System.out.println("Répertoire journalier vérifié/créé: " + dailyLogDirectory.toAbsolutePath()); // Utile pour le débogage de l'initialisation
        } catch (IOException e) {
            System.err.println("ERREUR GRAVE: Impossible de créer le répertoire de logs journalier: " + dailyLogDirectory.toAbsolutePath());
            e.printStackTrace();
            // Relancer une RuntimeException car l'écriture fichier ne sera pas possible
            throw new RuntimeException("Impossible d'initialiser le logger fichier: création répertoire échouée.", e);
        }

        // --- Initialiser les écrivains de fichier pour la journée en cours ---
        initFileWriters(dailyLogDirectory);

        // --- Écrire le marqueur de début d'exécution ---
        // On écrit le marqueur avec un niveau INFO pour qu'il apparaisse dans info.log (et fichiers supérieurs)
        writeExecutionMarker(Level.INFO, String.format("---------- Début de l'exécution de %s ----------", loggerName));

        // Initialiser les compteurs de messages de cette exécution
        for (Level level : Level.values()) {
            this.executionCounts.put(level, 0L); // Initialise tous les niveaux à 0
        }


        System.out.println(String.format("Logger '%s' initialisé (niveau min: %s). Logs journaliers dans %s.",
                loggerName, minimumLevel, dailyLogDirectory.toAbsolutePath()));
    }





    /**
     * Définit le niveau minimum de messages qui seront traités.
     * Les messages dont le niveau est strictement inférieur seront ignorés.
     *
     * @param minimumLevel Le nouveau niveau minimum.
     */
    public void setMinimumLevel(Level minimumLevel) {
        this.minimumLevel = Objects.requireNonNull(minimumLevel, "Le niveau minimum ne peut pas être null.");
        System.out.println(String.format("Logger '%s' : Niveau minimum changé à %s.", loggerName, minimumLevel));
    }





    /**
     * Ouvre ou réutilise les fichiers de log dans le répertoire journalier spécifié en mode ajout (append).
     * Un écrivain est créé pour chaque niveau défini dans LOG_FILE_BASE_NAMES.
     *
     * @param dailyLogDirectory Le répertoire journalier dans lequel ouvrir les fichiers.
     * @throws RuntimeException si l'ouverture d'un fichier échoue.
     */
    private void initFileWriters(Path dailyLogDirectory) {
        // Pour cette implémentation simple, on ferme et rouvre si cette méthode est appelée
        // alors que des écrivains sont déjà ouverts. Une version robuste gérerait mieux cela.
        closeFileWriters(); // Ferme les écrivains existants si applicable

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
                // System.out.println("Fichier de log journalier ouvert pour niveau " + level + " : " + filePath.toAbsolutePath()); // Utile pour le débogage
            } catch (IOException e) {
                System.err.println(String.format("ERREUR : Impossible d'ouvrir/écrire dans le fichier de log journalier pour le niveau %s : %s", level, filePath.toAbsolutePath()));
                e.printStackTrace();
                // Si l'ouverture d'un fichier échoue, on journalise l'erreur et on ne met pas d'écrivain pour ce niveau.
                // Cela permet aux autres niveaux de continuer à logger dans leurs fichiers respectifs.
                // MAIS, si c'est une erreur système (disque plein), toutes les ouvertures échoueront.
            }
        }
    }




    /**
     * Ferme tous les écrivains de fichier ouverts.
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
     *
     * @param level Le niveau auquel associer le marqueur (détermine dans quels fichiers il est écrit).
     * @param markerMessage Le message du marqueur.
     */
    private void writeExecutionMarker(Level level, String markerMessage) {
        String timestamp = LocalDateTime.now().format(messageFormatter);
        // Le format du marqueur est le même que celui des messages normaux dans les fichiers
        String formattedMarker = String.format("[%s] [%s] [%s] %s",
                timestamp,
                level.name(),
                loggerName,
                markerMessage);

        // Écrit le marqueur dans le fichier correspondant au niveau spécifié et tous les niveaux supérieurs
        // qui ont un fichier dédié (selon LOG_FILE_BASE_NAMES)
        for (Map.Entry<Level, PrintWriter> entry : levelWriters.entrySet()) {
            // On écrit le marqueur dans le fichier du niveau 'entry.getKey()' si ce niveau est >= au niveau du marqueur
            if (entry.getKey().ordinal() >= level.ordinal()) {
                PrintWriter writer = entry.getValue();
                if (writer != null) {
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





    /**
     * Traite un message de journalisation s'il a un niveau suffisant.
     * Incrémente le compteur pour le niveau de message pour cette exécution.
     * Formatte le message différemment pour la console et les fichiers.
     * Envoie à la console et écrit dans le fichier correspondant au niveau (si configuré).
     *
     * @param level   Le niveau du message.
     * @param message Le message à journaliser (peut être null).
     */
    private void log(Level level, String message) {
        // Incrémente le compteur pour ce niveau pour l'exécution actuelle
        // Utilise computeIfAbsent pour initialiser à 0L si le niveau n'était pas encore présent
        executionCounts.computeIfAbsent(level, k -> 0L);
        executionCounts.merge(level, 1L, Long::sum);

        // Vérifie si le niveau du message est supérieur ou égal au niveau minimum configuré
        if (level.ordinal() >= this.minimumLevel.ordinal()) {

            String currentMessage = message == null ? "null" : message;
            String timestamp = LocalDateTime.now().format(messageFormatter);

            // --- Format pour la console (Infos nécessaires seulement) ---
            String consoleMessage = String.format("[%s] %s",
                    level.name(),
                    currentMessage);

            // --- Format pour le fichier (Infos complètes) ---
            String fileMessage = String.format("[%s] [%s] [%s] %s",
                    timestamp,
                    level.name(),
                    loggerName,
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
                    writer.println(fileMessage);
                    // L'auto-flush est activé (PrintWriter(fw, true))
                } else {
                    // Ceci ne devrait pas arriver si initFileWriters n'a pas échoué,
                    // mais gère le cas par sécurité.
                    // Note: on n'utilise pas ce logger pour journaliser cette erreur interne,
                    // on utilise System.err directement pour éviter une boucle infinie.
                    System.err.println(String.format("ERREUR INTERNE LOGGER: Écrivain de fichier non trouvé ou non initialisé pour le niveau: %s", level));
                }
            }
            // Les messages DEBUG ne sont pas écrits dans des fichiers spécifiques ici.
        }
    }




    /**
     * Loggue un message de niveau DEBUG.
     * Les messages DEBUG s'affichent sur la console si le niveau minimum le permet,
     * mais ne sont pas enregistrés dans des fichiers spécifiques par défaut.
     *
     * @param message Le message à journaliser.
     */
    public void debug(String message) {
        log(Level.DEBUG, message);
    }




    /**
     * Loggue un message de niveau INFO.
     * Les messages INFO s'affichent sur la console si le niveau minimum le permet
     * et sont enregistrés dans le fichier journalier info.log dans le répertoire du jour.
     *
     * @param message Le message à journaliser.
     */
    public void info(String message) {
        log(Level.INFO, message);
    }




    /**
     * Loggue un message de niveau WARN.
     * Les messages WARN s'affichent sur la console (System.err) si le niveau minimum le permet
     * et sont enregistrés dans le fichier journalier warn.log dans le répertoire du jour.
     *
     * @param message Le message à journaliser.
     */
    public void warn(String message) {
        log(Level.WARN, message);
    }




    /**
     * Loggue un message de niveau ERROR.
     * Les messages ERROR s'affichent sur la console (System.err) si le niveau minimum le permet
     * et sont enregistrés dans le fichier journalier error.log dans le répertoire du jour.
     *
     * @param message Le message à journaliser.
     */
    public void error(String message) {
        log(Level.ERROR, message);
    }



    /**
     * Loggue un message de niveau FATAL.
     * Les messages FATAL s'affichent sur la console (System.err) si le niveau minimum le permet
     * et sont enregistrés dans le fichier journalier fatal.log dans le répertoire du jour.
     *
     * @param message Le message à journaliser.
     */
    public void fatal(String message) {
        log(Level.FATAL, message);
    }



    // --- Méthode de Nettoyage (TRÈS IMPORTANT À APPELER) ---

    /**
     * Écrit le marqueur de fin d'exécution avec le résumé des messages de cette exécution,
     * puis ferme tous les fichiers de log ouverts.
     * <p>
     * **Cette méthode DOIT être appelée avant la fin de l'application**
     * pour garantir que les logs de la fin d'exécution et le résumé sont bien enregistrés
     * et que les ressources (flux de fichiers) sont libérées.
     * <p>
     * Une bonne pratique est d'utiliser un {@link Runtime#addShutdownHook(Thread)}
     * ou d'appeler cette méthode lors de la fermeture de la fenêtre principale de votre application Swing
     * (par exemple, dans un {@link java.awt.event.WindowListener#windowClosing(java.awt.event.WindowEvent)}
     * ou si {@link JFrame#setDefaultCloseOperation(int)} est réglé sur {@link JFrame#EXIT_ON_CLOSE}).
     */
    public void close() {
        // Écrire le marqueur de fin d'exécution avec le résumé des messages de cette exécution
        // On écrit le marqueur avec un niveau INFO pour qu'il apparaisse dans info.log (et fichiers supérieurs)
        writeExecutionMarker(Level.INFO, formatExecutionSummary());

        // Fermer tous les écrivains de fichier ouverts
        closeFileWriters();

        System.out.println(String.format("Logger '%s' arrêté. Fichiers de log fermés.", loggerName));
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
                loggerName, executionEndTime.format(messageFormatter), duration.getSeconds()));

        summary.append("Résumé des messages loggés pendant cette exécution :\n");
        // Afficher les comptes par niveau, du plus sévère au moins sévère
        boolean hasLoggedAnything = false;
        // Parcourir les niveaux dans l'ordre de déclaration (du moins au plus sévère)
        // pour les afficher du plus au moins sévère, on peut inverser l'itération ou stocker dans une liste triée
        // Affichons du plus sévère au moins sévère pour le résumé
        Level[] levelsInOrder = Level.values();
        for (int i = levelsInOrder.length - 1; i >= 0; i--) {
            Level level = levelsInOrder[i];
            Long count = executionCounts.get(level);
            if (count != null && count > 0) {
                summary.append(String.format("  - %s: %d\n", level.name(), count));
                hasLoggedAnything = true;
            }
        }

        if (!hasLoggedAnything) {
            summary.append("  (Aucun message loggé par cette exécution au-dessus du niveau minimum configuré ou du niveau DEBUG).\n");
        }
        summary.append("-------------------------------------------------------------------------------------------------------------------------------------------------------------");

        return summary.toString();
    }

}
