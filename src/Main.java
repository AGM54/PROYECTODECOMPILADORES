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
            MyVisitor visitor = new MyVisitor();
            visitor.visit(tree);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Implementación de MyVisitor dentro de la clase Main
    static class MyVisitor extends CompiScriptBaseVisitor<Object> {
        // Tabla de símbolos para almacenar funciones
        private Map<String, CompiScriptParser.FunctionContext> functions = new HashMap<>();
        // Tabla de símbolos para almacenar variables globales
        private Map<String, Object> globalVariables = new HashMap<>();
        // Entorno local para las variables dentro de una función
        private Map<String, Object> localVariables = null;

        // Visita la declaración de una función
        @Override
        public Object visitFunction(CompiScriptParser.FunctionContext ctx) {
            // Obtén el nombre de la función del primer identificador en la lista
            String functionName = ctx.IDENTIFIER().getText();
            // Guarda la función en la tabla de funciones
            functions.put(functionName, ctx);
            return null;
        }

        // Visita la llamada a una función
        @Override
        public Object visitCall(CompiScriptParser.CallContext ctx) {
            String functionName = ctx.IDENTIFIER().getText();
            CompiScriptParser.FunctionContext function = functions.get(functionName);

            if (function != null) {
                // Evaluar los argumentos
                Object[] args = new Object[ctx.arguments().expression().size()];
                for (int i = 0; i < ctx.arguments().expression().size(); i++) {
                    args[i] = visit(ctx.arguments().expression(i));
                }

                // Llamar a la función con los argumentos
                return callFunction(function, args);
            }

            System.err.println("Error: Función no definida " + functionName);
            return null;
        }

        // Maneja la ejecución de una función
        private Object callFunction(CompiScriptParser.FunctionContext functionCtx, Object[] args) {
            // Crear un nuevo entorno local para las variables de la función
            localVariables = new HashMap<>();  // Reiniciar el entorno local

            // Asignar argumentos a las variables correspondientes en el ámbito local
            for (int i = 0; i < functionCtx.parameters().IDENTIFIER().size(); i++) {
                String paramName = functionCtx.parameters().IDENTIFIER(i).getText();
                localVariables.put(paramName, args[i]);
            }

            // Ejecutar el bloque de la función y capturar posibles retornos
            Object returnValue = null;
            for (CompiScriptParser.DeclarationContext declCtx : functionCtx.block().declaration()) {
                if (declCtx.statement() != null && declCtx.statement().returnStmt() != null) {
                    // Si es una sentencia de retorno, evaluarla y devolver su valor
                    returnValue = visit(declCtx.statement().returnStmt().expression());
                    localVariables = null;  // Limpiar el entorno local después de la ejecución de la función
                    return returnValue;  // Salir inmediatamente al encontrar un retorno
                } else {
                    visit(declCtx);  // Visitar otras declaraciones
                }
            }

            // Limpiar el entorno local después de la ejecución de la función
            localVariables = null;
            return returnValue;
        }

        // Visita la sentencia print
        @Override
        public Object visitPrintStmt(CompiScriptParser.PrintStmtContext ctx) {
            // Evalúa la expresión y la imprime
            Object value = visit(ctx.expression());
            System.out.println(value);
            return null;
        }

        // Visita una expresión de suma

        @Override
        public Object visitTerm(CompiScriptParser.TermContext ctx) {
            if (ctx.getChildCount() == 3) { // Ej. a + b, a - b
                Object left = visit(ctx.factor(0));
                Object right = visit(ctx.factor(1));

                // Asegurar que ambos lados sean números
                if (left instanceof Double && right instanceof Double) {
                    String operator = ctx.getChild(1).getText();
                    switch (operator) {
                        case "+":
                            return (Double) left + (Double) right;
                        case "-":
                            return (Double) left - (Double) right;
                    }
                }
            }
            return visit(ctx.factor(0)); // Retorna el valor de la única expresión si no hay operador binario
        }
        @Override
        public Object visitFactor(CompiScriptParser.FactorContext ctx) {
            if (ctx.getChildCount() == 3) { // Ej. a * b, a / b
                Object left = visit(ctx.unary(0));
                Object right = visit(ctx.unary(1));

                // Asegurar que ambos lados sean números
                if (left instanceof Double && right instanceof Double) {
                    String operator = ctx.getChild(1).getText();
                    switch (operator) {
                        case "*":
                            return (Double) left * (Double) right;
                        case "/":
                            return (Double) left / (Double) right;
                    }
                }
            }
            return visit(ctx.unary(0)); // Retorna el valor de la única expresión si no hay operador binario
        }
        // Maneja valores primarios (números, etc.)
        @Override
        public Object visitPrimary(CompiScriptParser.PrimaryContext ctx) {
            if (ctx.NUMBER() != null) {
                return Double.valueOf(ctx.NUMBER().getText());
            } else if (ctx.IDENTIFIER() != null) {
                // Si es un identificador, busca primero en las variables locales, luego en las globales
                String varName = ctx.IDENTIFIER().getText();
                if (localVariables != null && localVariables.containsKey(varName)) {
                    return localVariables.get(varName);
                } else if (globalVariables.containsKey(varName)) {
                    return globalVariables.get(varName);
                } else {
                    System.err.println("Error: Variable no definida " + varName);
                }
            }
            return null; // Manejar otros tipos si es necesario
        }

        // Visita una asignación
        @Override
        public Object visitAssignment(CompiScriptParser.AssignmentContext ctx) {
            // Verificar si la asignación sigue el patrón IDENTIFIER = expression
            if (ctx.IDENTIFIER() != null) {
                String varName = ctx.IDENTIFIER().getText();
                Object value = visit(ctx.expression());  // Procesar la expresión asignada

                // Asignar en el entorno local si existe, de lo contrario en el global
                if (localVariables != null) {
                    localVariables.put(varName, value);
                } else {
                    globalVariables.put(varName, value);
                }
                return value;
            } else {
                // Si no es una asignación, manejar la expresión como tal
                return visit(ctx.logic_or());
            }
        }
    }
}
