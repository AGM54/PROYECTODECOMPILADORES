import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        // Ruta del archivo fuente de CompiScript que deseas analizar
        String inputFile = "src/test2.txt";

        try {
            // Leer el archivo fuente
            CharStream input = CharStreams.fromFileName(inputFile);

            // Crear un lexer basado en la gramática
            CompiScriptLexer lexer = new CompiScriptLexer(input);

            // Crear un buffer de tokens a partir del lexer
            CommonTokenStream tokens = new CommonTokenStream(lexer);

            // Crear un parser basado en los tokens
            CompiScriptParser parser = new CompiScriptParser(tokens);

            // Parsear el archivo comenzando por la regla 'program'
            ParseTree tree = parser.program();

            // Imprimir el árbol sintáctico en formato lisp-style (opcional)
            System.out.println(tree.toStringTree(parser));

            // Crear un visitor personalizado para manejar la ejecución del código
            CompiScriptCustomVisitor visitor = new CompiScriptCustomVisitor();
            visitor.visit(tree);
            visitor.printSymbols();
            MippsTreeVisitor icVisitor = new MippsTreeVisitor(
                    visitor.getFusedSymbolTable(),
                    visitor.getFusedFunctionsTable(),
                    visitor.getFusedClassTable(),
                    visitor.getFusedParametersTable());
            icVisitor.visit(tree);

            icVisitor.writeToFile("tac_instructions_V32.s");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
