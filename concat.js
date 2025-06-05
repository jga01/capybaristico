// --- SCRIPT CONFIGURATION ---
const BACKEND_DIR_NAME = 'backend'; // Name of your backend folder
const FRONTEND_DIR_NAME = 'frontend'; // Name of your frontend folder
const OUTPUT_FILE = 'concatenated_project_context.txt'; // Set to null to print to console

// Define relevant file extensions for backend (Java Spring)
const RELEVANT_BACKEND_EXTENSIONS = [
    '.java',       // Java source files
    '.xml',        // pom.xml, Spring XML configs
    '.properties', // Application properties
    '.yml', '.yaml',// Application YAML configs
    '.sql',        // SQL schema or data
    '.gradle',     // Gradle build scripts
    '.md',         // READMEs or important markdown docs
];

// Define relevant file extensions for frontend (React + VITE)
const RELEVANT_FRONTEND_EXTENSIONS = [
    '.js', '.jsx', '.ts', '.tsx', // JavaScript/TypeScript files
    '.html',       // Entry HTML (e.g., index.html)
    '.css', '.scss', '.less',    // Stylesheets
    '.json',       // package.json, tsconfig.json, other configs
    '.md',         // READMEs or important markdown docs
    '.mjs',        // ES Modules
    '.cjs',        // CommonJS modules
];

// Define directories to ignore completely
const IGNORE_DIRECTORIES = [
    'node_modules', 'dist', 'build', 'target', '.git', '.idea',
    '.vscode', 'out', 'coverage', 'logs', 'public',
];

// Define specific files to always ignore, regardless of extension
const IGNORE_FILES = [
    'package-lock.json', 'yarn.lock', '.DS_Store',
];

// --- MODULE-SCOPED VARIABLES (will be initialized after import) ---
let fs;
let path;
let PROJECT_ROOT;
let backendPath;
let frontendPath;
let allContent = '';

// --- FUNCTION DEFINITIONS ---

function getProjectRoot() {
    // This function will be called AFTER fs and path are loaded
    let currentDir = process.cwd();
    if (fs.existsSync(path.join(currentDir, BACKEND_DIR_NAME)) && fs.existsSync(path.join(currentDir, FRONTEND_DIR_NAME))) {
        return currentDir;
    }
    const parentDir = path.dirname(currentDir);
    if (fs.existsSync(path.join(parentDir, BACKEND_DIR_NAME)) && fs.existsSync(path.join(parentDir, FRONTEND_DIR_NAME))) {
        return parentDir;
    }
    console.error(`Error: Could not find '${BACKEND_DIR_NAME}' and '${FRONTEND_DIR_NAME}' directories.`);
    console.error(`Please run this script from your project's root directory or one level down, or adjust directory names.`);
    process.exit(1);
}

function processFile(filePath, basePath) {
    const relativePath = path.relative(basePath, filePath);
    const fileExtension = path.extname(filePath).toLowerCase();
    const fileName = path.basename(filePath);

    if (IGNORE_FILES.includes(fileName) || IGNORE_FILES.includes(relativePath)) {
        console.log(`Skipping ignored file: ${relativePath}`);
        return;
    }

    try {
        const contentSample = fs.readFileSync(filePath, { encoding: 'utf8', flag: 'r', highWaterMark: 512 });
        if (contentSample.includes('\0')) {
            console.log(`Skipping likely binary file: ${relativePath}`);
            return;
        }

        const fileContent = fs.readFileSync(filePath, 'utf-8');
        allContent += `// --- START FILE: ${relativePath} ---\n`;
        allContent += fileContent;
        allContent += `\n// --- END FILE: ${relativePath} ---\n\n`;
        console.log(`Added: ${relativePath}`);
    } catch (error) {
        if (error.code === 'ENOENT') {
            console.warn(`Warning: File not found (possibly a broken symlink): ${filePath}`);
        } else if (error.message.includes('EISDIR')) {
            console.warn(`Warning: Tried to read a directory as a file: ${filePath}`);
        } else {
            console.warn(`Warning: Could not read file ${filePath}. Error: ${error.message}`);
        }
    }
}

function traverseDirectory(directoryPath, relevantExtensions, basePath) {
    if (!fs.existsSync(directoryPath)) {
        console.warn(`Warning: Directory not found, skipping: ${directoryPath}`);
        return;
    }
    const files = fs.readdirSync(directoryPath);

    for (const file of files) {
        const filePath = path.join(directoryPath, file);
        let stat;
        try {
            stat = fs.statSync(filePath);
        } catch (e) {
            console.warn(`Warning: Could not stat file (e.g. broken symlink), skipping: ${filePath}`);
            continue;
        }


        if (stat.isDirectory()) {
            if (!IGNORE_DIRECTORIES.includes(file.toLowerCase()) && !IGNORE_DIRECTORIES.includes(file)) {
                traverseDirectory(filePath, relevantExtensions, basePath);
            } else {
                console.log(`Skipping ignored directory: ${path.relative(basePath, filePath)}`);
            }
        } else if (stat.isFile()) {
            const fileExtension = path.extname(file).toLowerCase();
            if (relevantExtensions.includes(fileExtension)) {
                processFile(filePath, basePath);
            }
        }
    }
}

