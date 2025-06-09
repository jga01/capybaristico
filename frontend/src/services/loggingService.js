let logEntries = [];
let isInitialized = false;

// NEW: Define log levels
const LogLevel = {
    DEBUG: 'DEBUG',
    INFO: 'INFO',
    WARN: 'WARN',
    ERROR: 'ERROR',
    RENDER: 'RENDER', // Special level for rendering logs
};

const getTimestamp = () => new Date().toISOString();

// Helper to handle circular structures in JS objects which are common in React state
const getCircularReplacer = () => {
    const seen = new WeakSet();
    return (key, value) => {
        // Do not serialize React's internal fiber nodes or refs
        if (key === '_owner' || key === '_store' || key === 'ref' || key === 'current') {
            return '[React Internal]';
        }
        if (typeof value === "object" && value !== null) {
            if (seen.has(value)) {
                return "[Circular Reference]";
            }
            seen.add(value);
        }
        return value;
    };
};

/**
 * Initializes (or re-initializes) the logger for a new game session.
 */
export const initLogger = () => {
    logEntries = [];
    isInitialized = true;
    log('--- FRONTEND LOGGER INITIALIZED FOR NEW GAME ---', null, LogLevel.INFO);
    console.log("Frontend logger initialized.");
};

/**
 * The main logging function.
 * @param {string} message A description of what is being logged.
 * @param {object} [data=null] Optional data object to be stringified and included.
 * @param {string} [level=LogLevel.DEBUG] The severity level of the log.
 */
export const log = (message, data = null, level = LogLevel.DEBUG) => {
    if (!isInitialized) initLogger();

    // MODIFIED: Include log level in the entry
    let entry = `[${getTimestamp()}] [${level}] ${message}`;
    if (data) {
        try {
            // Stringify with the circular reference handler and nice formatting
            const jsonData = JSON.stringify(data, getCircularReplacer(), 2);
            entry += `\nDATA: ${jsonData}`;
        } catch (e) {
            entry += `\n[Logging Error] Could not stringify data: ${e.message}`;
        }
    }
    logEntries.push(entry);

    // Use corresponding console methods
    switch (level) {
        case LogLevel.INFO: console.info(`[GAME LOG] ${message}`, data); break;
        case LogLevel.WARN: console.warn(`[GAME LOG] ${message}`, data); break;
        case LogLevel.ERROR: console.error(`[GAME LOG] ${message}`, data); break;
        default: console.log(`[GAME LOG] ${message}`, data); break;
    }
};

/**
 * Creates a downloadable text file from the captured logs.
 * @param {string} gameId The ID of the game to include in the filename.
 */
export const downloadLogs = (gameId) => {
    if (logEntries.length === 0) {
        alert("No logs have been captured yet.");
        return;
    }
    log('--- LOGS DOWNLOADED ---', { gameId }, LogLevel.INFO);
    const blob = new Blob([logEntries.join('\n\n========================================\n\n')], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    // MODIFIED: Include gameId in the filename for correlation with backend logs
    const fileName = `frontend-game-${gameId}-${new Date().toISOString().replace(/:/g, '-')}.log`;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
};