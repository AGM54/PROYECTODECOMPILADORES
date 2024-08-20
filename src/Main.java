import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        // Ruta del archivo fuente de CompiScript que deseas analizar
        String inputFile = "src/test.txt";

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

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