function main() {
    console.log(`Starting concatenation process from project root: ${PROJECT_ROOT}...\n`);

    // Process Backend
    if (fs.existsSync(backendPath)) {
        console.log(`\n--- Processing Backend: ${backendPath} ---`);
        allContent += `// ====== START BACKEND FILES (Java Spring) ======\n\n`;
        traverseDirectory(backendPath, RELEVANT_BACKEND_EXTENSIONS, PROJECT_ROOT);
        allContent += `// ====== END BACKEND FILES ======\n\n`;
    } else {
        console.warn(`Backend directory '${BACKEND_DIR_NAME}' not found at ${backendPath}. Skipping.`);
    }

    // Process Frontend
    const frontendRootFiles = ['index.html', 'vite.config.js', 'vite.config.ts', 'package.json', 'tsconfig.json'];

    if (fs.existsSync(frontendPath)) {
        console.log(`\n--- Processing Frontend: ${frontendPath} ---`);
        allContent += `// ====== START FRONTEND FILES (React + VITE) ======\n\n`;

        for (const rootFile of frontendRootFiles) {
            const filePath = path.join(frontendPath, rootFile);
            if (fs.existsSync(filePath)) {
                const fileExtension = path.extname(filePath).toLowerCase();
                if (RELEVANT_FRONTEND_EXTENSIONS.includes(fileExtension) && !IGNORE_FILES.includes(path.basename(filePath))) {
                     processFile(filePath, PROJECT_ROOT);
                }
            }
        }
        const frontendSrcPath = path.join(frontendPath, 'src');
        if (fs.existsSync(frontendSrcPath)) {
             traverseDirectory(frontendSrcPath, RELEVANT_FRONTEND_EXTENSIONS, PROJECT_ROOT);
        } else {
            console.warn(`Frontend 'src' directory not found in ${frontendPath}. Traversing entire frontend folder if 'src' is missing.`);
            // Fallback: traverse the entire frontendPath if 'src' doesn't exist (excluding already processed root files)
            // This part needs care to avoid double-processing root files if not handled by IGNORE_FILES or specific conditions.
            // For simplicity, if src/ is not found, this will traverse from frontendPath root.
            // Ensure IGNORE_DIRECTORIES and IGNORE_FILES are well-configured.
            traverseDirectory(frontendPath, RELEVANT_FRONTEND_EXTENSIONS, PROJECT_ROOT);
        }

        allContent += `// ====== END FRONTEND FILES ======\n\n`;
    } else {
        console.warn(`Frontend directory '${FRONTEND_DIR_NAME}' not found at ${frontendPath}. Skipping.`);
    }


    if (OUTPUT_FILE) {
        try {
            fs.writeFileSync(path.join(PROJECT_ROOT, OUTPUT_FILE), allContent, 'utf-8');
            console.log(`\nSuccessfully concatenated all relevant files into: ${path.join(PROJECT_ROOT, OUTPUT_FILE)}`);
        } catch (error) {
            console.error(`\nError writing to output file ${OUTPUT_FILE}: ${error.message}`);
            console.log("\n--- CONCATENATED CONTENT (stdout due to file write error) ---");
            console.log(allContent);
        }
    } else {
        console.log("\n--- CONCATENATED CONTENT (stdout) ---");
        console.log(allContent);
    }
}

// --- SCRIPT EXECUTION ---
(async () => {
    try {
        const fsModule = await import('fs');
        const pathModule = await import('path');
        
        // For built-in Node.js modules, .default usually contains the CJS exports.
        // If you face issues, you might try fsModule directly (fs = fsModule;)
        fs = fsModule.default; 
        path = pathModule.default;

        // Initialize path-dependent constants now that modules are loaded
        PROJECT_ROOT = getProjectRoot();
        backendPath = path.join(PROJECT_ROOT, BACKEND_DIR_NAME);
        frontendPath = path.join(PROJECT_ROOT, FRONTEND_DIR_NAME);

        // Run the main logic
        main();

    } catch (error) {
        console.error("Failed to load modules or run script:", error);
        // Fallback if dynamic import itself fails or fs/path are not as expected
        if (typeof require !== 'undefined') {
            console.log("Attempting fallback with require()...");
            try {
                fs = require('fs');
                path = require('path');

                PROJECT_ROOT = getProjectRoot();
                backendPath = path.join(PROJECT_ROOT, BACKEND_DIR_NAME);
                frontendPath = path.join(PROJECT_ROOT, FRONTEND_DIR_NAME);
                main();
            } catch (requireError) {
                console.error("Fallback with require() also failed:", requireError);
                console.error("Please ensure you are running this script with a Node.js version that supports dynamic import() or traditional require().");
            }
        }
    }
})();