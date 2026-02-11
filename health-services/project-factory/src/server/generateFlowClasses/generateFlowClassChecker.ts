import * as ts from "typescript";
import * as path from "path";
import * as fs from "fs";

// Path to directory containing class files
const classesDir = path.join(__dirname); // since the file is in the same folder

// Function to check TypeScript files for errors using the tsconfig.json
export function checkGenerateFlowClasses() {
    // Get all .ts files (ignore .d.ts)
    const files = fs.readdirSync(classesDir)
        .filter(f => f.endsWith('.ts') && !f.endsWith('.d.ts'))
        .map(f => path.join(classesDir, f));

    files.forEach(f => console.log("Checking file:", f));

    // Load the existing tsconfig.json
    const configPath = path.join(__dirname, '../../', 'tsconfig.json');
    const configFile = ts.readConfigFile(configPath, ts.sys.readFile);
    if (configFile.error) {
        console.error("❌ Error reading tsconfig.json:", configFile.error);
        process.exit(1);
    }

    // Parse the tsconfig.json
    const parsedConfig = ts.parseJsonConfigFileContent(configFile.config, ts.sys, path.dirname(configPath));

    // Create TypeScript program using the existing config options
    const program = ts.createProgram(files, {
        ...parsedConfig.options,
        noEmit: true
    });

    // Collect diagnostics
    const diagnostics = ts.getPreEmitDiagnostics(program);

    if (diagnostics.length > 0) {
        const formatted = ts.formatDiagnosticsWithColorAndContext(diagnostics, {
            getCanonicalFileName: f => f,
            getCurrentDirectory: () => process.cwd(),
            getNewLine: () => '\n'
        });
        console.error("❌ Type errors in generateFlowClasses:\n" + formatted);
        process.exit(1);
    } else {
        console.log("✅ All generateFlowClasses compiled cleanly.");
    }
}