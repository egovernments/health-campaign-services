import * as ts from "typescript";
import * as path from "path";
import * as fs from "fs";

const classesDir = path.join(__dirname);

/** Type-checks every generate-flow class against the project tsconfig; exits non-zero on any type error (build gate). */
export function checkGenerateFlowClasses() {
    const files = fs.readdirSync(classesDir)
        .filter(f => f.endsWith('.ts') && !f.endsWith('.d.ts'))
        .map(f => path.join(classesDir, f));

    files.forEach(f => console.log("Checking file:", f));

    const configPath = path.join(__dirname, '../../', 'tsconfig.json');
    const configFile = ts.readConfigFile(configPath, ts.sys.readFile);
    if (configFile.error) {
        console.error("❌ Error reading tsconfig.json:", configFile.error);
        process.exit(1);
    }

    const parsedConfig = ts.parseJsonConfigFileContent(configFile.config, ts.sys, path.dirname(configPath));

    const program = ts.createProgram(files, {
        ...parsedConfig.options,
        noEmit: true
    });

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