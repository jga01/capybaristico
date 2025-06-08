let logEntries = [];
let isInitialized = false;

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
    logEntries.push(`[${getTimestamp()}] FRONTEND LOGGER INITIALIZED FOR NEW GAME.`);
    isInitialized = true;
    console.log("Frontend logger initialized.");
};

/**
 * The main logging function.
 * @param {string} message A description of what is being logged.
 * @param {object} [data=null] Optional data object to be stringified and included.
 */
export const log = (message, data = null) => {
    if (!isInitialized) initLogger();
    
    let entry = `[${getTimestamp()}] ${message}`;
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
    
    // For convenience, also log to the browser console
    console.log(`[GAME LOG] ${message}`, data);
};

/**
 * Creates a downloadable text file from the captured logs.
 */
export const downloadLogs = () => {
    if (logEntries.length === 0) {
        alert("No logs have been captured yet.");
        return;
    }
    const blob = new Blob([logEntries.join('\n\n========================================\n\n')], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    const fileName = `frontend-game-flux-${new Date().toISOString().replace(/:/g, '-')}.log`;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
    log(`Logs downloaded as ${fileName}`);
};